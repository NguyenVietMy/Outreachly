package com.pulse.pulse.integrations.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.integrations.application.IntegrationEventPayload;
import com.pulse.pulse.integrations.domain.UserIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service("linear")
@Slf4j
public class LinearIntegrationProvider implements IntegrationProvider {

    private final WebClient linearWebClient;

    public LinearIntegrationProvider(@Qualifier("linearWebClient") WebClient linearWebClient) {
        this.linearWebClient = linearWebClient;
    }

    @Override
    public String getProviderName() {
        return "linear";
    }

    @Override
    public boolean requiresOAuth() {
        return false;
    }

    @Override
    public String buildOAuthUrl(String state, String redirectUri) {
        throw new UnsupportedOperationException("Linear uses API key");
    }

    @Override
    public String exchangeCode(String code, String redirectUri) {
        throw new UnsupportedOperationException("Linear uses API key");
    }

    @Override
    public List<IntegrationEventPayload> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
        String apiKey = integration.getAccessToken();
        Map<String, Object> meta = integration.getMetadata();
        String viewerId = meta != null ? (String) meta.get("viewerId") : null;

        if (viewerId == null) {
            log.warn("No Linear viewer ID in metadata");
            return Collections.emptyList();
        }

        String sinceStr = since.toString() + "Z";
        String query = """
                {
                    issues(filter: {
                        assignee: { id: { eq: "%s" } },
                        updatedAt: { gte: "%s" }
                    }, first: 100) {
                        nodes {
                            id
                            title
                            state { name type }
                            createdAt
                            updatedAt
                        }
                    }
                }
                """.formatted(viewerId, sinceStr);

        JsonNode response = linearWebClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .bodyValue(Map.of("query", query))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || response.has("errors")) {
            log.warn("Linear API error: {}", response);
            return Collections.emptyList();
        }

        List<IntegrationEventPayload> result = new ArrayList<>();
        JsonNode issues = response.path("data").path("issues").path("nodes");

        for (JsonNode issue : issues) {
            String issueId = issue.path("id").asText();
            String title = issue.path("title").asText();
            String stateType = issue.path("state").path("type").asText();
            String stateName = issue.path("state").path("name").asText();
            String updatedAt = issue.path("updatedAt").asText();

            LocalDateTime timestamp = LocalDateTime.parse(updatedAt, DateTimeFormatter.ISO_DATE_TIME);

            String eventType;
            String displayTitle;
            if ("completed".equals(stateType) || "Done".equals(stateName)) {
                eventType = "issue_closed";
                displayTitle = "Closed - " + title;
            } else if ("canceled".equals(stateType) || "Canceled".equals(stateName)) {
                eventType = "issue_canceled";
                displayTitle = "Canceled - " + title;
            } else {
                eventType = "issue_updated";
                displayTitle = stateName + " - " + title;
            }

            result.add(new IntegrationEventPayload(
                    eventType,
                    displayTitle,
                    issueId + "_" + stateType,
                    timestamp,
                    Map.of("state", stateName, "stateType", stateType)
            ));
        }

        return result;
    }

    @Override
    public String getAccountLabel() {
        return "Account";
    }

    @Override
    public String getAccountValue(Map<String, Object> metadata) {
        return metadata != null ? (String) metadata.getOrDefault("name", "unknown") : "unknown";
    }

    @Override
    public String getEventsLabel() {
        return "Issues tracked";
    }

    @Override
    public Map<String, Object> fetchMetadata(String accessToken) {
        String query = "{ viewer { id name email } }";

        JsonNode response = linearWebClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .bodyValue(Map.of("query", query))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || response.has("errors")) {
            throw new RuntimeException("Invalid Linear API key");
        }

        JsonNode viewer = response.path("data").path("viewer");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("viewerId", viewer.path("id").asText());
        metadata.put("name", viewer.path("name").asText());
        metadata.put("email", viewer.path("email").asText());
        return metadata;
    }
}
