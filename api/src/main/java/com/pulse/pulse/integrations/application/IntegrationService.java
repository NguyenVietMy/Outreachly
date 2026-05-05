package com.pulse.pulse.integrations.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.activity.application.ActivityIngestCommand;
import com.pulse.pulse.activity.application.ActivityIngestItem;
import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.activity.application.GitHubProjectSyncRequest;
import com.pulse.pulse.activity.application.GitHubProjectSyncService;
import com.pulse.pulse.activity.application.RepositorySnapshotView;
import com.pulse.pulse.integrations.domain.UserIntegration;
import com.pulse.pulse.integrations.domain.WebhookDelivery;
import com.pulse.pulse.integrations.infrastructure.persistence.UserIntegrationRepository;
import com.pulse.pulse.integrations.infrastructure.persistence.WebhookDeliveryRepository;
import com.pulse.pulse.integrations.infrastructure.provider.GitHubIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.LinearIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.ObsidianIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.SlackIntegrationProvider;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IntegrationService {

    private static final List<String> PROVIDER_ORDER = List.of("github", "obsidian", "slack", "linear");

    private final UserIntegrationRepository integrationRepo;
    private final DashboardService dashboardService;
    private final GitHubProjectSyncService gitHubProjectSyncService;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final GitHubIntegrationProvider gitHubProvider;
    private final SlackIntegrationProvider slackProvider;
    private final LinearIntegrationProvider linearProvider;
    private final ObsidianIntegrationProvider obsidianProvider;
    private final ObjectMapper objectMapper;
    private final PulseObservability observability;
    private final ConcurrentHashMap<String, ConnectionState> connectionStates = new ConcurrentHashMap<>();

    public IntegrationService(UserIntegrationRepository integrationRepo,
                              DashboardService dashboardService,
                              GitHubProjectSyncService gitHubProjectSyncService,
                              WebhookDeliveryRepository webhookDeliveryRepository,
                              GitHubIntegrationProvider gitHubProvider,
                              SlackIntegrationProvider slackProvider,
                              LinearIntegrationProvider linearProvider,
                              ObsidianIntegrationProvider obsidianProvider,
                              ObjectMapper objectMapper,
                              PulseObservability observability) {
        this.integrationRepo = integrationRepo;
        this.dashboardService = dashboardService;
        this.gitHubProjectSyncService = gitHubProjectSyncService;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.gitHubProvider = gitHubProvider;
        this.slackProvider = slackProvider;
        this.linearProvider = linearProvider;
        this.obsidianProvider = obsidianProvider;
        this.objectMapper = objectMapper;
        this.observability = observability;
    }

    public List<UserIntegration> getIntegrations(Long userId) {
        return integrationRepo.findByUserId(userId);
    }

    public List<IntegrationView> getIntegrationViews(Long userId) {
        List<UserIntegration> integrations = getIntegrations(userId);
        Map<String, UserIntegration> byProvider = new HashMap<>();
        for (UserIntegration integration : integrations) {
            byProvider.put(integration.getProvider(), integration);
        }

        List<IntegrationView> views = new ArrayList<>();
        for (String providerName : PROVIDER_ORDER) {
            UserIntegration integration = byProvider.get(providerName);
            if (integration != null && "connected".equals(integration.getStatus())) {
                views.add(toView(integration, userId));
            } else {
                views.add(new IntegrationView(
                        userId,
                        providerName,
                        "disconnected",
                        Map.of(),
                        null,
                        null,
                        "disconnected",
                        null,
                        defaultAccountLabel(providerName),
                        null,
                        defaultEventsLabel(providerName),
                        null,
                        disconnectedLabel(providerName),
                defaultScopeSummary(providerName),
                List.of(),
                List.of()
        ));
            }
        }

        return views;
    }

    public IntegrationView getIntegrationView(Long userId, String provider) {
        UserIntegration integration = integrationRepo.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + provider));
        return toView(integration, userId);
    }

    public Optional<UserIntegration> getIntegration(Long userId, String provider) {
        return integrationRepo.findByUserIdAndProvider(userId, provider);
    }

    public String getOAuthUrl(Long userId, String provider) {
        String state = createState(userId, provider);
        return switch (provider) {
            case "github" -> gitHubProvider.buildInstallUrl(state);
            case "slack" -> slackProvider.buildOAuthUrl(state);
            case "linear" -> linearProvider.buildOAuthUrl(state);
            default -> throw new IllegalArgumentException(provider + " does not use an external connect flow");
        };
    }

    @Transactional
    public UserIntegration handleOAuthCallback(String provider,
                                               String code,
                                               String state,
                                               Long installationId) {
        return observability.observe("pulse.integration.oauth.callback", observation -> {
            observability.low(observation, "provider", provider);
            observability.high(observation, "installation.id", installationId);
        }, () -> {
            ConnectionState connectionState = consumeState(state, provider);
            return switch (provider) {
                case "github" -> connectGitHubInstallation(connectionState.userId(), installationId);
                case "slack" -> connectSlack(connectionState.userId(), code);
                case "linear" -> connectLinear(connectionState.userId(), code);
                default -> throw new IllegalArgumentException("Unknown provider: " + provider);
            };
        });
    }

    @Transactional
    public UserIntegration connectObsidian(Long userId) {
        UserIntegration github = integrationRepo.findByUserIdAndProvider(userId, "github")
                .orElseThrow(() -> new RuntimeException("GitHub must be connected first"));

        if (!"connected".equals(github.getStatus())) {
            throw new RuntimeException("GitHub integration is not active");
        }

        UserIntegration integration = integrationRepo
                .findByUserIdAndProvider(userId, "obsidian")
                .orElse(UserIntegration.builder()
                        .userId(userId)
                        .provider("obsidian")
                        .build());

        integration.setStatus("connected");
        integration.setAccessToken(null);
        integration.setRefreshToken(null);
        integration.setMetadata(Map.of());
        integration.setWebhookStatus("pending_scope");
        integration.setLastWebhookError(null);
        integration.setLastWebhookErrorAt(null);
        return integrationRepo.save(integration);
    }

    @Transactional
    public void disconnect(Long userId, String provider) {
        integrationRepo.deleteByUserIdAndProvider(userId, provider);

        if ("github".equals(provider)) {
            integrationRepo.deleteByUserIdAndProvider(userId, "obsidian");
            gitHubProjectSyncService.clearRepositories(userId);
        }
        if ("obsidian".equals(provider)) {
            gitHubProjectSyncService.clearRepositories(userId);
        }
    }

    @Transactional
    public SyncResult sync(Long userId, String provider) {
        Timer.Sample sample = observability.timerSample();
        String result = "error";
        try {
            SyncResult syncResult = observability.observe("pulse.integration.sync", observation -> {
                observability.low(observation, "provider", provider);
                observability.low(observation, "mode", "manual");
                observability.high(observation, "user.id", userId);
            }, () -> {
                UserIntegration integration = integrationRepo
                        .findByUserIdAndProvider(userId, provider)
                        .orElseThrow(() -> new RuntimeException("Integration not found: " + provider));

                if (!"connected".equals(integration.getStatus())) {
                    throw new RuntimeException("Integration is not connected");
                }

                LocalDateTime since = integration.getLastSyncedAt() != null
                        ? integration.getLastSyncedAt()
                        : LocalDateTime.now(ZoneOffset.UTC).minusDays(7);

                List<IntegrationEventPayload> fetched = switch (provider) {
                    case "github" -> gitHubProvider.fetchRecentEvents(integration, since);
                    case "obsidian" -> obsidianProvider.fetchRecentEvents(resolveGitHubToken(userId), integration, since);
                    case "slack" -> slackProvider.fetchRecentEvents(integration, since);
                    case "linear" -> linearProvider.fetchRecentEvents(integration, since);
                    default -> throw new IllegalArgumentException("Unknown provider: " + provider);
                };

                int newEvents = ingestEvents(userId, provider, fetched);
                integration.setLastSyncedAt(LocalDateTime.now(ZoneOffset.UTC));
                integrationRepo.save(integration);

                if ("github".equals(provider)) {
                    syncGitHubProjects(userId);
                }

                return new SyncResult(newEvents, fetched.size());
            });
            result = "success";
            return syncResult;
        } finally {
            observability.stopTimer(sample, "pulse.integration.sync.duration",
                    "provider", provider,
                    "mode", "manual",
                    "result", result);
        }
    }

    @Transactional
    public void markProjectSyncRun(Long userId, String provider) {
        UserIntegration integration = integrationRepo.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + provider));

        Map<String, Object> metadata = integration.getMetadata() == null ? new HashMap<>() : new HashMap<>(integration.getMetadata());
        metadata.put("projectSyncLastRun", LocalDateTime.now(ZoneOffset.UTC).toString());
        integration.setMetadata(metadata);
        integrationRepo.save(integration);
    }

    public int syncGitHubProjects(Long userId) {
        return observability.observe("pulse.integration.sync", observation -> {
            observability.low(observation, "provider", "github");
            observability.low(observation, "mode", "project_sync");
            observability.high(observation, "user.id", userId);
        }, () -> {
            GitHubProjectSyncRequest request = getGitHubProjectSyncRequest(userId);
            int count = gitHubProjectSyncService.syncProjects(request);
            markProjectSyncRun(userId, "github");
            return count;
        });
    }

    public GitHubProjectSyncRequest getGitHubProjectSyncRequest(Long userId) {
        UserIntegration github = integrationRepo.findByUserIdAndProvider(userId, "github")
                .orElseThrow(() -> new RuntimeException("GitHub not connected"));
        return new GitHubProjectSyncRequest(
                github.getUserId(),
                gitHubProvider.createInstallationAccessToken(github),
                github.getMetadata(),
                gitHubProvider.getSelectedRepositoryNames(github.getMetadata())
        );
    }

    public List<GitHubRepositoryView> getGitHubRepositoryViews(Long userId) {
        return dashboardService.getGitHubRepositories(userId).stream()
                .map(this::toGitHubRepositoryView)
                .toList();
    }

    public List<IntegrationResourceOption> listResources(Long userId, String provider) {
        UserIntegration integration = integrationRepo.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + provider));

        return switch (provider) {
            case "github" -> gitHubProvider.listResources(integration);
            case "obsidian" -> getObsidianResourceOptions(userId);
            case "slack" -> slackProvider.listResources(integration);
            case "linear" -> linearProvider.listResources(integration);
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    @Transactional
    public UserIntegration updateSelectedResources(Long userId, String provider, List<String> selectedIds) {
        return observability.observe("pulse.integration.sync", observation -> {
            observability.low(observation, "provider", provider);
            observability.low(observation, "mode", "scope_update");
            observability.high(observation, "user.id", userId);
            observability.high(observation, "selected_repo_count", selectedIds != null ? selectedIds.size() : 0);
        }, () -> {
            UserIntegration integration = integrationRepo.findByUserIdAndProvider(userId, provider)
                    .orElseThrow(() -> new RuntimeException("Integration not found: " + provider));

            Map<String, Object> metadata = switch (provider) {
                case "github" -> gitHubProvider.applySelectedResources(integration, selectedIds);
                case "obsidian" -> obsidianProvider.applySelectedRepository(
                        integration,
                        getObsidianResourceMaps(userId),
                        selectedIds
                );
                case "slack" -> slackProvider.applySelectedResources(integration, selectedIds);
                case "linear" -> linearProvider.applySelectedResources(integration, selectedIds);
                default -> throw new IllegalArgumentException("Unknown provider: " + provider);
            };

            integration.setMetadata(metadata);
            integration.setWebhookStatus(selectedIds.isEmpty() ? "pending_scope" : "active");
            integration.setLastWebhookError(null);
            integration.setLastWebhookErrorAt(null);
            integrationRepo.save(integration);

            if ("github".equals(provider)) {
                normalizeObsidianScope(userId);
                syncGitHubProjects(userId);
            }
            if ("obsidian".equals(provider)) {
                gitHubProjectSyncService.clearRepositories(userId);
            }

            return integration;
        });
    }

    @Transactional
    public void handleGitHubWebhook(Map<String, String> headers, byte[] body) {
        Observation observation = observability.start("pulse.webhook.receive");
        observability.low(observation, "provider", "github");
        observability.low(observation, "event_type", header(headers, "x-github-event"));
        try {
            JsonNode payload = observability.observe("pulse.webhook.process", child -> {
                observability.low(child, "provider", "github");
                observability.low(child, "operation", "verify_signature");
            }, () -> gitHubProvider.verifyAndParseWebhook(headers, body));
            String deliveryId = gitHubProvider.extractDeliveryId(headers);
            observability.high(observation, "delivery.id", deliveryId);

            processWebhookDelivery("github", deliveryId, payload, headers, () -> {
                String repoFullName = gitHubProvider.extractRepositoryFullName(payload);

                List<IntegrationEventPayload> githubEvents = observability.observe("pulse.webhook.process", child -> {
                    observability.low(child, "provider", "github");
                    observability.low(child, "operation", "map_events");
                }, () -> gitHubProvider.mapWebhookEvents(headers, payload));

                for (UserIntegration integration : integrationRepo.findByProviderAndStatus("github", "connected")) {
                    if (matchesRepository(integration.getMetadata(), repoFullName) || matchesInstallation(integration.getMetadata(), payload)) {
                        ingestEvents(integration.getUserId(), "github", githubEvents);
                        markWebhookSuccess(integration);
                        if ("push".equalsIgnoreCase(header(headers, "x-github-event"))) {
                            observability.observe("pulse.webhook.process", child -> {
                                observability.low(child, "provider", "github");
                                observability.low(child, "operation", "project_sync_trigger");
                                observability.high(child, "user.id", integration.getUserId());
                            }, () -> syncGitHubProjects(integration.getUserId()));
                        }
                    }
                }

                List<IntegrationEventPayload> obsidianEvents = obsidianProvider.mapWebhookEvents(payload);
                if (!obsidianEvents.isEmpty()) {
                    for (UserIntegration integration : integrationRepo.findByProviderAndStatus("obsidian", "connected")) {
                        if (matchesRepository(integration.getMetadata(), repoFullName)) {
                            ingestEvents(integration.getUserId(), "obsidian", obsidianEvents);
                            markWebhookSuccess(integration);
                        }
                    }
                }
            });
            observability.low(observation, "result", "processed");
        } catch (RuntimeException e) {
            observation.error(e);
            observability.low(observation, "result", "rejected");
            throw e;
        } finally {
            observation.stop();
        }
    }

    @Transactional
    public SlackWebhookResult handleSlackWebhook(Map<String, String> headers, byte[] body) {
        return observability.observe("pulse.webhook.receive", observation -> {
            observability.low(observation, "provider", "slack");
        }, () -> {
            JsonNode payload = slackProvider.verifyAndParseWebhook(headers, body);
            if (slackProvider.isUrlVerification(payload)) {
                return new SlackWebhookResult(true, slackProvider.extractChallenge(payload));
            }

            String deliveryId = slackProvider.extractDeliveryId(payload);
            processWebhookDelivery("slack", deliveryId, payload, headers, () -> {
                String teamId = slackProvider.extractTeamId(payload);
                String channelId = slackProvider.extractChannelId(payload);

                for (UserIntegration integration : integrationRepo.findByProviderAndStatus("slack", "connected")) {
                    if (!matchesMetadataValue(integration.getMetadata(), "teamId", teamId) ||
                            !hasSelectedResource(integration.getMetadata(), channelId)) {
                        continue;
                    }

                    ingestEvents(integration.getUserId(), "slack", slackProvider.mapWebhookEvents(integration, payload));
                    markWebhookSuccess(integration);
                }
            });

            return new SlackWebhookResult(false, null);
        });
    }

    @Transactional
    public void handleLinearWebhook(Map<String, String> headers, byte[] body) {
        observability.observe("pulse.webhook.receive", observation -> {
            observability.low(observation, "provider", "linear");
        }, () -> {
            JsonNode payload = linearProvider.verifyAndParseWebhook(headers, body);
            String deliveryId = linearProvider.extractDeliveryId(headers);

            processWebhookDelivery("linear", deliveryId, payload, headers, () -> {
                String organizationId = linearProvider.extractOrganizationId(payload);
                List<String> teamIds = linearProvider.extractTeamIds(payload);
                List<IntegrationEventPayload> events = linearProvider.mapWebhookEvents(payload);

                for (UserIntegration integration : integrationRepo.findByProviderAndStatus("linear", "connected")) {
                    if (!organizationId.isBlank() && !matchesMetadataValue(integration.getMetadata(), "organizationId", organizationId)) {
                        continue;
                    }
                    if (!teamIds.isEmpty() && teamIds.stream().noneMatch(teamId -> hasSelectedResource(integration.getMetadata(), teamId))) {
                        continue;
                    }

                    ingestEvents(integration.getUserId(), "linear", events);
                    markWebhookSuccess(integration);
                }
            });
        });
    }

    private UserIntegration connectGitHubInstallation(Long userId, Long installationId) {
        if (installationId == null) {
            throw new IllegalArgumentException("GitHub installation id is required");
        }

        UserIntegration integration = integrationRepo
                .findByUserIdAndProvider(userId, "github")
                .orElse(UserIntegration.builder()
                        .userId(userId)
                        .provider("github")
                        .build());

        Map<String, Object> metadata = new HashMap<>(gitHubProvider.fetchInstallationMetadata(installationId));
        List<Map<String, String>> selectedResources = readSelectedResources(integration.getMetadata());
        if (!selectedResources.isEmpty()) {
            metadata.put("selectedResources", selectedResources);
        }

        integration.setAccessToken(null);
        integration.setRefreshToken(null);
        integration.setStatus("connected");
        integration.setMetadata(metadata);
        integration.setWebhookStatus(selectedResources.isEmpty() ? "pending_scope" : "active");
        integration.setLastWebhookError(null);
        integration.setLastWebhookErrorAt(null);
        return integrationRepo.save(integration);
    }

    private UserIntegration connectSlack(Long userId, String code) {
        SlackIntegrationProvider.ConnectionResult result = slackProvider.exchangeCode(code);
        return saveOAuthIntegration(userId, "slack", result.accessToken(), result.refreshToken(), result.metadata());
    }

    private UserIntegration connectLinear(Long userId, String code) {
        LinearIntegrationProvider.ConnectionResult result = linearProvider.exchangeCode(code);
        return saveOAuthIntegration(userId, "linear", result.accessToken(), result.refreshToken(), result.metadata());
    }

    private UserIntegration saveOAuthIntegration(Long userId,
                                                 String provider,
                                                 String accessToken,
                                                 String refreshToken,
                                                 Map<String, Object> metadata) {
        UserIntegration integration = integrationRepo
                .findByUserIdAndProvider(userId, provider)
                .orElse(UserIntegration.builder()
                        .userId(userId)
                        .provider(provider)
                        .build());

        List<Map<String, String>> selectedResources = readSelectedResources(integration.getMetadata());
        Map<String, Object> mergedMetadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        if (!selectedResources.isEmpty()) {
            mergedMetadata.put("selectedResources", selectedResources);
        }

        integration.setAccessToken(accessToken);
        integration.setRefreshToken(refreshToken);
        integration.setStatus("connected");
        integration.setMetadata(mergedMetadata);
        integration.setWebhookStatus(selectedResources.isEmpty() ? "pending_scope" : "active");
        integration.setLastWebhookError(null);
        integration.setLastWebhookErrorAt(null);
        return integrationRepo.save(integration);
    }

    private List<IntegrationResourceOption> getObsidianResourceOptions(Long userId) {
        return getObsidianResourceMaps(userId).stream()
                .map(resource -> new IntegrationResourceOption(resource.get("id"), resource.get("name"), "repository"))
                .toList();
    }

    private List<Map<String, String>> getObsidianResourceMaps(Long userId) {
        UserIntegration github = integrationRepo.findByUserIdAndProvider(userId, "github")
                .orElseThrow(() -> new RuntimeException("GitHub must be connected first"));
        return gitHubProvider.getSelectedResourceMaps(github.getMetadata());
    }

    private void normalizeObsidianScope(Long userId) {
        integrationRepo.findByUserIdAndProvider(userId, "obsidian").ifPresent(obsidian -> {
            List<Map<String, String>> available = getObsidianResourceMaps(userId);
            String selectedRepo = obsidianProvider.getSelectedRepository(obsidian.getMetadata());
            boolean stillValid = available.stream().anyMatch(resource -> resource.get("id").equals(selectedRepo));
            if (selectedRepo != null && !stillValid) {
                obsidian.setMetadata(Map.of());
                obsidian.setWebhookStatus("pending_scope");
                integrationRepo.save(obsidian);
            }
        });
    }

    private IntegrationView toView(UserIntegration integration, Long userId) {
        Map<String, Object> meta = integration.getMetadata() != null ? integration.getMetadata() : Map.of();
        String providerName = integration.getProvider();
        List<Integer> sparkline = dashboardService.getActivitySparkline(userId, providerName);

        int totalEvents = sparkline.stream().mapToInt(Integer::intValue).sum();
        return new IntegrationView(
                integration.getUserId(),
                providerName,
                integration.getStatus(),
                meta,
                integration.getLastSyncedAt(),
                integration.getLastWebhookReceivedAt(),
                integration.getWebhookStatus(),
                integration.getLastWebhookError(),
                accountLabel(providerName),
                accountValue(providerName, meta),
                eventsLabel(providerName),
                totalEvents + " this week",
                buildLastActivityLabel(integration),
                buildScopeSummary(providerName, meta),
                readSelectedResources(meta).stream().map(resource -> resource.get("id")).toList(),
                sparkline
        );
    }

    private String buildLastActivityLabel(UserIntegration integration) {
        if (integration.getLastWebhookReceivedAt() != null) {
            return "Webhook received " + relativeTime(integration.getLastWebhookReceivedAt());
        }
        if (integration.getLastSyncedAt() != null) {
            return "Manual sync " + relativeTime(integration.getLastSyncedAt());
        }
        return "Waiting for events";
    }

    private String relativeTime(LocalDateTime timestamp) {
        long minutes = ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now(ZoneOffset.UTC));
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        if (minutes < 1440) {
            return (minutes / 60) + " hr ago";
        }
        return (minutes / 1440) + " days ago";
    }

    private int ingestEvents(Long userId, String provider, List<IntegrationEventPayload> fetched) {
        if (fetched.isEmpty()) {
            return 0;
        }

        return dashboardService.ingestEvents(new ActivityIngestCommand(
                userId,
                provider,
                fetched.stream()
                        .map(event -> new ActivityIngestItem(
                                event.eventType(),
                                event.title(),
                                event.externalId(),
                                event.eventTimestamp(),
                                event.rawPayload()))
                        .toList()
        ));
    }

    private void processWebhookDelivery(String provider,
                                        String deliveryId,
                                        JsonNode payload,
                                        Map<String, String> headers,
                                        Runnable processor) {
        Observation observation = observability.start("pulse.webhook.process");
        String result = "processed";
        observability.low(observation, "provider", provider);
        observability.high(observation, "delivery.id", deliveryId);
        if (webhookDeliveryRepository.findByProviderAndDeliveryId(provider, deliveryId).isPresent()) {
            result = "duplicate";
            observability.low(observation, "result", result);
            observability.counter("pulse.webhook.deliveries",
                    "provider", provider,
                    "result", result).increment();
            observation.stop();
            return;
        }

        WebhookDelivery delivery = WebhookDelivery.builder()
                .provider(provider)
                .deliveryId(deliveryId)
                .status("received")
                .headers(new LinkedHashMap<>(headers))
                .payload(objectMapper.convertValue(payload, new TypeReference<>() {}))
                .build();
        webhookDeliveryRepository.save(delivery);

        try {
            processor.run();
            delivery.setStatus("processed");
            delivery.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
        } catch (Exception e) {
            result = "failed";
            delivery.setStatus("failed");
            delivery.setErrorMessage(e.getMessage());
            delivery.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
            observation.error(e);
            log.error("Webhook processing failed for provider={} deliveryId={}: {}", provider, deliveryId, e.getMessage(), e);
            throw e;
        } finally {
            webhookDeliveryRepository.save(delivery);
            observability.low(observation, "result", result);
            observability.counter("pulse.webhook.deliveries",
                    "provider", provider,
                    "result", result).increment();
            observation.stop();
        }
    }

    private void markWebhookSuccess(UserIntegration integration) {
        integration.setWebhookStatus("active");
        integration.setLastWebhookReceivedAt(LocalDateTime.now(ZoneOffset.UTC));
        integration.setLastWebhookError(null);
        integration.setLastWebhookErrorAt(null);
        integrationRepo.save(integration);
    }

    private boolean matchesRepository(Map<String, Object> metadata, String repoFullName) {
        return repoFullName != null && !repoFullName.isBlank() && hasSelectedResource(metadata, repoFullName);
    }

    private boolean matchesInstallation(Map<String, Object> metadata, JsonNode payload) {
        JsonNode installation = payload.path("installation");
        if (installation.isMissingNode()) {
            return false;
        }
        String installationId = installation.path("id").asText("");
        return matchesMetadataValue(metadata, "installationId", installationId);
    }

    private boolean matchesMetadataValue(Map<String, Object> metadata, String key, String expected) {
        if (metadata == null || expected == null || expected.isBlank()) {
            return false;
        }
        Object value = metadata.get(key);
        return value != null && expected.equals(String.valueOf(value));
    }

    private boolean hasSelectedResource(Map<String, Object> metadata, String id) {
        return readSelectedResources(metadata).stream()
                .anyMatch(resource -> id.equals(resource.get("id")));
    }

    private List<Map<String, String>> readSelectedResources(Map<String, Object> metadata) {
        Object raw = metadata != null ? metadata.get("selectedResources") : null;
        if (raw == null) {
            return List.of();
        }
        return objectMapper.convertValue(raw, new TypeReference<>() {});
    }

    private String resolveGitHubToken(Long userId) {
        UserIntegration github = integrationRepo.findByUserIdAndProvider(userId, "github")
                .orElseThrow(() -> new RuntimeException("GitHub must be connected first"));
        return gitHubProvider.createInstallationAccessToken(github);
    }

    private GitHubRepositoryView toGitHubRepositoryView(RepositorySnapshotView repository) {
        return new GitHubRepositoryView(
                repository.repoFullName(),
                repository.repoName(),
                repository.description(),
                repository.primaryLanguage(),
                repository.topics(),
                repository.languages(),
                repository.pushedAt(),
                repository.openIssuesCount(),
                repository.fork(),
                repository.defaultBranch(),
                repository.lastSyncedAt()
        );
    }

    private String accountLabel(String provider) {
        return switch (provider) {
            case "github" -> gitHubProvider.getAccountLabel();
            case "obsidian" -> obsidianProvider.getAccountLabel();
            case "slack" -> slackProvider.getAccountLabel();
            case "linear" -> linearProvider.getAccountLabel();
            default -> defaultAccountLabel(provider);
        };
    }

    private String accountValue(String provider, Map<String, Object> metadata) {
        return switch (provider) {
            case "github" -> gitHubProvider.getAccountValue(metadata);
            case "obsidian" -> obsidianProvider.getAccountValue(metadata);
            case "slack" -> slackProvider.getAccountValue(metadata);
            case "linear" -> linearProvider.getAccountValue(metadata);
            default -> null;
        };
    }

    private String eventsLabel(String provider) {
        return switch (provider) {
            case "github" -> gitHubProvider.getEventsLabel();
            case "obsidian" -> obsidianProvider.getEventsLabel();
            case "slack" -> slackProvider.getEventsLabel();
            case "linear" -> linearProvider.getEventsLabel();
            default -> defaultEventsLabel(provider);
        };
    }

    private String buildScopeSummary(String provider, Map<String, Object> metadata) {
        return switch (provider) {
            case "github" -> gitHubProvider.buildScopeSummary(metadata);
            case "obsidian" -> obsidianProvider.buildScopeSummary(metadata);
            case "slack" -> slackProvider.buildScopeSummary(metadata);
            case "linear" -> linearProvider.buildScopeSummary(metadata);
            default -> defaultScopeSummary(provider);
        };
    }

    private String defaultAccountLabel(String provider) {
        return switch (provider) {
            case "slack", "linear" -> "Workspace";
            case "obsidian" -> "Vault repo";
            default -> "Account";
        };
    }

    private String defaultEventsLabel(String provider) {
        return switch (provider) {
            case "github" -> "Repo activity";
            case "obsidian" -> "Vault activity";
            case "slack" -> "Channel activity";
            case "linear" -> "Team activity";
            default -> "Activity";
        };
    }

    private String disconnectedLabel(String provider) {
        return switch (provider) {
            case "github" -> "Install GitHub App";
            case "obsidian" -> "Requires GitHub connection";
            case "slack" -> "OAuth workspace install";
            case "linear" -> "OAuth workspace install";
            default -> "Not connected";
        };
    }

    private String defaultScopeSummary(String provider) {
        return switch (provider) {
            case "github" -> "Select repositories";
            case "obsidian" -> "Select a vault repo";
            case "slack" -> "Select channels";
            case "linear" -> "Select teams";
            default -> "Configure scope";
        };
    }

    private String createState(Long userId, String provider) {
        String state = java.util.UUID.randomUUID().toString();
        connectionStates.put(state, new ConnectionState(userId, provider, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)));
        cleanExpiredStates();
        return state;
    }

    private ConnectionState consumeState(String state, String provider) {
        if (state == null || state.isBlank()) {
            throw new RuntimeException("Invalid or expired integration state");
        }
        ConnectionState connectionState = connectionStates.remove(state);
        if (connectionState == null || connectionState.expiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new RuntimeException("Invalid or expired integration state");
        }
        if (!connectionState.provider().equals(provider)) {
            throw new RuntimeException("Provider mismatch in callback");
        }
        return connectionState;
    }

    private void cleanExpiredStates() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        connectionStates.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private record ConnectionState(Long userId, String provider, LocalDateTime expiresAt) {
    }

    public record SyncResult(int newEvents, int totalFetched) {
    }

    public record SlackWebhookResult(boolean challenge, String challengeValue) {
    }
}
