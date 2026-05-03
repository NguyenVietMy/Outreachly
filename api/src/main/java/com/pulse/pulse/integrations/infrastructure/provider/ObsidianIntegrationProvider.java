package com.pulse.pulse.integrations.infrastructure.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.integrations.application.IntegrationEventPayload;
import com.pulse.pulse.integrations.domain.UserIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ObsidianIntegrationProvider {

    public static final String PROVIDER_NAME = "obsidian";
    public static final String SELECTED_RESOURCES_KEY = "selectedResources";

    private final WebClient gitHubWebClient;
    private final ObjectMapper objectMapper;

    public ObsidianIntegrationProvider(@Qualifier("gitHubWebClient") WebClient gitHubWebClient,
                                       ObjectMapper objectMapper) {
        this.gitHubWebClient = gitHubWebClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> applySelectedRepository(UserIntegration integration,
                                                       List<Map<String, String>> availableRepositories,
                                                       List<String> selectedIds) {
        Map<String, String> byId = new HashMap<>();
        for (Map<String, String> resource : availableRepositories) {
            byId.put(resource.get("id"), resource.getOrDefault("name", resource.get("id")));
        }

        List<Map<String, String>> selected = new ArrayList<>();
        if (!selectedIds.isEmpty()) {
            String id = selectedIds.get(0);
            String label = byId.get(id);
            if (label != null) {
                selected.add(Map.of("id", id, "name", label, "type", "repository"));
            }
        }

        Map<String, Object> metadata = integration.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(integration.getMetadata());
        metadata.put(SELECTED_RESOURCES_KEY, selected);
        return metadata;
    }

    public List<IntegrationEventPayload> fetchRecentEvents(String token,
                                                           UserIntegration integration,
                                                           LocalDateTime since) {
        String repoFullName = getSelectedRepository(integration.getMetadata());
        if (repoFullName == null || token == null || token.isBlank()) {
            return List.of();
        }

        String[] parts = repoFullName.split("/");
        if (parts.length != 2) {
            return List.of();
        }

        JsonNode commits = gitHubWebClient.get()
                .uri("/repos/{owner}/{repo}/commits?per_page=100&since={since}",
                        parts[0], parts[1], since.toInstant(ZoneOffset.UTC).toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (commits == null || !commits.isArray()) {
            return List.of();
        }

        List<IntegrationEventPayload> result = new ArrayList<>();
        for (JsonNode commit : commits) {
            String sha = commit.path("sha").asText();
            String message = commit.path("commit").path("message").asText();
            String dateStr = commit.path("commit").path("author").path("date").asText();
            LocalDateTime timestamp = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);

            if (timestamp.isBefore(since)) {
                continue;
            }

            String title = message.split("\n")[0];
            if (title.length() > 120) {
                title = title.substring(0, 117) + "...";
            }

            result.add(new IntegrationEventPayload(
                    "note_edit",
                    title,
                    sha,
                    timestamp,
                    Map.of("sha", sha, "repo", repoFullName)
            ));
        }

        return result;
    }

    public List<IntegrationEventPayload> mapWebhookEvents(JsonNode payload) {
        String repoFullName = payload.path("repository").path("full_name").asText("");
        if (repoFullName.isBlank() || !payload.path("commits").isArray() || payload.path("commits").isEmpty()) {
            return List.of();
        }

        List<IntegrationEventPayload> events = new ArrayList<>();
        for (JsonNode commit : payload.path("commits")) {
            String sha = commit.path("id").asText("");
            String message = commit.path("message").asText("");
            String timestampValue = commit.path("timestamp").asText("");
            if (sha.isBlank() || timestampValue.isBlank()) {
                continue;
            }

            String title = message.split("\n")[0];
            if (title.length() > 120) {
                title = title.substring(0, 117) + "...";
            }

            events.add(new IntegrationEventPayload(
                    "note_edit",
                    title,
                    sha,
                    LocalDateTime.parse(timestampValue, DateTimeFormatter.ISO_DATE_TIME),
                    Map.of("sha", sha, "repo", repoFullName)
            ));
        }

        return events;
    }

    public String getAccountLabel() {
        return "Vault repo";
    }

    public String getAccountValue(Map<String, Object> metadata) {
        String selected = getSelectedRepository(metadata);
        return selected != null ? selected : "Not selected";
    }

    public String getEventsLabel() {
        return "Vault activity";
    }

    public String buildScopeSummary(Map<String, Object> metadata) {
        String selected = getSelectedRepository(metadata);
        return selected != null ? selected : "Select a vault repo";
    }

    public String getSelectedRepository(Map<String, Object> metadata) {
        List<Map<String, String>> resources = getSelectedResourceMaps(metadata);
        if (resources.isEmpty()) {
            return null;
        }
        return resources.get(0).get("id");
    }

    public List<Map<String, String>> getSelectedResourceMaps(Map<String, Object> metadata) {
        Object raw = metadata != null ? metadata.get(SELECTED_RESOURCES_KEY) : null;
        if (raw == null) {
            return List.of();
        }
        return objectMapper.convertValue(raw, new TypeReference<>() {});
    }
}
