package com.pulse.pulse.service;

import com.pulse.pulse.entity.IntegrationEvent;
import com.pulse.pulse.entity.UserIntegration;
import com.pulse.pulse.repository.IntegrationEventRepository;
import com.pulse.pulse.repository.UserIntegrationRepository;
import com.pulse.pulse.service.integration.IntegrationProvider;
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
    private final IntegrationEventRepository eventRepo;
    private final Map<String, IntegrationProvider> providers;
    private final ConcurrentHashMap<String, OAuthState> oauthStates = new ConcurrentHashMap<>();

    public IntegrationService(UserIntegrationRepository integrationRepo,
                              IntegrationEventRepository eventRepo,
                              Map<String, IntegrationProvider> providers) {
        this.integrationRepo = integrationRepo;
        this.eventRepo = eventRepo;
        this.providers = providers;
    }

    public List<UserIntegration> getIntegrations(Long userId) {
        return integrationRepo.findByUserId(userId);
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

        List<IntegrationEvent> fetched = impl.fetchRecentEvents(integration, since);

        int newEvents = 0;
        for (IntegrationEvent event : fetched) {
            if (!eventRepo.existsByUserIdAndProviderAndExternalId(
                    userId, provider, event.getExternalId())) {
                eventRepo.save(event);
                newEvents++;
            }
        }

        integration.setLastSyncedAt(LocalDateTime.now());
        integrationRepo.save(integration);

        return new SyncResult(newEvents, fetched.size());
    }

    public List<Integer> getActivitySparkline(Long userId, String provider) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Object[]> dailyCounts = eventRepo.countByProviderPerDay(userId, provider, since);

        Map<String, Long> countMap = new HashMap<>();
        for (Object[] row : dailyCounts) {
            countMap.put(row[0].toString(), (Long) row[1]);
        }

        List<Integer> sparkline = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String date = LocalDateTime.now().minusDays(i).toLocalDate().toString();
            sparkline.add(countMap.getOrDefault(date, 0L).intValue());
        }

        return sparkline;
    }

    private IntegrationProvider getProvider(String provider) {
        IntegrationProvider impl = providers.get(provider);
        if (impl == null) {
            throw new IllegalArgumentException("Unknown provider: " + provider);
        }
        return impl;
    }

    private String getRedirectUri(String provider) {
        return switch (provider) {
            case "github" -> System.getProperty("github.integration.redirect-uri",
                    "http://localhost:8080/api/integrations/github/callback");
            case "slack" -> System.getProperty("slack.redirect-uri",
                    "http://localhost:8080/api/integrations/slack/callback");
            default -> throw new IllegalArgumentException("No redirect URI for: " + provider);
        };
    }

    private void cleanExpiredStates() {
        LocalDateTime now = LocalDateTime.now();
        oauthStates.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    private record OAuthState(Long userId, String provider, LocalDateTime expiresAt) {}

    public record SyncResult(int newEvents, int totalFetched) {}
}
