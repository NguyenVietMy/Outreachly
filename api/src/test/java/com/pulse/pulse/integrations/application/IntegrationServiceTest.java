package com.pulse.pulse.integrations.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.activity.application.GitHubProjectSyncService;
import com.pulse.pulse.integrations.domain.UserIntegration;
import com.pulse.pulse.integrations.infrastructure.persistence.UserIntegrationRepository;
import com.pulse.pulse.integrations.infrastructure.persistence.WebhookDeliveryRepository;
import com.pulse.pulse.integrations.infrastructure.provider.GitHubIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.LinearIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.ObsidianIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.SlackIntegrationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class IntegrationServiceTest {

    private UserIntegrationRepository integrationRepository;
    private IntegrationService integrationService;

    @BeforeEach
    void setUp() {
        integrationRepository = Mockito.mock(UserIntegrationRepository.class);

        integrationService = new IntegrationService(
                integrationRepository,
                Mockito.mock(DashboardService.class),
                Mockito.mock(GitHubProjectSyncService.class),
                Mockito.mock(WebhookDeliveryRepository.class),
                Mockito.mock(GitHubIntegrationProvider.class),
                Mockito.mock(SlackIntegrationProvider.class),
                Mockito.mock(LinearIntegrationProvider.class),
                Mockito.mock(ObsidianIntegrationProvider.class),
                new ObjectMapper()
        );
    }

    @Test
    void connectObsidianRequiresConnectedGithub() {
        when(integrationRepository.findByUserIdAndProvider(42L, "github")).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> integrationService.connectObsidian(42L));

        assertEquals("GitHub must be connected first", error.getMessage());
    }

    @Test
    void connectObsidianCreatesPendingScopedIntegration() {
        UserIntegration github = UserIntegration.builder()
                .userId(42L)
                .provider("github")
                .status("connected")
                .build();

        when(integrationRepository.findByUserIdAndProvider(42L, "github")).thenReturn(Optional.of(github));
        when(integrationRepository.findByUserIdAndProvider(42L, "obsidian")).thenReturn(Optional.empty());
        when(integrationRepository.save(any(UserIntegration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserIntegration saved = integrationService.connectObsidian(42L);

        assertEquals("obsidian", saved.getProvider());
        assertEquals("pending_scope", saved.getWebhookStatus());
        assertEquals("connected", saved.getStatus());
    }
}
