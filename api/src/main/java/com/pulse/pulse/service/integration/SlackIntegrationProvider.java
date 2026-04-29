package com.pulse.pulse.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.entity.IntegrationEvent;
import com.pulse.pulse.entity.UserIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.*;
import java.util.*;

@Service("slack")
@Slf4j
public class SlackIntegrationProvider implements IntegrationProvider {

    private final WebClient slackWebClient;

    @Value("${slack.client-id:}")
    private String clientId;

    @Value("${slack.client-secret:}")
    private String clientSecret;

    public SlackIntegrationProvider(@Qualifier("slackWebClient") WebClient slackWebClient) {
        this.slackWebClient = slackWebClient;
    }

    @Override
    public String getProviderName() {
        return "slack";
    }

    @Override
    public boolean requiresOAuth() {
        return true;
    }

    @Override
    public String buildOAuthUrl(String state, String redirectUri) {
        return "https://slack.com/oauth/v2/authorize" +
                "?client_id=" + clientId +
                "&scope=channels:history,channels:read,users:read" +
                "&user_scope=channels:history,channels:read" +
                "&state=" + state +
                "&redirect_uri=" + redirectUri;
    }

    @Override
    public String exchangeCode(String code, String redirectUri) {
        JsonNode response = slackWebClient.post()
                .uri("/oauth.v2.access")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("client_id=" + clientId +
                        "&client_secret=" + clientSecret +
                        "&code=" + code +
                        "&redirect_uri=" + redirectUri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.path("ok").asBoolean()) {
            String error = response != null ? response.path("error").asText("unknown") : "null response";
            log.error("Slack OAuth response: {}", response);
            throw new RuntimeException("Slack OAuth failed: " + error);
        }

        String botToken = response.path("access_token").asText("");
        if (botToken.isEmpty()) {
            log.warn("No bot token, falling back to user token");
            botToken = response.path("authed_user").path("access_token").asText("");
        }
        log.info("Slack token type: {}", botToken.startsWith("xoxb-") ? "bot" : botToken.startsWith("xoxp-") ? "user" : "unknown");
        return botToken;
    }

    @Override
    public List<IntegrationEvent> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
        String token = integration.getAccessToken();

        JsonNode channelsResponse = slackWebClient.get()
                .uri("/conversations.list?types=public_channel&limit=20")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (channelsResponse == null || !channelsResponse.path("ok").asBoolean()) {
            String error = channelsResponse != null ? channelsResponse.toString() : "null";
            log.warn("Failed to fetch Slack channels: {}", error);
            return Collections.emptyList();
        }

        long sinceEpoch = since.toEpochSecond(ZoneOffset.UTC);
        List<IntegrationEvent> result = new ArrayList<>();

        JsonNode channels = channelsResponse.path("channels");
        for (JsonNode channel : channels) {
            if (!channel.path("is_member").asBoolean()) continue;

            String channelId = channel.path("id").asText();
            String channelName = channel.path("name").asText();

            JsonNode historyResponse = slackWebClient.get()
                    .uri("/conversations.history?channel={channel}&oldest={oldest}&limit=50",
                            channelId, String.valueOf(sinceEpoch))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (historyResponse == null || !historyResponse.path("ok").asBoolean()) continue;

            for (JsonNode message : historyResponse.path("messages")) {
                if (!"message".equals(message.path("type").asText())) continue;
                if (message.has("subtype")) continue;

                String ts = message.path("ts").asText();
                String text = message.path("text").asText("");
                String title = text.length() > 100 ? text.substring(0, 97) + "..." : text;
                if (title.isEmpty()) title = "Message in #" + channelName;

                long epochSeconds = Long.parseLong(ts.split("\\.")[0]);
                LocalDateTime timestamp = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);

                result.add(IntegrationEvent.builder()
                        .userId(integration.getUserId())
                        .provider("slack")
                        .eventType("message")
                        .title(title)
                        .externalId(channelId + "_" + ts)
                        .eventTimestamp(timestamp)
                        .rawPayload(Map.of("channel", channelName, "channelId", channelId))
                        .build());
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> fetchMetadata(String accessToken) {
        JsonNode response = slackWebClient.get()
                .uri("/auth.test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.path("ok").asBoolean()) {
            throw new RuntimeException("Failed to validate Slack token");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user", response.path("user").asText());
        metadata.put("team", response.path("team").asText());
        metadata.put("teamId", response.path("team_id").asText());
        return metadata;
    }
}
