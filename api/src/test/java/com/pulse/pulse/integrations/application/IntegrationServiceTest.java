package com.pulse.pulse.integrations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.activity.application.GitHubProjectSyncService;
import com.pulse.pulse.integrations.domain.UserIntegration;
import com.pulse.pulse.integrations.infrastructure.persistence.UserIntegrationRepository;
import com.pulse.pulse.integrations.infrastructure.provider.IntegrationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class IntegrationServiceTest {

    private UserIntegrationRepository integrationRepository;
    private DashboardService dashboardService;
    private GitHubProjectSyncService gitHubProjectSyncService;
    private IntegrationService integrationService;

    @BeforeEach
    void setUp() {
        integrationRepository = Mockito.mock(UserIntegrationRepository.class);
        dashboardService = Mockito.mock(DashboardService.class);
        gitHubProjectSyncService = Mockito.mock(GitHubProjectSyncService.class);
        integrationService = new IntegrationService(
                integrationRepository,
                dashboardService,
                gitHubProjectSyncService,
                Map.of("github", new StubProvider("github"), "obsidian", new StubProvider("obsidian")));
    }

    @Test
    void connectObsidianRequiresConnectedGithub() {
        when(integrationRepository.findByUserIdAndProvider(42L, "github")).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> integrationService.connectObsidian(42L, "owner/repo"));

        assertEquals("GitHub must be connected first", error.getMessage());
    }

    @Test
    void connectObsidianNormalizesRepoNameAndCopiesGithubToken() {
        UserIntegration github = UserIntegration.builder()
                .userId(42L)
                .provider("github")
                .accessToken("gh-token")
                .status("connected")
                .build();

        when(integrationRepository.findByUserIdAndProvider(42L, "github")).thenReturn(Optional.of(github));
        when(integrationRepository.findByUserIdAndProvider(42L, "obsidian")).thenReturn(Optional.empty());
        when(integrationRepository.save(any(UserIntegration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserIntegration saved = integrationService.connectObsidian(42L, "https://github.com/acme/pulse-notes.git/");

        assertEquals("obsidian", saved.getProvider());
        assertEquals("gh-token", saved.getAccessToken());
        assertEquals("acme/pulse-notes", saved.getMetadata().get("repoFullName"));
    }

    private record StubProvider(String providerName) implements IntegrationProvider {
        @Override
        public boolean requiresOAuth() {
            return false;
        }

        @Override
        public String buildOAuthUrl(String state, String redirectUri) {
            return "";
        }

        @Override
        public String exchangeCode(String code, String redirectUri) {
            return "";
        }

        @Override
        public List<IntegrationEventPayload> fetchRecentEvents(UserIntegration integration, LocalDateTime since) {
            return List.of();
        }

        @Override
        public Map<String, Object> fetchMetadata(String accessToken) {
            return Map.of();
        }

        @Override
        public String getAccountLabel() {
            return "Account";
        }

        @Override
        public String getAccountValue(Map<String, Object> metadata) {
            return "value";
        }

        @Override
        public String getEventsLabel() {
            return "Events";
        }

        @Override
        public String getProviderName() {
            return providerName;
        }
    }
}
