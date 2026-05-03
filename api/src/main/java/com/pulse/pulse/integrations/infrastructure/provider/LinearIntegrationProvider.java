package com.pulse.pulse.integrations.infrastructure.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.integrations.application.IntegrationEventPayload;
import com.pulse.pulse.integrations.application.IntegrationResourceOption;
import com.pulse.pulse.integrations.domain.UserIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class LinearIntegrationProvider {

    public static final String PROVIDER_NAME = "linear";
    public static final String SELECTED_RESOURCES_KEY = "selectedResources";

    private final WebClient linearWebClient;
    private final ObjectMapper objectMapper;

    @Value("${linear.client-id:}")
    private String clientId;

    @Value("${linear.client-secret:}")
    private String clientSecret;

    @Value("${linear.redirect-uri:http://localhost:8080/api/integrations/linear/callback}")
    private String redirectUri;

    @Value("${linear.webhook-secret:}")
    private String webhookSecret;

    public LinearIntegrationProvider(@Qualifier("linearWebClient") WebClient linearWebClient,
                                     ObjectMapper objectMapper) {
        this.linearWebClient = linearWebClient;
        this.objectMapper = objectMapper;
    }

    public String buildOAuthUrl(String state) {
        return "https://linear.app/oauth/authorize" +
                "?response_type=code" +
                "&client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&scope=read,admin" +
                "&state=" + state;
    }

    public ConnectionResult exchangeCode(String code) {
        JsonNode response = linearWebClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(buildTokenForm(code)))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || response.path("access_token").asText("").isBlank()) {
            throw new IllegalStateException("Linear OAuth failed");
        }

        String accessToken = response.path("access_token").asText("");
        String refreshToken = response.path("refresh_token").asText("");
        Map<String, Object> metadata = fetchMetadata(accessToken);
        return new ConnectionResult(accessToken, refreshToken.isBlank() ? null : refreshToken, metadata);
    }

    public List<IntegrationResourceOption> listResources(UserIntegration integration) {
        JsonNode response = executeGraphQl(integration.getAccessToken(), """
                {
                  teams(first: 100) {
                    nodes {
                      id
                      name
                      key
                    }
                  }
                }
                """);

        JsonNode teams = response.path("data").path("teams").path("nodes");
        if (!teams.isArray()) {
            return List.of();
        }

        List<IntegrationResourceOption> resources = new ArrayList<>();
        for (JsonNode team : teams) {
            String id = team.path("id").asText("");
            String label = team.path("key").asText("").isBlank()
                    ? team.path("name").asText("")
                    : team.path("key").asText("") + " - " + team.path("name").asText("");
            if (!id.isBlank() && !label.isBlank()) {
                resources.add(new IntegrationResourceOption(id, label, "team"));
            }
        }
        resources.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        return resources;
    }

    public Map<String, Object> applySelectedResources(UserIntegration integration, List<String> selectedIds) {
        Map<String, String> labelsById = new LinkedHashMap<>();
        for (IntegrationResourceOption option : listResources(integration)) {
            labelsById.put(option.id(), option.label());
        }

        List<Map<String, String>> selected = new ArrayList<>();
        for (String id : selectedIds) {
            String label = labelsById.get(id);
            if (label != null) {
                selected.add(Map.of("id", id, "name", label, "type", "team"));
            }
        }

        Map<String, Object> metadata = integration.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(integration.getMetadata());
        metadata.put(SELECTED_RESOURCES_KEY, selected);
        return metadata;
    }

    public List<IntegrationEventPayload> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
        List<String> teamIds = getSelectedTeamIds(integration.getMetadata());
        if (teamIds.isEmpty()) {
            return List.of();
        }

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < teamIds.size(); i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append("\"").append(teamIds.get(i)).append("\"");
        }

        String query = """
                {
                  issues(
                    filter: {
                      team: { id: { in: [%s] } }
                      updatedAt: { gte: "%s" }
                    }
                    first: 100
                  ) {
                    nodes {
                      id
                      title
                      updatedAt
                      state { name type }
                      team { id name key }
                    }
                  }
                }
                """.formatted(ids, since.toInstant(ZoneOffset.UTC).toString());

        JsonNode response = executeGraphQl(integration.getAccessToken(), query);
        JsonNode issues = response.path("data").path("issues").path("nodes");
        if (!issues.isArray()) {
            return List.of();
        }

        List<IntegrationEventPayload> events = new ArrayList<>();
        for (JsonNode issue : issues) {
            LocalDateTime timestamp = LocalDateTime.parse(issue.path("updatedAt").asText(""), DateTimeFormatter.ISO_DATE_TIME);
            if (timestamp.isBefore(since)) {
                continue;
            }

            String teamLabel = teamLabel(issue.path("team"));
            String title = issue.path("title").asText("");
            String stateName = issue.path("state").path("name").asText("Updated");
            String stateType = issue.path("state").path("type").asText("");
            String eventType = mapIssueEventType(stateType, stateName);

            events.add(new IntegrationEventPayload(
                    eventType,
                    stateName + " - " + title,
                    "linear:issue:" + issue.path("id").asText("") + ":" + issue.path("updatedAt").asText(""),
                    timestamp,
                    Map.of("team", teamLabel, "teamId", issue.path("team").path("id").asText(""))
            ));
        }

        return events;
    }

    public JsonNode verifyAndParseWebhook(Map<String, String> headers, byte[] body) {
        String signature = header(headers, "linear-signature");
        if (signature == null || webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Linear webhook verification is not configured");
        }

        String computed = hmacHex(body, webhookSecret);
        if (!constantTimeEquals(signature, computed)) {
            throw new IllegalArgumentException("Invalid Linear webhook signature");
        }

        try {
            JsonNode payload = objectMapper.readTree(body);
            long webhookTimestamp = payload.path("webhookTimestamp").asLong(0L);
            if (webhookTimestamp > 0 && Math.abs(System.currentTimeMillis() - webhookTimestamp) > 60_000L) {
                throw new IllegalArgumentException("Linear webhook timestamp is too old");
            }
            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Linear webhook payload", e);
        }
    }

    public String extractDeliveryId(Map<String, String> headers) {
        String deliveryId = header(headers, "linear-delivery");
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new IllegalArgumentException("Missing Linear delivery id");
        }
        return deliveryId;
    }

    public String extractOrganizationId(JsonNode payload) {
        JsonNode organizationId = findFirst(payload, "organizationId");
        return organizationId != null ? organizationId.asText("") : "";
    }

    public List<String> extractTeamIds(JsonNode payload) {
        List<String> teamIds = new ArrayList<>();
        collectTeamIds(payload, teamIds);
        return teamIds.stream().distinct().toList();
    }

    public List<IntegrationEventPayload> mapWebhookEvents(JsonNode payload) {
        JsonNode data = payload.path("data");
        if (data.isMissingNode()) {
            return List.of();
        }

        String type = payload.path("type").asText("");
        String action = payload.path("action").asText("update");
        String externalId = "linear:" + type + ":" + data.path("id").asText("") + ":" +
                data.path("updatedAt").asText(data.path("createdAt").asText("")) + ":" + action;
        LocalDateTime timestamp = extractTimestamp(data);
        if (timestamp == null) {
            timestamp = LocalDateTime.now(ZoneOffset.UTC);
        }

        String title = switch (type) {
            case "Issue" -> buildIssueTitle(data, action);
            case "Project" -> capitalize(action) + " project - " + data.path("name").asText("");
            case "Cycle" -> capitalize(action) + " cycle - " + data.path("name").asText("");
            default -> "";
        };

        String eventType = switch (type) {
            case "Issue" -> mapIssueEventType(data.path("state").path("type").asText(""),
                    data.path("state").path("name").asText(""));
            case "Project" -> "project_" + action;
            case "Cycle" -> "cycle_" + action;
            default -> "";
        };

        if (title.isBlank() || eventType.isBlank()) {
            return List.of();
        }

        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("type", type);
        rawPayload.put("action", action);
        JsonNode teamIdNode = findFirst(data, "teamId");
        if (teamIdNode != null && !teamIdNode.asText("").isBlank()) {
            rawPayload.put("teamId", teamIdNode.asText(""));
        }

        return List.of(new IntegrationEventPayload(eventType, title, externalId, timestamp, rawPayload));
    }

    public String getAccountLabel() {
        return "Workspace";
    }

    public String getAccountValue(Map<String, Object> metadata) {
        return metadata != null ? String.valueOf(metadata.getOrDefault("organizationName", "unknown")) : "unknown";
    }

    public String getEventsLabel() {
        return "Team activity";
    }

    public String buildScopeSummary(Map<String, Object> metadata) {
        List<Map<String, String>> selected = getSelectedResourceMaps(metadata);
        if (selected.isEmpty()) {
            return "Select teams";
        }
        if (selected.size() == 1) {
            return selected.get(0).getOrDefault("name", selected.get(0).get("id"));
        }
        return selected.size() + " teams selected";
    }

    public List<String> getSelectedTeamIds(Map<String, Object> metadata) {
        return getSelectedResourceMaps(metadata).stream()
                .map(resource -> resource.get("id"))
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    public List<Map<String, String>> getSelectedResourceMaps(Map<String, Object> metadata) {
        Object raw = metadata != null ? metadata.get(SELECTED_RESOURCES_KEY) : null;
        if (raw == null) {
            return List.of();
        }
        return objectMapper.convertValue(raw, new TypeReference<>() {});
    }

    private Map<String, Object> fetchMetadata(String accessToken) {
        JsonNode response = executeGraphQl(accessToken, """
                {
                  viewer {
                    id
                    name
                    email
                    organization {
                      id
                      name
                    }
                  }
                }
                """);

        JsonNode viewer = response.path("data").path("viewer");
        if (viewer.isMissingNode()) {
            throw new IllegalStateException("Failed to fetch Linear viewer");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("viewerId", viewer.path("id").asText(""));
        metadata.put("name", viewer.path("name").asText(""));
        metadata.put("email", viewer.path("email").asText(""));
        metadata.put("organizationId", viewer.path("organization").path("id").asText(""));
        metadata.put("organizationName", viewer.path("organization").path("name").asText(""));
        return metadata;
    }

    private JsonNode executeGraphQl(String accessToken, String query) {
        JsonNode response = linearWebClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .bodyValue(Map.of("query", query))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || response.has("errors")) {
            throw new IllegalStateException("Linear API request failed");
        }
        return response;
    }

    private MultiValueMap<String, String> buildTokenForm(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "authorization_code");
        return form;
    }

    private String buildIssueTitle(JsonNode data, String action) {
        String stateName = data.path("state").path("name").asText("");
        String title = data.path("title").asText("");
        if (!stateName.isBlank() && ("update".equals(action) || "create".equals(action))) {
            return stateName + " - " + title;
        }
        return capitalize(action) + " issue - " + title;
    }

    private String mapIssueEventType(String stateType, String stateName) {
        if ("completed".equals(stateType) || "Done".equalsIgnoreCase(stateName)) {
            return "issue_closed";
        }
        if ("canceled".equals(stateType) || "Canceled".equalsIgnoreCase(stateName)) {
            return "issue_canceled";
        }
        return "issue_updated";
    }

    private String teamLabel(JsonNode team) {
        String key = team.path("key").asText("");
        String name = team.path("name").asText("");
        return key.isBlank() ? name : key + " - " + name;
    }

    private LocalDateTime extractTimestamp(JsonNode data) {
        String value = data.path("updatedAt").asText(data.path("createdAt").asText(""));
        if (value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
    }

    private void collectTeamIds(JsonNode node, List<String> teamIds) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            if (node.hasNonNull("teamId")) {
                String teamId = node.path("teamId").asText("");
                if (!teamId.isBlank()) {
                    teamIds.add(teamId);
                }
            }
            if (node.has("team") && node.path("team").path("id").isTextual()) {
                String teamId = node.path("team").path("id").asText("");
                if (!teamId.isBlank()) {
                    teamIds.add(teamId);
                }
            }
            if (node.has("teamIds") && node.path("teamIds").isArray()) {
                for (JsonNode teamId : node.path("teamIds")) {
                    if (teamId.isTextual() && !teamId.asText("").isBlank()) {
                        teamIds.add(teamId.asText(""));
                    }
                }
            }
            node.fields().forEachRemaining(entry -> collectTeamIds(entry.getValue(), teamIds));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTeamIds(child, teamIds);
            }
        }
    }

    private JsonNode findFirst(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            if (node.has(fieldName)) {
                return node.get(fieldName);
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode match = findFirst(entry.getValue(), fieldName);
                if (match != null) {
                    return match;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode match = findFirst(child, fieldName);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private String hmacHex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(body);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify Linear webhook signature", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Updated";
        }
        return value.substring(0, 1).toUpperCase(Locale.ENGLISH) + value.substring(1);
    }

    public record ConnectionResult(
            String accessToken,
            String refreshToken,
            Map<String, Object> metadata
    ) {
    }
}
