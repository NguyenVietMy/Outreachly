package com.outreachly.outreachly.service;

import com.fasterxml.jackson.databind.JsonNode;
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

    public LeetCodeService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://leetcode.com")
                .build();
    }

    public Map<String, Object> fetchStats(String username) {
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

        try {
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

            Map<String, Object> stats = new HashMap<>();
            stats.put("username", user.path("username").asText());
            stats.put("ranking", user.path("profile").path("ranking").asInt(0));

            int easy = 0, medium = 0, hard = 0, total = 0;
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

            stats.put("easy", easy);
            stats.put("medium", medium);
            stats.put("hard", hard);
            stats.put("total", total);

            int dsaScore = computeDsaScore(easy, medium, hard);
            stats.put("dsaScore", dsaScore);

            return stats;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch LeetCode stats for {}", username, e);
            throw new RuntimeException("Failed to fetch LeetCode stats", e);
        }
    }

    public int computeDsaScore(int easy, int medium, int hard) {
        double raw = easy * 1.0 + medium * 3.0 + hard * 6.0;
        return (int) Math.min(Math.round(raw / 5.0), 100);
    }
}
