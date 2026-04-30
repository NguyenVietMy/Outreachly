package com.pulse.pulse.controller;

import com.pulse.pulse.dto.ConnectApiKeyRequest;
import com.pulse.pulse.dto.IntegrationDto;
import com.pulse.pulse.entity.User;
import com.pulse.pulse.entity.UserIntegration;
import com.pulse.pulse.service.IntegrationService;
import com.pulse.pulse.service.IntegrationSyncScheduler;
import com.pulse.pulse.service.UserService;
import com.pulse.pulse.service.integration.IntegrationProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/integrations")
@Slf4j
public class IntegrationController {

    private final IntegrationService integrationService;
    private final IntegrationSyncScheduler syncScheduler;
    private final UserService userService;
    private final Map<String, IntegrationProvider> providers;

    @Value("${integrations.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public IntegrationController(IntegrationService integrationService,
                                  IntegrationSyncScheduler syncScheduler,
                                  UserService userService,
                                  Map<String, IntegrationProvider> providers) {
        this.integrationService = integrationService;
        this.syncScheduler = syncScheduler;
        this.userService = userService;
        this.providers = providers;
    }

    @GetMapping
    public ResponseEntity<List<IntegrationDto>> getIntegrations(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<UserIntegration> integrations = integrationService.getIntegrations(user.getId());
        List<IntegrationDto> dtos = new ArrayList<>();

        Map<String, UserIntegration> byProvider = new HashMap<>();
        for (UserIntegration i : integrations) {
            byProvider.put(i.getProvider(), i);
        }

        for (String provider : providers.keySet()) {
            UserIntegration integration = byProvider.get(provider);
            if (integration != null && "connected".equals(integration.getStatus())) {
                dtos.add(buildConnectedDto(integration, user.getId()));
            } else {
                dtos.add(buildDisconnectedDto(provider));
            }
        }

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{provider}/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl(
            @PathVariable String provider,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String redirectUri = getRedirectUri(provider);
        String url = integrationService.getOAuthUrl(user.getId(), provider, redirectUri);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/{provider}/callback")
    public void handleOAuthCallback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        try {
            integrationService.handleOAuthCallback(provider, code, state);
            response.sendRedirect(frontendUrl + "/integrations?connected=" + provider);
        } catch (Exception e) {
            log.error("OAuth callback error for {}: {}", provider, e.getMessage());
            response.sendRedirect(frontendUrl + "/integrations?error=" + provider);
        }
    }

    @PostMapping("/{provider}/connect")
    public ResponseEntity<IntegrationDto> connect(
            @PathVariable String provider,
            @RequestBody ConnectApiKeyRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            UserIntegration integration;
            if ("obsidian".equals(provider)) {
                String repoFullName = request.metadata() != null
                        ? (String) request.metadata().get("repoFullName")
                        : null;
                if (repoFullName == null || repoFullName.isBlank()) {
                    return ResponseEntity.badRequest().build();
                }
                integration = integrationService.connectObsidian(user.getId(), repoFullName);
            } else {
                if (request.apiKey() == null || request.apiKey().isBlank()) {
                    return ResponseEntity.badRequest().build();
                }
                integration = integrationService.connectWithApiKey(
                        user.getId(), provider, request.apiKey(), request.metadata());
            }
            return ResponseEntity.ok(buildConnectedDto(integration, user.getId()));
        } catch (Exception e) {
            log.error("Connect error for {}: {}", provider, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> disconnect(
            @PathVariable String provider,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        integrationService.disconnect(user.getId(), provider);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{provider}/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @PathVariable String provider,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            IntegrationService.SyncResult result = integrationService.sync(user.getId(), provider);
            return ResponseEntity.ok(Map.of(
                    "newEvents", result.newEvents(),
                    "totalFetched", result.totalFetched()
            ));
        } catch (Exception e) {
            log.error("Sync error for {}: {}", provider, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sync/stats")
    public ResponseEntity<IntegrationSyncScheduler.SyncSchedulerStats> getSyncStats(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(syncScheduler.getStats());
    }

    @PostMapping("/sync/trigger")
    public ResponseEntity<Map<String, String>> triggerAutoSync(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();
        syncScheduler.triggerManualSync();
        return ResponseEntity.ok(Map.of("status", "triggered"));
    }

    @PostMapping("/{provider}/reset-backoff")
    public ResponseEntity<Map<String, String>> resetBackoff(
            @PathVariable String provider,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();
        syncScheduler.resetBackoff(user.getId(), provider);
        return ResponseEntity.ok(Map.of("status", "reset"));
    }

    private IntegrationDto buildConnectedDto(UserIntegration integration, Long userId) {
        Map<String, Object> meta = integration.getMetadata();
        String providerName = integration.getProvider();
        List<Integer> sparkline = integrationService.getActivitySparkline(userId, providerName);

        IntegrationProvider provider = providers.get(providerName);
        String accountLabel = provider != null ? provider.getAccountLabel() : "Account";
        String accountValue = provider != null ? provider.getAccountValue(meta) : "unknown";
        String eventsLabel = provider != null ? provider.getEventsLabel() : "Events";

        int totalEvents = sparkline.stream().mapToInt(Integer::intValue).sum();
        String eventsValue = totalEvents + " this week";

        String lastSynced = "Never synced";
        if (integration.getLastSyncedAt() != null) {
            long minutes = ChronoUnit.MINUTES.between(integration.getLastSyncedAt(), LocalDateTime.now());
            if (minutes < 1) lastSynced = "Just now";
            else if (minutes < 60) lastSynced = "Last synced " + minutes + " min ago";
            else if (minutes < 1440) lastSynced = "Last synced " + (minutes / 60) + " hr ago";
            else lastSynced = "Last synced " + (minutes / 1440) + " days ago";
        }

        return new IntegrationDto(
                providerName,
                "connected",
                true,
                accountLabel,
                accountValue,
                eventsLabel,
                eventsValue,
                lastSynced,
                sparkline,
                integration.getConsecutiveFailures() != null ? integration.getConsecutiveFailures() : 0,
                integration.getAutoSyncEnabled() != null && integration.getAutoSyncEnabled()
        );
    }

    private IntegrationDto buildDisconnectedDto(String provider) {
        return new IntegrationDto(
                provider,
                "disconnected",
                true,
                null, null, null, null, null,
                Collections.emptyList(),
                0,
                false
        );
    }

    private String getRedirectUri(String providerName) {
        IntegrationProvider provider = providers.get(providerName);
        if (provider == null || provider.getRedirectUri() == null) {
            throw new IllegalArgumentException("No redirect URI for: " + providerName);
        }
        return provider.getRedirectUri();
    }

    private User getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.findByEmail(authentication.getName());
    }
}
