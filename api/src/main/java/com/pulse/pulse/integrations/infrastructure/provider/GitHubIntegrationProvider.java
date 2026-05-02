package com.pulse.pulse.integrations.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.pulse.integrations.application.IntegrationEventPayload;
import com.pulse.pulse.integrations.domain.UserIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service("github")
@Slf4j
public class GitHubIntegrationProvider implements IntegrationProvider {

    private final WebClient gitHubWebClient;
    private final WebClient tokenClient;

    @Value("${github.integration.client-id:}")
    private String clientId;

    @Value("${github.integration.client-secret:}")
    private String clientSecret;

    @Value("${github.integration.redirect-uri:http://localhost:8080/api/integrations/github/callback}")
    private String redirectUri;

    public GitHubIntegrationProvider(@Qualifier("gitHubWebClient") WebClient gitHubWebClient) {
        this.gitHubWebClient = gitHubWebClient;
        this.tokenClient = WebClient.builder()
                .baseUrl("https://github.com")
                .build();
    }

    @Override
    public String getProviderName() {
        return "github";
    }

    @Override
    public boolean requiresOAuth() {
        return true;
    }

    @Override
    public String buildOAuthUrl(String state, String redirectUri) {
        return "https://github.com/login/oauth/authorize" +
                "?client_id=" + clientId +
                "&scope=repo,read:user" +
                "&state=" + state +
                "&redirect_uri=" + redirectUri;
    }

    @Override
    public String exchangeCode(String code, String redirectUri) {
        JsonNode response = tokenClient.post()
                .uri("/login/oauth/access_token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "code", code,
                        "redirect_uri", redirectUri
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("access_token")) {
            throw new RuntimeException("Failed to exchange GitHub code for token");
        }
        return response.get("access_token").asText();
    }

    @Override
    public List<IntegrationEventPayload> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
        String token = integration.getAccessToken();
        Map<String, Object> meta = integration.getMetadata();
        String username = meta != null ? (String) meta.get("login") : null;

        if (username == null) {
            log.warn("No GitHub username in metadata, cannot fetch events");
            return Collections.emptyList();
        }

        JsonNode events = gitHubWebClient.get()
                .uri("/users/{username}/events?per_page=100", username)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (events == null || !events.isArray()) {
            return Collections.emptyList();
        }

        List<IntegrationEventPayload> result = new ArrayList<>();
        for (JsonNode event : events) {
            String type = event.get("type").asText();
            String eventId = event.get("id").asText();
            String createdAt = event.get("created_at").asText();
            LocalDateTime timestamp = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME);

            if (timestamp.isBefore(since)) continue;

            String title;
            String eventType;
            switch (type) {
                case "PushEvent":
                    String pushRef = event.path("payload").path("ref").asText("");
                    String branch = pushRef.replaceFirst("^refs/heads/", "");
                    String repo = event.path("repo").path("name").asText();
                    title = "Pushed to " + branch + " on " + repo;
                    eventType = "commit";
                    break;
                case "PullRequestEvent":
                    String action = event.path("payload").path("action").asText();
                    String prTitle = event.path("payload").path("pull_request").path("title").asText();
                    title = action.substring(0, 1).toUpperCase() + action.substring(1) + " PR - " + prTitle;
                    eventType = "pull_request";
                    break;
                case "IssuesEvent":
                    String issueAction = event.path("payload").path("action").asText();
                    String issueTitle = event.path("payload").path("issue").path("title").asText();
                    title = issueAction.substring(0, 1).toUpperCase() + issueAction.substring(1) + " issue - " + issueTitle;
                    eventType = "issue";
                    break;
                case "CreateEvent":
                    String refType = event.path("payload").path("ref_type").asText();
                    String ref = event.path("payload").path("ref").asText("");
                    String createRepo = event.path("repo").path("name").asText();
                    title = "Created " + refType + (ref.isEmpty() ? "" : " " + ref) + " in " + createRepo;
                    eventType = "create";
                    break;
                default:
                    continue;
            }

            result.add(new IntegrationEventPayload(
                    eventType,
                    title,
                    eventId,
                    timestamp,
                    Map.of("type", type)
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
        return "@" + (metadata != null ? metadata.getOrDefault("login", "unknown") : "unknown");
    }

    @Override
    public String getEventsLabel() {
        return "Events synced";
    }

    @Override
    public String getRedirectUri() {
        return redirectUri;
    }

    @Override
    public Map<String, Object> fetchMetadata(String accessToken) {
        JsonNode user = gitHubWebClient.get()
                .uri("/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (user == null) {
            throw new RuntimeException("Failed to fetch GitHub user info");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("login", user.get("login").asText());
        metadata.put("name", user.path("name").asText(""));
        metadata.put("avatarUrl", user.path("avatar_url").asText(""));
        return metadata;
    }
}
