package com.pulse.pulse.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class OpenAiService {

    private final WebClient webClient;
    private final PulseObservability observability;

    public OpenAiService(WebClient.Builder webClientBuilder,
                         @Value("${OPENAI_API_KEY}") String apiKey,
                         PulseObservability observability) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.observability = observability;
    }

    public Mono<String> generateDigest(String activityContext) {
        String model = "gpt-3.5-turbo";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content",
                        "You are a personal productivity assistant for a CS student. " +
                                "Write a 2-3 sentence plain-text daily digest summarizing the developer's recent activity. " +
                                "Be concise, direct, and highlight the most significant work. " +
                                "Start with the most impactful thing they did. " +
                                "Do not use bullet points or markdown."),
                Map.of("role", "user", "content", activityContext)
        });
        requestBody.put("max_tokens", 200);
        requestBody.put("temperature", 0.6);

        return executeChatCompletion("digest", model, activityContext.length(), requestBody, "OpenAI digest error");
    }

    public Mono<String> generatePersonalInsights(String contextPrompt) {
        String model = "gpt-4o-mini";
        String systemPrompt = "You are a personal CS career and study coach. " +
                "Based on the student's profile, knowledge levels, goals, and recent activity, " +
                "provide 3-5 actionable insights. Identify knowledge gaps, suggest focus areas, " +
                "flag stale goals, and acknowledge progress. Be specific and reference their actual data. " +
                "Format as a numbered list. Keep each item to 1-2 sentences.";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", contextPrompt)
        });
        requestBody.put("max_tokens", 500);
        requestBody.put("temperature", 0.5);

        return executeChatCompletion("insights", model, contextPrompt.length(), requestBody,
                "OpenAI personal insights error");
    }

    public Mono<String> scoreResume(String resumeText) {
        String model = "gpt-5.2";
        String systemPrompt = RESUME_SCORING_SYSTEM_PROMPT + "\n\n" + RESUME_SCORING_RUBRIC;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", resumeText)
        });
        requestBody.put("max_completion_tokens", 4000);
        requestBody.put("reasoning_effort", "low");

        return executeChatCompletion("resume_score", model, resumeText.length(), requestBody,
                "OpenAI resume scoring error");
    }

    public Mono<String> generateDailySuggestions(String contextPrompt) {
        String model = "gpt-4o-mini";
        String systemPrompt = "You are a daily work planning assistant for a software developer. " +
                "Given their active projects, open work items, goals, and recent activity, " +
                "suggest the 5-8 most impactful tasks they should work on today.\n\n" +
                "Mix project-specific tasks with career growth tasks based on what's most urgent.\n" +
                "Prioritize:\n" +
                "1. Anything with an approaching deadline\n" +
                "2. Open PRs that need attention (reviews, merge conflicts, stale drafts)\n" +
                "3. Assigned issues that are in-progress or unstarted\n" +
                "4. Logical next steps based on recent commit messages and project context\n" +
                "5. Career/learning tasks to fill gaps in their readiness scores\n\n" +
                "Return ONLY a JSON array where each object has:\n" +
                "- \"title\": actionable task, max 80 chars\n" +
                "- \"description\": 1-2 sentences with specific context (mention the repo, issue, or PR)\n" +
                "- \"priority\": 0 (do today), 1 (do this week), 2 (nice to have)\n" +
                "- \"category\": \"project\" | \"learning\" | \"career\" | \"review\"\n" +
                "- \"repoContext\": repo full name if task is repo-specific, null otherwise\n" +
                "- \"rationale\": one sentence explaining why this is suggested today\n\n" +
                "Return ONLY the JSON array, no markdown fences.";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", contextPrompt)
        });
        requestBody.put("max_tokens", 1200);
        requestBody.put("temperature", 0.5);

        return executeChatCompletion("daily_suggestions", model, contextPrompt.length(), requestBody,
                "OpenAI daily suggestions error");
    }

    public Mono<String> generateSectionTasks(String sectionContext) {
        String model = "gpt-4o-mini";
        String systemPrompt = "You are a CS study coach. Given a student's assessment results for a specific section, " +
                "generate a checklist of concrete topics and skills to learn or master. " +
                "Focus on weak areas (tier 0-2). For strong areas (tier 3-4), suggest advanced practice only.\n\n" +
                "Return ONLY a JSON array of objects, each with:\n" +
                "- \"title\": short actionable task (e.g., \"Learn consistent hashing and practice a design using it\")\n" +
                "- \"description\": 1 sentence explaining why this matters or how to approach it\n" +
                "- \"priority\": 0 (highest) to 2 (lowest), based on how weak the student is in this area\n\n" +
                "Generate 3-8 tasks per section. More tasks for weaker sections, fewer for strong ones. " +
                "If the student scored tier 3-4 on everything, return 1-2 stretch tasks.\n" +
                "Return ONLY the JSON array, no markdown fences.";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", sectionContext)
        });
        requestBody.put("max_tokens", 800);
        requestBody.put("temperature", 0.4);

        return executeChatCompletion("section_tasks", model, sectionContext.length(), requestBody,
                "OpenAI section tasks error");
    }

    public Mono<String> updateMemory(String currentMemory, String fullContext) {
        String model = "gpt-4o-mini";
        String systemPrompt = "You are a memory manager for a CS student career platform called Pulse. " +
                "You maintain a markdown profile about the student that evolves as they progress.\n\n" +
                "You receive the current memory and the student's full data context (stats, resume, goals, activity, etc.) " +
                "along with the specific event that triggered this update.\n\n" +
                "Rules:\n" +
                "- Rewrite the profile using ALL provided data â€” scores, resume details, projects, goals, activity\n" +
                "- Use the student's real name if provided\n" +
                "- Extract concrete details from the resume: project names, technologies, job titles, companies\n" +
                "- Include specific numbers: LeetCode counts, axis scores, resume score\n" +
                "- NEVER use bracket placeholders like [Student Name] or [Project Name] â€” if data is unknown, omit it entirely\n" +
                "- Use markdown: # for title, ## for sections, bullet points for lists\n" +
                "- Write in third person about the student\n" +
                "- Keep it concise â€” under 500 words total\n\n" +
                "Return ONLY the updated markdown profile, no preamble or code fences.";

        String userPrompt = "=== CURRENT MEMORY ===\n" + currentMemory +
                "\n\n=== FULL CONTEXT ===\n" + fullContext;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        });
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.3);

        return executeChatCompletion("memory_update", model, userPrompt.length(), requestBody,
                "OpenAI memory update error");
    }

    public Mono<String> generateEventTasks(String eventContext) {
        String model = "gpt-4o-mini";
        String systemPrompt = "You are a CS career coach. Based on a student event (resume upload, LeetCode progress), " +
                "generate actionable study/improvement tasks.\n\n" +
                "Return ONLY a JSON array of objects, each with:\n" +
                "- \"title\": short actionable task\n" +
                "- \"description\": 1 sentence explaining why or how\n" +
                "- \"priority\": 0 (highest) to 2 (lowest)\n\n" +
                "Generate 2-5 tasks. Be specific to the student's data. " +
                "Return ONLY the JSON array, no markdown fences.";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", eventContext)
        });
        requestBody.put("max_tokens", 600);
        requestBody.put("temperature", 0.4);

        return executeChatCompletion("event_tasks", model, eventContext.length(), requestBody,
                "OpenAI event tasks error");
    }

    private Mono<String> executeChatCompletion(String operation,
                                               String model,
                                               int promptCharCount,
                                               Map<String, Object> requestBody,
                                               String errorLogMessage) {
        return Mono.defer(() -> {
            Observation observation = observability.start("pulse.openai.chat");
            Timer.Sample sample = observability.timerSample();
            AtomicReference<String> result = new AtomicReference<>("unknown");

            observability.low(observation, "operation", operation);
            observability.low(observation, "model", model);
            observability.high(observation, "prompt.char_count", promptCharCount);

            return Mono.using(
                    observation::openScope,
                    scope -> webClient.post()
                            .uri("/chat/completions")
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(this::extractMessageContent)
                            .doOnNext(content -> {
                                result.set("success");
                                observability.high(observation, "response.char_count", content.length());
                            })
                            .doOnError(error -> {
                                result.set("error");
                                observation.error(error);
                                log.error("{}: ", errorLogMessage, error);
                            })
                            .doFinally(signal -> {
                                observability.low(observation, "result", result.get());
                                observability.counter("pulse.openai.calls",
                                                "operation", operation,
                                                "model", model,
                                                "result", result.get())
                                        .increment();
                                observability.stopTimer(sample, "pulse.openai.call.duration",
                                        "operation", operation,
                                        "model", model,
                                        "result", result.get());
                                observation.stop();
                            }),
                    Observation.Scope::close
            );
        });
    }

    private String extractMessageContent(JsonNode response) {
        JsonNode choices = response.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null) {
                return message.get("content").asText();
            }
        }
        throw new RuntimeException("No response from OpenAI");
    }

    private static final String RESUME_SCORING_SYSTEM_PROMPT =
            "You are a resume evaluator for a general CS career stats platform. " +
                    "You will receive a candidate's resume. There is NO specific job description. " +
                    "Score the resume using the SWE Backend/Fullstack Scoring Rubric provided below, " +
                    "which evaluates general market readiness against the most commonly demanded backend/fullstack skills.\n\n" +
                    "Rules:\n" +
                    "1. Score each subsection independently. Write a 1-2 sentence rationale for every score explaining what evidence you found (or did not find).\n" +
                    "2. Quote or paraphrase the specific resume text that justifies each score. If no evidence exists, state \"No evidence found.\"\n" +
                    "3. Apply override rules (e.g., experience-based education floor, polyglot bonus) after initial scoring.\n" +
                    "4. Return ONLY the JSON output object. No preamble, no markdown fences.\n" +
                    "5. Be conservative: when evidence is ambiguous, score one tier lower and note the ambiguity in the rationale.\n" +
                    "6. Do not infer technologies from company reputation. Score only what the resume explicitly states.\n" +
                    "7. Use the Market Tiers provided in the rubric to classify languages, frameworks, data technologies, and infra tools. " +
                    "If a technology does not appear in any tier, treat it as Tier 3 (niche).";

    private static final String RESUME_SCORING_RUBRIC =
            "## Scoring Overview\n" +
                    "| Section | Max Points |\n" +
                    "| 1. Technical Skills Match | 25 |\n" +
                    "| 2. Professional Experience | 30 |\n" +
                    "| 3. Impact & Quantification | 15 |\n" +
                    "| 4. Education & Credentials | 10 |\n" +
                    "| 5. Project Complexity | 10 |\n" +
                    "| 6. Resume Quality & Parsability | 10 |\n" +
                    "| Total | 100 |\n\n" +
                    "Pass threshold: 60/100 to ADVANCE. 45-59 = HOLD. <45 = REJECT. 80+ = auto-priority.\n\n" +
                    "## Section 1 â€” Technical Skills Match (25 pts)\n\n" +
                    "### Language & Framework Market Tiers\n" +
                    "Tier 1 (High demand): Python, Java, TypeScript/JavaScript, Go, C#\n" +
                    "Tier 2 (Solid demand): Kotlin, Rust, Ruby, C++, Scala, Swift (server-side)\n" +
                    "Tier 3 (Niche/declining): PHP, Perl, R, Lua, Elixir, Clojure, Haskell, COBOL, Fortran\n\n" +
                    "Framework Tier 1: Spring/Spring Boot, Django/Flask/FastAPI, Express/NestJS/Next.js, ASP.NET, Rails, Gin/Fiber\n" +
                    "Framework Tier 2: Laravel, Phoenix, Actix, Ktor, Play, Micronaut\n\n" +
                    "Data Tier 1: PostgreSQL, MySQL, MongoDB, Redis, Kafka, DynamoDB, Elasticsearch\n" +
                    "Data Tier 2: Cassandra, Neo4j, CockroachDB, RabbitMQ, Pulsar, ClickHouse\n\n" +
                    "Infra Tier 1: AWS, GCP, Azure, Docker, Kubernetes, Terraform, GitHub Actions, Jenkins, Datadog\n" +
                    "Infra Tier 2: Pulumi, Nomad, ArgoCD, Prometheus/Grafana, CloudFormation\n\n" +
                    "### 1A. Primary Language Proficiency (10 pts)\n" +
                    "10: >=1 Tier 1 language in >=2 professional bullets AND skills section.\n" +
                    "7: >=1 Tier 1 in >=1 bullet OR Tier 2 in >=2 bullets with detail.\n" +
                    "4: Only listed in skills with no context, OR Tier 3 demonstrated in depth.\n" +
                    "2: Only Tier 3 languages demonstrated.\n" +
                    "0: No recognizable programming language evidence.\n" +
                    "Polyglot bonus: +2 (cap 10) if >=3 Tier 1/2 languages with bullet-level evidence.\n\n" +
                    "### 1B. Backend Frameworks & Runtime (5 pts)\n" +
                    "5: Tier 1 framework in production with concrete detail.\n" +
                    "3: Tier 1 named without detail OR Tier 2 with detail.\n" +
                    "1: Any framework in skills only.\n" +
                    "0: No backend framework evidence.\n\n" +
                    "### 1C. Data Layer (5 pts)\n" +
                    "5: Designing/optimizing data system with Tier 1 tech and specifics.\n" +
                    "3: >=2 data technologies with usage context.\n" +
                    "1: Data tech in skills only.\n" +
                    "0: No data layer evidence.\n\n" +
                    "### 1D. Infrastructure & DevOps (5 pts)\n" +
                    "5: Owning/building infra with Tier 1 tools and outcome detail.\n" +
                    "3: >=2 infra tools with light context.\n" +
                    "1: Infra tools in skills list only.\n" +
                    "0: No infrastructure evidence.\n\n" +
                    "## Section 2 â€” Professional Experience (30 pts)\n\n" +
                    "### 2A. Years of Relevant Experience (10 pts)\n" +
                    "10: >=5 years. 7: 3-4 years. 4: 1-2 years. 2: <1 year but internships/projects. 0: No dateable experience.\n\n" +
                    "### 2B. Role Progression & Scope (10 pts)\n" +
                    "10: Clear upward trajectory or consistently senior-scope responsibilities.\n" +
                    "7: Lateral moves with increasing scope.\n" +
                    "4: Flat trajectory, appropriate responsibilities.\n" +
                    "0: Significantly below target seniority or too vague.\n\n" +
                    "### 2C. Relevance of Most Recent Role (10 pts)\n" +
                    "10: Current role is backend/fullstack at software company with backend bullet points.\n" +
                    "7: Engineering-adjacent with backend overlap.\n" +
                    "4: In tech but not engineering.\n" +
                    "0: Non-technical or >2 year gap.\n\n" +
                    "## Section 3 â€” Impact & Quantification (15 pts)\n\n" +
                    "### 3A. Quantified Outcomes (10 pts)\n" +
                    "10: >=4 quantified outcome bullets. 7: 2-3. 4: 1. 0: Zero.\n\n" +
                    "### 3B. Scope of Impact (5 pts)\n" +
                    "5: Org/company-wide impact. 3: Team-level. 1: Task-level only. 0: No impact inferable.\n\n" +
                    "## Section 4 â€” Education & Credentials (10 pts)\n\n" +
                    "### 4A. Degree (6 pts)\n" +
                    "6: BS/BA+ in CS or related field. 4: Unrelated BS/BA + bootcamp/self-taught evidence. 2: Bootcamp only. 0: None mentioned.\n" +
                    "Override: if >=5 years experience, floor at 4.\n\n" +
                    "### 4B. Certifications (4 pts)\n" +
                    "4: >=1 industry-recognized cert. 2: MOOCs/non-proctored certs. 0: None.\n\n" +
                    "## Section 5 â€” Project Complexity (10 pts)\n" +
                    "Indicators: distributed systems, scale (>10K RPS, >1TB, 99.9%+ SLA, >100K users), architectural ownership, hard problems, cross-system integration.\n" +
                    "10: >=4 indicators in one project. 7: 2-3. 4: 1. 2: Projects but no indicators. 0: No projects described.\n\n" +
                    "## Section 6 â€” Resume Quality & Parsability (10 pts)\n\n" +
                    "### 6A. Structural Parsability (5 pts)\n" +
                    "5: Clear sections, standard dates, bullet points. 3: Minor issues. 1: Major issues. 0: Unparsable.\n\n" +
                    "### 6B. Conciseness & Signal Density (5 pts)\n" +
                    "5: <=2 pages, every bullet has tech detail or outcome, no filler. 3: <=3 pages, mostly substantive. 1: >3 pages or duties-based. 0: Wall of text.\n\n" +
                    "## Decision Logic\n" +
                    "ADVANCE (>=60), HOLD (45-59), REJECT (<45). Auto-priority >=80.\n\n" +
                    "## Flags\n" +
                    "OVERQUALIFIED: >=85. CAREER_SWITCH: recent non-eng + prior eng. GAP_>2Y: >2yr gap unexplained. " +
                    "NO_QUANTIFICATION: 3A=0. SKILLS_LIST_ONLY: 1A-1D all <=1. EXPERIENCE_OVERRIDE: 4A floor applied. " +
                    "NICHE_STACK_ONLY: all Tier 3. POLYGLOT: bonus applied.\n\n" +
                    "## Output Format\n" +
                    "Return ONLY this JSON (no markdown fences):\n" +
                    "{\"candidate_id\":\"<filename>\",\"scoring_date\":\"<ISO8601>\"," +
                    "\"sections\":{\"technical_skills\":{\"primary_language\":{\"score\":0,\"max\":10,\"rationale\":\"\"}," +
                    "\"backend_frameworks\":{\"score\":0,\"max\":5,\"rationale\":\"\"}," +
                    "\"data_layer\":{\"score\":0,\"max\":5,\"rationale\":\"\"}," +
                    "\"infra_devops\":{\"score\":0,\"max\":5,\"rationale\":\"\"}}," +
                    "\"experience\":{\"years\":{\"score\":0,\"max\":10,\"rationale\":\"\"}," +
                    "\"progression\":{\"score\":0,\"max\":10,\"rationale\":\"\"}," +
                    "\"recency\":{\"score\":0,\"max\":10,\"rationale\":\"\"}}," +
                    "\"impact\":{\"quantified_outcomes\":{\"score\":0,\"max\":10,\"rationale\":\"\"}," +
                    "\"scope\":{\"score\":0,\"max\":5,\"rationale\":\"\"}}," +
                    "\"education\":{\"degree\":{\"score\":0,\"max\":6,\"rationale\":\"\"}," +
                    "\"certifications\":{\"score\":0,\"max\":4,\"rationale\":\"\"}}," +
                    "\"project_complexity\":{\"score\":0,\"max\":10,\"rationale\":\"\"}," +
                    "\"resume_quality\":{\"parsability\":{\"score\":0,\"max\":5,\"rationale\":\"\"}," +
                    "\"conciseness\":{\"score\":0,\"max\":5,\"rationale\":\"\"}}}," +
                    "\"total_score\":0,\"max_score\":100," +
                    "\"decision\":\"ADVANCE|HOLD|REJECT\",\"flags\":[],\"summary\":\"\"}";
}
