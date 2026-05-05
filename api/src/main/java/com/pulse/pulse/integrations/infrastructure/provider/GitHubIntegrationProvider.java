package com.pulse.pulse.integrations.infrastructure.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.integrations.application.IntegrationEventPayload;
import com.pulse.pulse.integrations.application.IntegrationResourceOption;
import com.pulse.pulse.integrations.domain.UserIntegration;
import com.pulse.pulse.platform.observability.PulseObservability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class GitHubIntegrationProvider {

    public static final String PROVIDER_NAME = "github";
    public static final String SELECTED_RESOURCES_KEY = "selectedResources";

    private final WebClient gitHubWebClient;
    private final ObjectMapper objectMapper;
    private final PulseObservability observability;

    @Value("${github.app-id:}")
    private String appId;

    @Value("${github.app-install-url:}")
    private String installUrl;

    @Value("${github.app.private-key:}")
    private String privateKeyPem;

    @Value("${github.webhook-secret:}")
    private String webhookSecret;

    public GitHubIntegrationProvider(@Qualifier("gitHubWebClient") WebClient gitHubWebClient,
                                     ObjectMapper objectMapper,
                                     PulseObservability observability) {
        this.gitHubWebClient = gitHubWebClient;
        this.objectMapper = objectMapper;
        this.observability = observability;
    }

    public String buildInstallUrl(String state) {
        if (installUrl == null || installUrl.isBlank()) {
            throw new IllegalStateException("GitHub app install URL is not configured");
        }

        String separator = installUrl.contains("?") ? "&" : "?";
        return installUrl + separator + "state=" + state;
    }

    public Map<String, Object> fetchInstallationMetadata(long installationId) {
        return observability.observe("pulse.github.installation.metadata", observation -> {
            observability.low(observation, "operation", "fetch_installation_metadata");
            observability.high(observation, "installation.id", installationId);
        }, () -> {
            JsonNode installation = gitHubWebClient.get()
                    .uri("/app/installations/{installationId}", installationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + createAppJwt())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (installation == null) {
                throw new IllegalStateException("Failed to load GitHub installation details");
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("installationId", installationId);
            metadata.put("accountLogin", installation.path("account").path("login").asText(""));
            metadata.put("accountType", installation.path("account").path("type").asText(""));
            metadata.put("repositorySelection", installation.path("repository_selection").asText(""));
            return metadata;
        });
    }

    public String createInstallationAccessToken(UserIntegration integration) {
        Object installationId = integration.getMetadata() != null
                ? integration.getMetadata().get("installationId")
                : null;
        if (installationId == null) {
            throw new IllegalStateException("GitHub installation is not configured");
        }
        return createInstallationAccessToken(Long.parseLong(installationId.toString()));
    }

    public String createInstallationAccessToken(long installationId) {
        return observability.observe("pulse.github.installation.token", observation -> {
            observability.low(observation, "operation", "create_installation_token");
            observability.high(observation, "installation.id", installationId);
        }, () -> {
            JsonNode response = gitHubWebClient.post()
                    .uri("/app/installations/{installationId}/access_tokens", installationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + createAppJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || response.path("token").asText("").isBlank()) {
                throw new IllegalStateException("Failed to create GitHub installation token");
            }

            return response.path("token").asText();
        });
    }

    public List<IntegrationResourceOption> listResources(UserIntegration integration) {
        return observability.observe("pulse.github.resources.list", observation -> {
            observability.low(observation, "operation", "list_resources");
            observability.high(observation, "installation.id",
                    integration.getMetadata() != null ? integration.getMetadata().get("installationId") : null);
        }, () -> {
            String token = createInstallationAccessToken(integration);
            JsonNode response = gitHubWebClient.get()
                    .uri("/installation/repositories?per_page=100")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("repositories").isArray()) {
                return List.of();
            }

            List<IntegrationResourceOption> resources = new ArrayList<>();
            for (JsonNode repo : response.path("repositories")) {
                String fullName = repo.path("full_name").asText("");
                if (!fullName.isBlank()) {
                    resources.add(new IntegrationResourceOption(fullName, fullName, "repository"));
                }
            }
            resources.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
            return resources;
        });
    }

    public Map<String, Object> applySelectedResources(UserIntegration integration, List<String> selectedIds) {
        List<IntegrationResourceOption> options = listResources(integration);
        Map<String, String> byId = new LinkedHashMap<>();
        for (IntegrationResourceOption option : options) {
            byId.put(option.id(), option.label());
        }

        List<Map<String, String>> selected = new ArrayList<>();
        for (String selectedId : selectedIds) {
            String label = byId.get(selectedId);
            if (label != null) {
                selected.add(Map.of("id", selectedId, "name", label, "type", "repository"));
            }
        }

        Map<String, Object> metadata = integration.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(integration.getMetadata());
        metadata.put(SELECTED_RESOURCES_KEY, selected);
        return metadata;
    }

    public List<String> getSelectedRepositoryNames(Map<String, Object> metadata) {
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

    public String buildScopeSummary(Map<String, Object> metadata) {
        List<Map<String, String>> selected = getSelectedResourceMaps(metadata);
        if (selected.isEmpty()) {
            return "Select repositories";
        }
        if (selected.size() == 1) {
            return selected.get(0).getOrDefault("name", selected.get(0).get("id"));
        }
        return selected.size() + " repositories selected";
    }

    public JsonNode verifyAndParseWebhook(Map<String, String> headers, byte[] body) {
        String providedSignature = header(headers, "x-hub-signature-256");
        if (providedSignature == null || webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("GitHub webhook verification is not configured");
        }

        String computed = "sha256=" + hmacHex(body, webhookSecret);
        if (!constantTimeEquals(providedSignature, computed)) {
            throw new IllegalArgumentException("Invalid GitHub webhook signature");
        }

        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid GitHub webhook payload", e);
        }
    }

    public String extractDeliveryId(Map<String, String> headers) {
        String deliveryId = header(headers, "x-github-delivery");
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new IllegalArgumentException("Missing GitHub delivery id");
        }
        return deliveryId;
    }

    public String extractRepositoryFullName(JsonNode payload) {
        return payload.path("repository").path("full_name").asText("");
    }

    public List<IntegrationEventPayload> mapWebhookEvents(Map<String, String> headers, JsonNode payload) {
        String event = header(headers, "x-github-event");
        if (event == null || event.isBlank()) {
            return List.of();
        }

        String repoFullName = extractRepositoryFullName(payload);
        if (repoFullName.isBlank()) {
            return List.of();
        }

        return switch (event) {
            case "push" -> mapPushEvent(payload, repoFullName);
            case "pull_request" -> mapPullRequestEvent(payload);
            case "issues" -> mapIssueEvent(payload);
            case "create" -> mapCreateEvent(payload, repoFullName);
            default -> List.of();
        };
    }

    public List<IntegrationEventPayload> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
        return observability.observe("pulse.github.events.fetch", observation -> {
            observability.low(observation, "operation", "fetch_recent_events");
            observability.high(observation, "installation.id",
                    integration.getMetadata() != null ? integration.getMetadata().get("installationId") : null);
        }, () -> {
            List<String> repositories = getSelectedRepositoryNames(integration.getMetadata());
            if (repositories.isEmpty()) {
                return List.of();
            }

            String token = createInstallationAccessToken(integration);
            List<IntegrationEventPayload> events = new ArrayList<>();
            for (String repoFullName : repositories) {
                String[] parts = repoFullName.split("/");
                if (parts.length != 2) {
                    continue;
                }

                fetchRecentCommits(token, parts[0], parts[1], since).forEach(events::add);
                fetchRecentIssues(token, parts[0], parts[1], since).forEach(events::add);
            }
            return events;
        });
    }

    public String getAccountLabel() {
        return "Installation";
    }

    public String getAccountValue(Map<String, Object> metadata) {
        String login = metadata != null ? String.valueOf(metadata.getOrDefault("accountLogin", "")) : "";
        return login.isBlank() ? "GitHub App" : "@" + login;
    }

    public String getEventsLabel() {
        return "Repo activity";
    }

    private List<IntegrationEventPayload> mapPushEvent(JsonNode payload, String repoFullName) {
        String ref = payload.path("ref").asText("");
        String branch = ref.replaceFirst("^refs/heads/", "");
        int commitCount = payload.path("commits").isArray() ? payload.path("commits").size() : 0;
        String title = "Pushed " + commitCount + " commit" + (commitCount == 1 ? "" : "s") +
                " to " + branch + " on " + repoFullName;
        String externalId = "push:" + repoFullName + ":" + payload.path("after").asText("");
        LocalDateTime timestamp = parseTimestamp(payload.path("head_commit").path("timestamp").asText(""));
        return List.of(new IntegrationEventPayload(
                "commit",
                title,
                externalId,
                timestamp != null ? timestamp : LocalDateTime.now(ZoneOffset.UTC),
                Map.of("repo", repoFullName, "branch", branch, "commitCount", commitCount)
        ));
    }

    private List<IntegrationEventPayload> mapPullRequestEvent(JsonNode payload) {
        JsonNode pr = payload.path("pull_request");
        if (pr.isMissingNode()) {
            return List.of();
        }

        String action = payload.path("action").asText("updated");
        String title = capitalize(action) + " PR - " + pr.path("title").asText("");
        String externalId = "pr:" + pr.path("id").asText("") + ":" + action + ":" + pr.path("updated_at").asText("");
        LocalDateTime timestamp = parseTimestamp(pr.path("updated_at").asText(""));
        return List.of(new IntegrationEventPayload(
                "pull_request",
                title,
                externalId,
                timestamp != null ? timestamp : LocalDateTime.now(ZoneOffset.UTC),
                Map.of(
                        "repo", payload.path("repository").path("full_name").asText(""),
                        "number", pr.path("number").asInt(0),
                        "action", action
                )
        ));
    }

    private List<IntegrationEventPayload> mapIssueEvent(JsonNode payload) {
        JsonNode issue = payload.path("issue");
        if (issue.isMissingNode()) {
            return List.of();
        }

        String action = payload.path("action").asText("updated");
        String title = capitalize(action) + " issue - " + issue.path("title").asText("");
        String externalId = "issue:" + issue.path("id").asText("") + ":" + action + ":" + issue.path("updated_at").asText("");
        LocalDateTime timestamp = parseTimestamp(issue.path("updated_at").asText(""));
        return List.of(new IntegrationEventPayload(
                "issue",
                title,
                externalId,
                timestamp != null ? timestamp : LocalDateTime.now(ZoneOffset.UTC),
                Map.of(
                        "repo", payload.path("repository").path("full_name").asText(""),
                        "number", issue.path("number").asInt(0),
                        "action", action
                )
        ));
    }

    private List<IntegrationEventPayload> mapCreateEvent(JsonNode payload, String repoFullName) {
        String refType = payload.path("ref_type").asText("");
        String ref = payload.path("ref").asText("");
        String title = "Created " + refType + (ref.isBlank() ? "" : " " + ref) + " in " + repoFullName;
        String externalId = "create:" + repoFullName + ":" + refType + ":" + ref;
        return List.of(new IntegrationEventPayload(
                "create",
                title,
                externalId,
                LocalDateTime.now(ZoneOffset.UTC),
                Map.of("repo", repoFullName, "refType", refType, "ref", ref)
        ));
    }

    private List<IntegrationEventPayload> fetchRecentCommits(String token,
                                                             String owner,
                                                             String repo,
                                                             LocalDateTime since) {
        JsonNode commits = gitHubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/commits")
                        .queryParam("since", since.toInstant(ZoneOffset.UTC).toString())
                        .queryParam("per_page", 25)
                        .build(owner, repo))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (commits == null || !commits.isArray()) {
            return List.of();
        }

        List<IntegrationEventPayload> events = new ArrayList<>();
        String repoFullName = owner + "/" + repo;
        for (JsonNode commit : commits) {
            String sha = commit.path("sha").asText("");
            if (sha.isBlank()) {
                continue;
            }

            String message = commit.path("commit").path("message").asText("");
            String title = firstLine(message);
            String timestampValue = commit.path("commit").path("author").path("date").asText("");
            LocalDateTime timestamp = parseTimestamp(timestampValue);
            if (timestamp == null || timestamp.isBefore(since)) {
                continue;
            }

            events.add(new IntegrationEventPayload(
                    "commit",
                    title,
                    "commit:" + repoFullName + ":" + sha,
                    timestamp,
                    Map.of("repo", repoFullName, "sha", sha)
            ));
        }
        return events;
    }

    private List<IntegrationEventPayload> fetchRecentIssues(String token,
                                                            String owner,
                                                            String repo,
                                                            LocalDateTime since) {
        JsonNode issues = gitHubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/issues")
                        .queryParam("state", "all")
                        .queryParam("since", since.toInstant(ZoneOffset.UTC).toString())
                        .queryParam("per_page", 25)
                        .build(owner, repo))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (issues == null || !issues.isArray()) {
            return List.of();
        }

        List<IntegrationEventPayload> events = new ArrayList<>();
        String repoFullName = owner + "/" + repo;
        for (JsonNode issue : issues) {
            String updatedAt = issue.path("updated_at").asText("");
            LocalDateTime timestamp = parseTimestamp(updatedAt);
            if (timestamp == null || timestamp.isBefore(since)) {
                continue;
            }

            boolean pullRequest = issue.has("pull_request");
            String state = issue.path("state").asText("open");
            int number = issue.path("number").asInt(0);
            String title = issue.path("title").asText("");
            if (pullRequest) {
                events.add(new IntegrationEventPayload(
                        "pull_request",
                        state.toUpperCase(Locale.ENGLISH) + " PR - " + title,
                        "pr:" + repoFullName + ":" + number + ":" + updatedAt,
                        timestamp,
                        Map.of("repo", repoFullName, "number", number, "state", state)
                ));
            } else {
                events.add(new IntegrationEventPayload(
                        "issue",
                        state.toUpperCase(Locale.ENGLISH) + " issue - " + title,
                        "issue:" + repoFullName + ":" + number + ":" + updatedAt,
                        timestamp,
                        Map.of("repo", repoFullName, "number", number, "state", state)
                ));
            }
        }
        return events;
    }

    private String createAppJwt() {
        if (appId == null || appId.isBlank() || privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalStateException("GitHub app credentials are not configured");
        }

        try {
            long now = Instant.now().getEpochSecond();
            String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
            String payloadJson = "{\"iat\":" + (now - 60) + ",\"exp\":" + (now + 540) + ",\"iss\":\"" + appId + "\"}";

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + payload;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(readPrivateKey(privateKeyPem));
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String signed = encoder.encodeToString(signature.sign());

            return signingInput + "." + signed;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create GitHub app JWT", e);
        }
    }

    private PrivateKey readPrivateKey(String pem) throws Exception {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String stripped = pem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);

        if (pkcs1) {
            keyBytes = wrapPkcs1InPkcs8(keyBytes);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    // Wraps a PKCS#1 RSAPrivateKey DER blob in PKCS#8 PrivateKeyInfo so the JDK can parse it.
    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        byte[] prefix = {
                0x30, (byte) 0x82, 0, 0,           // SEQUENCE (length placeholder at [2..3])
                0x02, 0x01, 0x00,                   // INTEGER 0 (version)
                0x30, 0x0d,                          // SEQUENCE (algorithmIdentifier)
                0x06, 0x09,                          //   OID 1.2.840.113549.1.1.1 (rsaEncryption)
                0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00,                          //   NULL
                0x04, (byte) 0x82, 0, 0             // OCTET STRING (length placeholder at [24..25])
        };
        int octetLen = pkcs1.length;
        prefix[24] = (byte) (octetLen >> 8);
        prefix[25] = (byte) octetLen;
        int totalLen = prefix.length - 4 + pkcs1.length;
        prefix[2] = (byte) (totalLen >> 8);
        prefix[3] = (byte) totalLen;

        byte[] pkcs8 = new byte[prefix.length + pkcs1.length];
        System.arraycopy(prefix, 0, pkcs8, 0, prefix.length);
        System.arraycopy(pkcs1, 0, pkcs8, prefix.length, pkcs1.length);
        return pkcs8;
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
            throw new IllegalStateException("Failed to verify GitHub webhook signature", e);
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

    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
    }

    private String firstLine(String value) {
        String line = value == null ? "" : value.split("\n", 2)[0].trim();
        if (line.isBlank()) {
            return "Commit";
        }
        return line.length() > 120 ? line.substring(0, 117) + "..." : line;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Updated";
        }
        return value.substring(0, 1).toUpperCase(Locale.ENGLISH) + value.substring(1);
    }
}
