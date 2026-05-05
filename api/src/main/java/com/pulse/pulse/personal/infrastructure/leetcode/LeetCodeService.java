package com.pulse.pulse.personal.infrastructure.leetcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class LeetCodeService {

    private final WebClient webClient;
    private final PulseObservability observability;

    public LeetCodeService(WebClient.Builder webClientBuilder, PulseObservability observability) {
        this.webClient = webClientBuilder
                .baseUrl("https://leetcode.com")
                .build();
        this.observability = observability;
    }

    public Map<String, Object> fetchStats(String username) {
        Observation observation = observability.start("pulse.leetcode.fetch");
        observability.low(observation, "operation", "fetch");
        observability.high(observation, "username", username);

        String query = """
                query getUserProfile($username: String!) {
                  matchedUser(username: $username) {
                    username
                    submitStatsGlobal {
                      acSubmissionNum {
                        difficulty
                        count
                      }
                    }
                    profile {
                      ranking
                    }
                  }
                }
                """;

        Map<String, Object> body = Map.of(
                "query", query,
                "variables", Map.of("username", username));

        String result = "error";
        try {
            Map<String, Object> stats = observability.scoped(observation, () -> {
                JsonNode response = webClient.post()
                        .uri("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

                if (response == null || response.path("data").path("matchedUser").isNull()
                        || response.path("data").path("matchedUser").isMissingNode()) {
                    throw new RuntimeException("LeetCode user not found: " + username);
                }

                JsonNode user = response.path("data").path("matchedUser");
                JsonNode submissions = user.path("submitStatsGlobal").path("acSubmissionNum");

                Map<String, Object> fetchedStats = new HashMap<>();
                fetchedStats.put("username", user.path("username").asText());
                fetchedStats.put("ranking", user.path("profile").path("ranking").asInt(0));

                int easy = 0;
                int medium = 0;
                int hard = 0;
                int total = 0;
                for (JsonNode sub : submissions) {
                    String diff = sub.path("difficulty").asText();
                    int count = sub.path("count").asInt(0);
                    switch (diff) {
                        case "Easy" -> easy = count;
                        case "Medium" -> medium = count;
                        case "Hard" -> hard = count;
                        case "All" -> total = count;
                    }
                }

                fetchedStats.put("easy", easy);
                fetchedStats.put("medium", medium);
                fetchedStats.put("hard", hard);
                fetchedStats.put("total", total);

                int dsaScore = computeDsaScore(easy, medium, hard);
                fetchedStats.put("dsaScore", dsaScore);
                return fetchedStats;
            });
            result = "success";
            return stats;
        } catch (RuntimeException e) {
            observation.error(e);
            throw e;
        } catch (Exception e) {
            observation.error(e);
            log.error("Failed to fetch LeetCode stats for {}", username, e);
            throw new RuntimeException("Failed to fetch LeetCode stats", e);
        } finally {
            observability.low(observation, "result", result);
            observation.stop();
        }
    }

    public int computeDsaScore(int easy, int medium, int hard) {
        double raw = easy * 1.0 + medium * 3.0 + hard * 6.0;
        return (int) Math.min(Math.round(raw / 5.0), 100);
    }
}
