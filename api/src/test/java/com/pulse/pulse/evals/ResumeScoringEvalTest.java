package com.pulse.pulse.evals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.platform.ai.OpenAiService;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("evals")
class ResumeScoringEvalTest {

    private static OpenAiService openAiService;
    private static ObjectMapper objectMapper;

    private static final Map<String, Boolean> results = new ConcurrentHashMap<>();
    private static final List<String> failures = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void setUp() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required for eval tests");
        }

        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TestObservationRegistry observationRegistry = TestObservationRegistry.create();
        PulseObservability observability = new PulseObservability(observationRegistry, meterRegistry);

        openAiService = new OpenAiService(builder, apiKey, observability);
        objectMapper = new ObjectMapper();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resumeFixtures")
    void scoredResumeIsGroundedAndWithinExpectedRange(String fixtureId, ResumeFixture fixture) throws Exception {
        System.out.printf("[EVAL] Starting %s...%n", fixtureId);
        long start = System.currentTimeMillis();
        String raw = openAiService.scoreResume(fixture.resumeText()).block();
        Map<String, Object> breakdown = objectMapper.readValue(raw, new TypeReference<>() {});

        int totalScore = ((Number) breakdown.get("total_score")).intValue();
        String decision = (String) breakdown.get("decision");

        assertThat(totalScore)
                .as("Score for %s (seniority: %s) should be in [%d, %d], got %d",
                        fixtureId, fixture.metadata().seniority(),
                        fixture.metadata().expectedScoreRange().min(),
                        fixture.metadata().expectedScoreRange().max(),
                        totalScore)
                .isBetween(fixture.metadata().expectedScoreRange().min(),
                        fixture.metadata().expectedScoreRange().max());

        assertThat(decision)
                .as("Decision for %s should be %s, got %s",
                        fixtureId, fixture.metadata().expectedDecision(), decision)
                .isEqualTo(fixture.metadata().expectedDecision());

        List<RationaleEntry> rationales = extractRationales(breakdown);
        List<String> groundednessFailures = runGroundednessJudge(fixture.resumeText(), rationales);

        if (groundednessFailures.isEmpty()) {
            results.put(fixtureId, true);
        } else {
            results.put(fixtureId, false);
            failures.addAll(groundednessFailures.stream()
                    .map(f -> fixtureId + ": " + f)
                    .toList());
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("[EVAL] %s: score=%d, decision=%s, grounded=%s (%dms)%n",
                fixtureId, totalScore, decision,
                groundednessFailures.isEmpty() ? "YES" : "NO(" + groundednessFailures.size() + ")",
                elapsed);

        assertThat(groundednessFailures)
                .as("Ungrounded rationales for %s", fixtureId)
                .isEmpty();
    }

    @AfterAll
    static void printSummary() {
        long passed = results.values().stream().filter(v -> v).count();
        long total = results.size();
        double passRate = total > 0 ? (double) passed / total * 100 : 0;

        System.out.println("\n=== EVAL SUMMARY ===");
        System.out.printf("%d/%d resumes PASSED (%.1f%% grounded-answer pass rate)%n",
                passed, total, passRate);

        if (!failures.isEmpty()) {
            System.out.println("\nFAILURES:");
            failures.forEach(f -> System.out.println("  - " + f));
        }
        System.out.println("====================\n");
    }

    @SuppressWarnings("unchecked")
    private List<RationaleEntry> extractRationales(Map<String, Object> breakdown) {
        List<RationaleEntry> entries = new ArrayList<>();
        Map<String, Object> sections = (Map<String, Object>) breakdown.get("sections");
        if (sections == null) return entries;

        for (Map.Entry<String, Object> section : sections.entrySet()) {
            Object value = section.getValue();
            if (value instanceof Map) {
                Map<String, Object> sectionMap = (Map<String, Object>) value;
                if (sectionMap.containsKey("rationale")) {
                    String rationale = (String) sectionMap.get("rationale");
                    if (rationale != null && !rationale.isBlank()) {
                        entries.add(new RationaleEntry("sections." + section.getKey(), rationale));
                    }
                } else {
                    for (Map.Entry<String, Object> sub : sectionMap.entrySet()) {
                        if (sub.getValue() instanceof Map) {
                            Map<String, Object> subMap = (Map<String, Object>) sub.getValue();
                            String rationale = (String) subMap.get("rationale");
                            if (rationale != null && !rationale.isBlank()) {
                                entries.add(new RationaleEntry(
                                        "sections." + section.getKey() + "." + sub.getKey(), rationale));
                            }
                        }
                    }
                }
            }
        }
        return entries;
    }

    private List<String> runGroundednessJudge(String resumeText, List<RationaleEntry> rationales) throws Exception {
        if (rationales.isEmpty()) return List.of();

        StringBuilder prompt = new StringBuilder();
        prompt.append("RESUME TEXT:\n").append(resumeText).append("\n\n");
        prompt.append("RATIONALES TO EVALUATE:\n[\n");
        for (int i = 0; i < rationales.size(); i++) {
            RationaleEntry entry = rationales.get(i);
            prompt.append("  {\"field\": \"").append(escapeJson(entry.field()))
                    .append("\", \"rationale\": \"").append(escapeJson(entry.rationale())).append("\"}");
            if (i < rationales.size() - 1) prompt.append(",");
            prompt.append("\n");
        }
        prompt.append("]\n\n");
        prompt.append("Return: [{\"field\": \"...\", \"verdict\": \"GROUNDED|HALLUCINATED\", \"reason\": \"one sentence\"}]");

        String response = openAiService.judgeGroundedness(prompt.toString()).block();
        if (response == null) return List.of("Judge returned null response");

        List<Map<String, String>> verdicts;
        try {
            verdicts = objectMapper.readValue(response, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of("Failed to parse judge response: " + response);
        }

        List<String> hallucinated = new ArrayList<>();
        for (Map<String, String> verdict : verdicts) {
            if ("HALLUCINATED".equalsIgnoreCase(verdict.get("verdict"))) {
                hallucinated.add(verdict.get("field") + " — " + verdict.get("reason"));
            }
        }
        return hallucinated;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static Stream<Arguments> resumeFixtures() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Arguments> fixtures = new ArrayList<>();

        ClassLoader cl = ResumeScoringEvalTest.class.getClassLoader();
        try (InputStream manifest = cl.getResourceAsStream("evals/resumes/manifest.json")) {
            if (manifest == null) {
                throw new IllegalStateException("evals/resumes/manifest.json not found on classpath. " +
                        "Run the resume generator script first.");
            }
            List<String> fileNames = mapper.readValue(manifest, new TypeReference<>() {});
            for (String fileName : fileNames) {
                try (InputStream is = cl.getResourceAsStream("evals/resumes/" + fileName)) {
                    if (is == null) continue;
                    JsonNode node = mapper.readTree(is);
                    JsonNode meta = node.get("metadata");
                    ResumeFixture fixture = new ResumeFixture(
                            node.get("resumeText").asText(),
                            new FixtureMetadata(
                                    meta.get("id").asText(),
                                    meta.get("seniority").asText(),
                                    meta.get("expectedDecision").asText(),
                                    new ScoreRange(
                                            meta.get("expectedScoreRange").get("min").asInt(),
                                            meta.get("expectedScoreRange").get("max").asInt()
                                    ),
                                    mapper.convertValue(meta.get("tags"), new TypeReference<>() {})
                            )
                    );
                    fixtures.add(Arguments.of(fixture.metadata().id(), fixture));
                }
            }
        }

        if (fixtures.isEmpty()) {
            throw new IllegalStateException("No resume fixtures found. Run the resume generator script first.");
        }
        int maxFixtures = Integer.parseInt(System.getProperty("eval.maxFixtures", "0"));
        if (maxFixtures > 0 && fixtures.size() > maxFixtures) {
            fixtures = fixtures.subList(0, maxFixtures);
        }
        return fixtures.stream();
    }

    record ScoreRange(int min, int max) {}
    record FixtureMetadata(String id, String seniority, String expectedDecision,
                           ScoreRange expectedScoreRange, List<String> tags) {}
    record ResumeFixture(String resumeText, FixtureMetadata metadata) {}
    record RationaleEntry(String field, String rationale) {}
}
