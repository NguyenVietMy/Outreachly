package com.pulse.pulse.integrations.application;

import com.pulse.pulse.activity.application.ActivityIngestCommand;
import com.pulse.pulse.activity.application.ActivityIngestItem;
import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.activity.application.GitHubProjectSyncService;
import com.pulse.pulse.activity.application.GitHubProjectSyncRequest;
import com.pulse.pulse.activity.application.RepositorySnapshotView;
import com.pulse.pulse.integrations.domain.UserIntegration;
import com.pulse.pulse.integrations.infrastructure.persistence.UserIntegrationRepository;
import com.pulse.pulse.integrations.infrastructure.provider.IntegrationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IntegrationService {

    private final UserIntegrationRepository integrationRepo;
    private final DashboardService dashboardService;
    private final GitHubProjectSyncService gitHubProjectSyncService;
    private final Map<String, IntegrationProvider> providers;
    private final ConcurrentHashMap<String, OAuthState> oauthStates = new ConcurrentHashMap<>();

    public IntegrationService(UserIntegrationRepository integrationRepo,
                              DashboardService dashboardService,
                              GitHubProjectSyncService gitHubProjectSyncService,
                              Map<String, IntegrationProvider> providers) {
        this.integrationRepo = integrationRepo;
        this.dashboardService = dashboardService;
        this.gitHubProjectSyncService = gitHubProjectSyncService;
        this.providers = providers;
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
        for (String providerName : providers.keySet()) {
            UserIntegration integration = byProvider.get(providerName);
            if (integration != null && "connected".equals(integration.getStatus())) {
                views.add(toView(integration, userId));
            } else {
                views.add(new IntegrationView(
                        userId,
                        providerName,
                        "disconnected",
                        null,
                        Map.of(),
                        null,
                        0,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
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

    public String getOAuthUrl(Long userId, String provider, String redirectUri) {
        IntegrationProvider impl = getProvider(provider);
        if (!impl.requiresOAuth()) {
            throw new IllegalArgumentException(provider + " does not use OAuth");
        }

        String state = UUID.randomUUID().toString();
        oauthStates.put(state, new OAuthState(userId, provider, LocalDateTime.now().plusMinutes(10)));
        cleanExpiredStates();

        return impl.buildOAuthUrl(state, redirectUri);
    }

    public String getOAuthUrl(Long userId, String provider) {
        return getOAuthUrl(userId, provider, getRedirectUri(provider));
    }

    @Transactional
    public UserIntegration handleOAuthCallback(String provider, String code, String state) {
        OAuthState oauthState = oauthStates.remove(state);
        if (oauthState == null || oauthState.expiresAt.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invalid or expired OAuth state");
        }

        if (!oauthState.provider.equals(provider)) {
            throw new RuntimeException("Provider mismatch in OAuth callback");
        }

        IntegrationProvider impl = getProvider(provider);
        String redirectUri = getRedirectUri(provider);
        String accessToken = impl.exchangeCode(code, redirectUri);
        Map<String, Object> metadata = impl.fetchMetadata(accessToken);

        UserIntegration integration = integrationRepo
                .findByUserIdAndProvider(oauthState.userId, provider)
                .orElse(UserIntegration.builder()
                        .userId(oauthState.userId)
                        .provider(provider)
                        .build());

        integration.setAccessToken(accessToken);
        integration.setStatus("connected");
        integration.setMetadata(metadata);

        return integrationRepo.save(integration);
    }

    @Transactional
    public UserIntegration connectWithApiKey(Long userId, String provider,
                                              String apiKey, Map<String, Object> extraMetadata) {
        IntegrationProvider impl = getProvider(provider);
        Map<String, Object> metadata = impl.fetchMetadata(apiKey);

        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        UserIntegration integration = integrationRepo
                .findByUserIdAndProvider(userId, provider)
                .orElse(UserIntegration.builder()
                        .userId(userId)
                        .provider(provider)
                        .build());

        integration.setAccessToken(apiKey);
        integration.setStatus("connected");
        integration.setMetadata(metadata);

        return integrationRepo.save(integration);
    }

    @Transactional
    public UserIntegration connectObsidian(Long userId, String repoFullName) {
        UserIntegration github = integrationRepo.findByUserIdAndProvider(userId, "github")
                .orElseThrow(() -> new RuntimeException("GitHub must be connected first"));

        if (!"connected".equals(github.getStatus())) {
            throw new RuntimeException("GitHub integration is not active");
        }

        String normalized = normalizeRepoName(repoFullName);

        UserIntegration integration = integrationRepo
                .findByUserIdAndProvider(userId, "obsidian")
                .orElse(UserIntegration.builder()
                        .userId(userId)
                        .provider("obsidian")
                        .build());

        integration.setAccessToken(github.getAccessToken());
        integration.setStatus("connected");
        integration.setMetadata(Map.of("repoFullName", normalized));

        return integrationRepo.save(integration);
    }

    private String normalizeRepoName(String input) {
        String cleaned = input.trim()
                .replaceFirst("^https?://github\\.com/", "")
                .replaceFirst("\\.git$", "")
                .replaceFirst("/$", "");
        String[] parts = cleaned.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid repo format. Use owner/repo or a GitHub URL.");
        }
        return parts[0] + "/" + parts[1];
    }

    @Transactional
    public void disconnect(Long userId, String provider) {
        integrationRepo.deleteByUserIdAndProvider(userId, provider);
    }

    @Transactional
    public SyncResult sync(Long userId, String provider) {
        UserIntegration integration = integrationRepo
                .findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + provider));

        if (!"connected".equals(integration.getStatus())) {
            throw new RuntimeException("Integration is not connected");
        }

        IntegrationProvider impl = getProvider(provider);
        LocalDateTime since = integration.getLastSyncedAt() != null
                ? integration.getLastSyncedAt()
                : LocalDateTime.now().minusDays(7);

        List<IntegrationEventPayload> fetched = impl.fetchRecentEvents(integration, since);

        int newEvents = dashboardService.ingestEvents(new ActivityIngestCommand(
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

        integration.setLastSyncedAt(LocalDateTime.now());
        integrationRepo.save(integration);

        return new SyncResult(newEvents, fetched.size());
    }

    @Transactional
    public void markProjectSyncRun(Long userId, String provider) {
        UserIntegration integration = integrationRepo.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + provider));

        Map<String, Object> metadata = integration.getMetadata() == null ? new HashMap<>() : new HashMap<>(integration.getMetadata());
        metadata.put("projectSyncLastRun", LocalDateTime.now().toString());
        integration.setMetadata(metadata);
        integrationRepo.save(integration);
    }

    public int syncGitHubProjects(Long userId) {
        GitHubProjectSyncRequest request = getGitHubProjectSyncRequest(userId);
        int count = gitHubProjectSyncService.syncProjects(request);
        markProjectSyncRun(userId, "github");
        return count;
    }

    public GitHubProjectSyncRequest getGitHubProjectSyncRequest(Long userId) {
        UserIntegration github = integrationRepo.findByUserIdAndProvider(userId, "github")
                .orElseThrow(() -> new RuntimeException("GitHub not connected"));
        return toGitHubProjectSyncRequest(github);
    }

    public List<GitHubRepositoryView> getGitHubRepositoryViews(Long userId) {
        return dashboardService.getGitHubRepositories(userId).stream()
                .map(this::toGitHubRepositoryView)
                .toList();
    }

    private IntegrationView toView(UserIntegration integration, Long userId) {
        Map<String, Object> meta = integration.getMetadata() != null ? integration.getMetadata() : Map.of();
        String providerName = integration.getProvider();
        List<Integer> sparkline = dashboardService.getActivitySparkline(userId, providerName);

        IntegrationProvider provider = providers.get(providerName);
        String accountLabel = provider != null ? provider.getAccountLabel() : "Account";
        String accountValue = provider != null ? provider.getAccountValue(meta) : "unknown";
        String eventsLabel = provider != null ? provider.getEventsLabel() : "Events";

        int totalEvents = sparkline.stream().mapToInt(Integer::intValue).sum();
        String eventsValue = totalEvents + " this week";
        String lastSyncedLabel = buildLastSyncedLabel(integration.getLastSyncedAt());

        return new IntegrationView(
                integration.getUserId(),
                providerName,
                integration.getStatus(),
                integration.getAccessToken(),
                meta,
                integration.getLastSyncedAt(),
                integration.getConsecutiveFailures(),
                integration.getAutoSyncEnabled(),
                accountLabel,
                accountValue,
                eventsLabel,
                eventsValue,
                lastSyncedLabel,
                sparkline
        );
    }

    private String buildLastSyncedLabel(LocalDateTime lastSyncedAt) {
        if (lastSyncedAt == null) {
            return "Never synced";
        }

        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(lastSyncedAt, LocalDateTime.now());
        if (minutes < 1) return "Just now";
        if (minutes < 60) return "Last synced " + minutes + " min ago";
        if (minutes < 1440) return "Last synced " + (minutes / 60) + " hr ago";
        return "Last synced " + (minutes / 1440) + " days ago";
    }

    private GitHubProjectSyncRequest toGitHubProjectSyncRequest(UserIntegration integration) {
        return new GitHubProjectSyncRequest(
                integration.getUserId(),
                integration.getAccessToken(),
                integration.getMetadata()
        );
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

    private IntegrationProvider getProvider(String provider) {
        IntegrationProvider impl = providers.get(provider);
        if (impl == null) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }
        return impl;
    }

    private String getRedirectUri(String providerName) {
        IntegrationProvider impl = getProvider(providerName);
        String uri = impl.getRedirectUri();
        if (uri == null) {
            throw new IllegalArgumentException("No redirect URI for: " + providerName);
        }
        return uri;
    }

    private void cleanExpiredStates() {
        LocalDateTime now = LocalDateTime.now();
        oauthStates.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    private record OAuthState(Long userId, String provider, LocalDateTime expiresAt) {}

    public record SyncResult(int newEvents, int totalFetched) {}
}
