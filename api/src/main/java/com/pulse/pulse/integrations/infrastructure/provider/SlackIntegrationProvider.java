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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class SlackIntegrationProvider {

    public static final String PROVIDER_NAME = "slack";
    public static final String SELECTED_RESOURCES_KEY = "selectedResources";

    private final WebClient slackWebClient;
    private final ObjectMapper objectMapper;

    @Value("${slack.client-id:}")
    private String clientId;

    @Value("${slack.client-secret:}")
    private String clientSecret;

    @Value("${slack.redirect-uri:http://localhost:8080/api/integrations/slack/callback}")
    private String redirectUri;

    @Value("${slack.signing-secret:}")
    private String signingSecret;

    public SlackIntegrationProvider(@Qualifier("slackWebClient") WebClient slackWebClient,
                                    ObjectMapper objectMapper) {
        this.slackWebClient = slackWebClient;
        this.objectMapper = objectMapper;
    }

    public String buildOAuthUrl(String state) {
        return "https://slack.com/oauth/v2/authorize" +
                "?client_id=" + clientId +
                "&scope=channels:read,channels:history,groups:read,groups:history,team:read" +
                "&state=" + state +
                "&redirect_uri=" + redirectUri;
    }

    public ConnectionResult exchangeCode(String code) {
        JsonNode response = slackWebClient.post()
                .uri("/oauth.v2.access")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(buildOAuthForm(code)))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.path("ok").asBoolean()) {
            String error = response != null ? response.path("error").asText("unknown") : "null response";
            throw new IllegalStateException("Slack OAuth failed: " + error);
        }

        String token = response.path("access_token").asText("");
        if (token.isBlank()) {
            token = response.path("authed_user").path("access_token").asText("");
        }
        if (token.isBlank()) {
            throw new IllegalStateException("Slack OAuth did not return an access token");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("team", response.path("team").path("name").asText(""));
        metadata.put("teamId", response.path("team").path("id").asText(""));
        metadata.put("authedUserId", response.path("authed_user").path("id").asText(""));
        metadata.put("botUserId", response.path("bot_user_id").asText(""));

        JsonNode authTest = slackWebClient.get()
                .uri("/auth.test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (authTest != null && authTest.path("ok").asBoolean()) {
            metadata.put("user", authTest.path("user").asText(""));
        }

        return new ConnectionResult(token, null, metadata);
    }

    public List<IntegrationResourceOption> listResources(UserIntegration integration) {
        JsonNode response = slackWebClient.get()
                .uri("/conversations.list?types=public_channel,private_channel&limit=200")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration.getAccessToken())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.path("ok").asBoolean()) {
            return List.of();
        }

        List<IntegrationResourceOption> resources = new ArrayList<>();
        for (JsonNode channel : response.path("channels")) {
            String id = channel.path("id").asText("");
            String name = channel.path("name").asText("");
            if (!id.isBlank() && !name.isBlank()) {
                resources.add(new IntegrationResourceOption(id, "#" + name, "channel"));
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
                selected.add(Map.of("id", id, "name", label, "type", "channel"));
            }
        }

        Map<String, Object> metadata = integration.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(integration.getMetadata());
        metadata.put(SELECTED_RESOURCES_KEY, selected);
        return metadata;
    }

    public List<IntegrationEventPayload> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
        List<Map<String, String>> selectedChannels = getSelectedResourceMaps(integration.getMetadata());
        if (selectedChannels.isEmpty()) {
            return List.of();
        }

        long oldest = since.toEpochSecond(ZoneOffset.UTC);
        List<IntegrationEventPayload> events = new ArrayList<>();
        for (Map<String, String> channel : selectedChannels) {
            String channelId = channel.get("id");
            JsonNode history = slackWebClient.get()
                    .uri("/conversations.history?channel={channel}&oldest={oldest}&limit=100",
                            channelId, String.valueOf(oldest))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration.getAccessToken())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (history == null || !history.path("ok").asBoolean()) {
                continue;
            }

            for (JsonNode message : history.path("messages")) {
                if (!isTrackableMessage(message)) {
                    continue;
                }

                String ts = message.path("ts").asText("");
                LocalDateTime timestamp = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(Long.parseLong(ts.split("\\.")[0])),
                        ZoneOffset.UTC
                );
                String title = formatMessageTitle(message.path("text").asText(""), channel.getOrDefault("name", channelId));
                events.add(new IntegrationEventPayload(
                        "message",
                        title,
                        "slack:" + channelId + ":" + ts,
                        timestamp,
                        Map.of("channelId", channelId, "channel", channel.getOrDefault("name", channelId))
                ));
            }
        }

        return events;
    }

    public JsonNode verifyAndParseWebhook(Map<String, String> headers, byte[] body) {
        String timestamp = header(headers, "x-slack-request-timestamp");
        String signature = header(headers, "x-slack-signature");
        if (timestamp == null || signature == null || signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalStateException("Slack webhook verification is not configured");
        }

        long requestTimestamp = Long.parseLong(timestamp);
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - requestTimestamp) > 300) {
            throw new IllegalArgumentException("Slack webhook timestamp is too old");
        }

        String base = "v0:" + timestamp + ":" + new String(body, StandardCharsets.UTF_8);
        String computed = "v0=" + hmacHex(base.getBytes(StandardCharsets.UTF_8), signingSecret);
        if (!constantTimeEquals(signature, computed)) {
            throw new IllegalArgumentException("Invalid Slack webhook signature");
        }

        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Slack webhook payload", e);
        }
    }

    public String extractDeliveryId(JsonNode payload) {
        if ("url_verification".equals(payload.path("type").asText())) {
            return "challenge:" + payload.path("challenge").asText("");
        }
        String eventId = payload.path("event_id").asText("");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("Missing Slack event_id");
        }
        return eventId;
    }

    public boolean isUrlVerification(JsonNode payload) {
        return "url_verification".equals(payload.path("type").asText());
    }

    public String extractChallenge(JsonNode payload) {
        return payload.path("challenge").asText("");
    }

    public String extractTeamId(JsonNode payload) {
        return payload.path("team_id").asText("");
    }

    public String extractChannelId(JsonNode payload) {
        return payload.path("event").path("channel").asText("");
    }

    public List<IntegrationEventPayload> mapWebhookEvents(UserIntegration integration, JsonNode payload) {
        if (!"event_callback".equals(payload.path("type").asText())) {
            return List.of();
        }

        JsonNode event = payload.path("event");
        if (!"message".equals(event.path("type").asText()) || !isTrackableMessage(event)) {
            return List.of();
        }

        String channelId = event.path("channel").asText("");
        String ts = event.path("ts").asText("");
        if (channelId.isBlank() || ts.isBlank()) {
            return List.of();
        }

        String channelLabel = getSelectedResourceMaps(integration.getMetadata()).stream()
                .filter(resource -> channelId.equals(resource.get("id")))
                .map(resource -> resource.getOrDefault("name", channelId))
                .findFirst()
                .orElse(channelId);

        LocalDateTime timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(Long.parseLong(ts.split("\\.")[0])),
                ZoneOffset.UTC
        );

        return List.of(new IntegrationEventPayload(
                "message",
                formatMessageTitle(event.path("text").asText(""), channelLabel),
                "slack:" + channelId + ":" + ts,
                timestamp,
                Map.of("channelId", channelId, "channel", channelLabel)
        ));
    }

    public String getAccountLabel() {
        return "Workspace";
    }

    public String getAccountValue(Map<String, Object> metadata) {
        return metadata != null ? String.valueOf(metadata.getOrDefault("team", "unknown")) : "unknown";
    }

    public String getEventsLabel() {
        return "Channel activity";
    }

    public String buildScopeSummary(Map<String, Object> metadata) {
        List<Map<String, String>> selected = getSelectedResourceMaps(metadata);
        if (selected.isEmpty()) {
            return "Select channels";
        }
        if (selected.size() == 1) {
            return selected.get(0).getOrDefault("name", selected.get(0).get("id"));
        }
        return selected.size() + " channels selected";
    }

    public List<Map<String, String>> getSelectedResourceMaps(Map<String, Object> metadata) {
        Object raw = metadata != null ? metadata.get(SELECTED_RESOURCES_KEY) : null;
        if (raw == null) {
            return List.of();
        }
        return objectMapper.convertValue(raw, new TypeReference<>() {});
    }

    private MultiValueMap<String, String> buildOAuthForm(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        return form;
    }

    private boolean isTrackableMessage(JsonNode message) {
        if (!"message".equals(message.path("type").asText())) {
            return false;
        }
        return !message.hasNonNull("subtype");
    }

    private String formatMessageTitle(String text, String channelLabel) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return "Message in " + channelLabel;
        }
        return trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
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
            throw new IllegalStateException("Failed to verify Slack webhook signature", e);
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

    public record ConnectionResult(
            String accessToken,
            String refreshToken,
            Map<String, Object> metadata
    ) {
    }
}
