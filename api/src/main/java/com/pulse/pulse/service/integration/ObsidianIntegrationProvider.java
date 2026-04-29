package com.pulse.pulse.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.entity.IntegrationEvent;
import com.pulse.pulse.entity.UserIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service("obsidian")
@Slf4j
public class ObsidianIntegrationProvider implements IntegrationProvider {

    private final WebClient gitHubWebClient;

    public ObsidianIntegrationProvider(@Qualifier("gitHubWebClient") WebClient gitHubWebClient) {
        this.gitHubWebClient = gitHubWebClient;
    }

    @Override
    public String getProviderName() {
        return "obsidian";
    }

    @Override
    public boolean requiresOAuth() {
        return false;
    }

    @Override
    public String buildOAuthUrl(String state, String redirectUri) {
        throw new UnsupportedOperationException("Obsidian uses GitHub token");
    }

    @Override
    public String exchangeCode(String code, String redirectUri) {
        throw new UnsupportedOperationException("Obsidian uses GitHub token");
    }

    @Override
    public List<IntegrationEvent> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
        String token = integration.getAccessToken();
        Map<String, Object> meta = integration.getMetadata();
        String repoFullName = meta != null ? (String) meta.get("repoFullName") : null;

        if (repoFullName == null || token == null) {
            log.warn("Missing repo name or token for Obsidian sync");
            return Collections.emptyList();
        }

        String[] parts = repoFullName.split("/");
        if (parts.length != 2) {
            log.warn("Invalid repo format: {}", repoFullName);
            return Collections.emptyList();
        }

        JsonNode commits = gitHubWebClient.get()
                .uri("/repos/{owner}/{repo}/commits?per_page=100&since={since}",
                        parts[0], parts[1], since.toString() + "Z")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (commits == null || !commits.isArray()) {
            return Collections.emptyList();
        }

        List<IntegrationEvent> result = new ArrayList<>();
        for (JsonNode commit : commits) {
            String sha = commit.path("sha").asText();
            String message = commit.path("commit").path("message").asText();
            String dateStr = commit.path("commit").path("author").path("date").asText();
            LocalDateTime timestamp = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);

            if (timestamp.isBefore(since)) continue;

            String title = message.split("\n")[0];
            if (title.length() > 120) {
                title = title.substring(0, 117) + "...";
            }

            result.add(IntegrationEvent.builder()
                    .userId(integration.getUserId())
                    .provider("obsidian")
                    .eventType("note_edit")
                    .title(title)
                    .externalId(sha)
                    .eventTimestamp(timestamp)
                    .rawPayload(Map.of("sha", sha, "repo", repoFullName))
                    .build());
        }

        return result;
    }

    @Override
    public Map<String, Object> fetchMetadata(String accessToken) {
        return Collections.emptyMap();
    }
}
