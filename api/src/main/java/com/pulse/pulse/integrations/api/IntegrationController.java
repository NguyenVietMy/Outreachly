package com.pulse.pulse.integrations.api;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.integrations.api.dto.ConnectApiKeyRequest;
import com.pulse.pulse.integrations.api.dto.IntegrationDto;
import com.pulse.pulse.integrations.application.GitHubRepositoryView;
import com.pulse.pulse.integrations.application.IntegrationService;
import com.pulse.pulse.integrations.application.IntegrationSyncScheduler;
import com.pulse.pulse.integrations.application.IntegrationView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
@Slf4j
public class IntegrationController {

    private final IntegrationService integrationService;
    private final IntegrationSyncScheduler syncScheduler;
    private final UserService userService;

    @Value("${integrations.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public IntegrationController(IntegrationService integrationService,
                                 IntegrationSyncScheduler syncScheduler,
                                 UserService userService) {
        this.integrationService = integrationService;
        this.syncScheduler = syncScheduler;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<IntegrationDto>> getIntegrations(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<IntegrationDto> dtos = integrationService.getIntegrationViews(user.id()).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{provider}/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl(
            @PathVariable String provider,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String url = integrationService.getOAuthUrl(user.id(), provider);
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
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            if ("obsidian".equals(provider)) {
                String repoFullName = request.metadata() != null
                        ? (String) request.metadata().get("repoFullName")
                        : null;
                if (repoFullName == null || repoFullName.isBlank()) {
                    return ResponseEntity.badRequest().build();
                }
                integrationService.connectObsidian(user.id(), repoFullName);
            } else {
                if (request.apiKey() == null || request.apiKey().isBlank()) {
                    return ResponseEntity.badRequest().build();
                }
                integrationService.connectWithApiKey(
                        user.id(), provider, request.apiKey(), request.metadata());
            }
            return ResponseEntity.ok(toDto(integrationService.getIntegrationView(user.id(), provider)));
        } catch (Exception e) {
            log.error("Connect error for {}: {}", provider, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> disconnect(
            @PathVariable String provider,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        integrationService.disconnect(user.id(), provider);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{provider}/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @PathVariable String provider,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            IntegrationService.SyncResult result = integrationService.sync(user.id(), provider);
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
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(syncScheduler.getStats());
    }

    @PostMapping("/sync/trigger")
    public ResponseEntity<Map<String, String>> triggerAutoSync(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();
        syncScheduler.triggerManualSync();
        return ResponseEntity.ok(Map.of("status", "triggered"));
    }

    @PostMapping("/{provider}/reset-backoff")
    public ResponseEntity<Map<String, String>> resetBackoff(
            @PathVariable String provider,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();
        syncScheduler.resetBackoff(user.id(), provider);
        return ResponseEntity.ok(Map.of("status", "reset"));
    }

    @GetMapping("/github/repositories")
    public ResponseEntity<List<Map<String, Object>>> getGitHubRepositories(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<Map<String, Object>> result = integrationService.getGitHubRepositoryViews(user.id()).stream()
                .map(this::toRepositoryMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/github/sync-projects")
    public ResponseEntity<Map<String, Object>> syncGitHubProjects(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            int count = integrationService.syncGitHubProjects(user.id());
            return ResponseEntity.ok(Map.of("reposSynced", count));
        } catch (Exception e) {
            log.error("GitHub project sync error: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private IntegrationDto toDto(IntegrationView integration) {
        return new IntegrationDto(
                integration.provider(),
                integration.status(),
                true,
                integration.accountLabel(),
                integration.accountValue(),
                integration.eventsLabel(),
                integration.eventsValue(),
                integration.lastSyncedLabel(),
                integration.sparkline(),
                integration.consecutiveFailures() != null ? integration.consecutiveFailures() : 0,
                integration.autoSyncEnabled() != null && integration.autoSyncEnabled()
        );
    }

    private Map<String, Object> toRepositoryMap(GitHubRepositoryView repo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("repoFullName", repo.repoFullName());
        map.put("repoName", repo.repoName());
        map.put("description", repo.description());
        map.put("primaryLanguage", repo.primaryLanguage());
        map.put("topics", repo.topics());
        map.put("languages", repo.languages());
        map.put("pushedAt", repo.pushedAt() != null ? repo.pushedAt().toString() : null);
        map.put("openIssuesCount", repo.openIssuesCount());
        map.put("isFork", repo.fork());
        map.put("defaultBranch", repo.defaultBranch());
        map.put("lastSyncedAt", repo.lastSyncedAt() != null ? repo.lastSyncedAt().toString() : null);
        return map;
    }

    private CurrentUserView getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.getCurrentUser(authentication.getName());
    }
}
