package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.ConnectApiKeyRequest;
import com.outreachly.outreachly.dto.IntegrationDto;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.entity.UserIntegration;
import com.outreachly.outreachly.service.GmailService;
import com.outreachly.outreachly.service.IntegrationService;
import com.outreachly.outreachly.service.IntegrationSyncScheduler;
import com.outreachly.outreachly.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@Slf4j
public class IntegrationController {

    private final IntegrationService integrationService;
    private final IntegrationSyncScheduler syncScheduler;
    private final GmailService gmailService;
    private final UserService userService;

    @Value("${integrations.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${github.integration.redirect-uri:http://localhost:8080/api/integrations/github/callback}")
    private String githubRedirectUri;

    @Value("${slack.redirect-uri:http://localhost:8080/api/integrations/slack/callback}")
    private String slackRedirectUri;

    @GetMapping
    public ResponseEntity<List<IntegrationDto>> getIntegrations(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<UserIntegration> integrations = integrationService.getIntegrations(user.getId());
        List<IntegrationDto> dtos = new ArrayList<>();

        dtos.add(buildGmailDto(user));

        Set<String> allProviders = Set.of("github", "obsidian", "slack", "linear");
        Map<String, UserIntegration> byProvider = new HashMap<>();
        for (UserIntegration i : integrations) {
            byProvider.put(i.getProvider(), i);
        }

        for (String provider : allProviders) {
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

        if ("gmail".equals(provider)) {
            String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/oauth2/authorization/google-gmail")
                    .toUriString();
            return ResponseEntity.ok(Map.of("url", url));
        }

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

        if ("gmail".equals(provider)) {
            gmailService.disconnectCurrentUser();
            return ResponseEntity.noContent().build();
        }

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
        String provider = integration.getProvider();
        List<Integer> sparkline = integrationService.getActivitySparkline(userId, provider);

        String accountLabel;
        String accountValue;
        String eventsLabel;

        switch (provider) {
            case "github":
                accountLabel = "Account";
                accountValue = "@" + (meta != null ? meta.getOrDefault("login", "unknown") : "unknown");
                eventsLabel = "Events synced";
                break;
            case "obsidian":
                accountLabel = "Vault repo";
                accountValue = meta != null ? (String) meta.getOrDefault("repoFullName", "unknown") : "unknown";
                eventsLabel = "Notes tracked";
                break;
            case "slack":
                accountLabel = "Workspace";
                accountValue = meta != null ? (String) meta.getOrDefault("team", "unknown") : "unknown";
                eventsLabel = "Messages synced";
                break;
            case "linear":
                accountLabel = "Account";
                accountValue = meta != null ? (String) meta.getOrDefault("name", "unknown") : "unknown";
                eventsLabel = "Issues tracked";
                break;
            default:
                accountLabel = "Account";
                accountValue = "unknown";
                eventsLabel = "Events";
        }

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
                provider,
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

    private IntegrationDto buildGmailDto(User user) {
        boolean connected = gmailService.hasGmailAccess();
        return new IntegrationDto(
                "gmail",
                connected ? "connected" : "disconnected",
                false,
                connected ? "Account" : null,
                connected ? user.getEmail() : null,
                null,
                null,
                connected ? "Connected" : null,
                Collections.emptyList(),
                0,
                false);
    }

    private String getRedirectUri(String provider) {
        return switch (provider) {
            case "github" -> githubRedirectUri;
            case "slack" -> slackRedirectUri;
            default -> throw new IllegalArgumentException("No redirect URI for: " + provider);
        };
    }

    private User getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.findByEmail(authentication.getName());
    }
}
