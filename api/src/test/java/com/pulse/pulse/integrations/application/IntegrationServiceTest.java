package com.pulse.pulse.integrations.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.activity.application.GitHubProjectSyncService;
import com.pulse.pulse.integrations.domain.UserIntegration;
import com.pulse.pulse.integrations.domain.WebhookDelivery;
import com.pulse.pulse.integrations.infrastructure.persistence.UserIntegrationRepository;
import com.pulse.pulse.integrations.infrastructure.persistence.WebhookDeliveryRepository;
import com.pulse.pulse.integrations.infrastructure.provider.GitHubIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.LinearIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.ObsidianIntegrationProvider;
import com.pulse.pulse.integrations.infrastructure.provider.SlackIntegrationProvider;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationServiceTest {

    private UserIntegrationRepository integrationRepository;
    private DashboardService dashboardService;
    private WebhookDeliveryRepository webhookDeliveryRepository;
    private GitHubIntegrationProvider gitHubProvider;
    private ObsidianIntegrationProvider obsidianProvider;
    private IntegrationService integrationService;
    private SimpleMeterRegistry meterRegistry;
    private TestObservationRegistry observationRegistry;

    @BeforeEach
    void setUp() {
        integrationRepository = Mockito.mock(UserIntegrationRepository.class);
        dashboardService = Mockito.mock(DashboardService.class);
        webhookDeliveryRepository = Mockito.mock(WebhookDeliveryRepository.class);
        gitHubProvider = Mockito.mock(GitHubIntegrationProvider.class);
        obsidianProvider = Mockito.mock(ObsidianIntegrationProvider.class);
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();

        integrationService = new IntegrationService(
                integrationRepository,
                dashboardService,
                Mockito.mock(GitHubProjectSyncService.class),
                webhookDeliveryRepository,
                gitHubProvider,
                Mockito.mock(SlackIntegrationProvider.class),
                Mockito.mock(LinearIntegrationProvider.class),
                obsidianProvider,
                new ObjectMapper(),
                new PulseObservability(observationRegistry, meterRegistry)
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

    @Test
    void githubWebhookSuccessRecordsProcessedMetricAndObservation() throws Exception {
        Map<String, String> headers = Map.of(
                "x-github-event", "push",
                "x-github-delivery", "delivery-1"
        );
        byte[] body = "{}".getBytes();
        JsonNode payload = new ObjectMapper().readTree("""
                {"repository":{"full_name":"owner/repo"},"installation":{"id":"123"}}
                """);
        UserIntegration integration = githubIntegration(7L);
        IntegrationEventPayload event = new IntegrationEventPayload(
                "commit",
                "Pushed code",
                "event-1",
                LocalDateTime.now(),
                Map.of("repo", "owner/repo")
        );

        when(gitHubProvider.verifyAndParseWebhook(headers, body)).thenReturn(payload);
        when(gitHubProvider.extractDeliveryId(headers)).thenReturn("delivery-1");
        when(gitHubProvider.extractRepositoryFullName(payload)).thenReturn("owner/repo");
        when(gitHubProvider.mapWebhookEvents(headers, payload)).thenReturn(List.of(event));
        when(webhookDeliveryRepository.findByProviderAndDeliveryId("github", "delivery-1")).thenReturn(Optional.empty());
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(integrationRepository.findByProviderAndStatus("github", "connected")).thenReturn(List.of(integration));
        when(integrationRepository.findByProviderAndStatus("obsidian", "connected")).thenReturn(List.of());
        when(integrationRepository.findByUserIdAndProvider(7L, "github")).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any(UserIntegration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dashboardService.ingestEvents(any())).thenReturn(1);

        integrationService.handleGitHubWebhook(headers, body);

        assertEquals(1.0, meterRegistry.get("pulse.webhook.deliveries")
                .tag("provider", "github")
                .tag("result", "processed")
                .counter()
                .count());
        verify(dashboardService).ingestEvents(any());
        assertThat(observationRegistry).hasObservationWithNameEqualTo("pulse.webhook.receive");
        assertThat(observationRegistry).hasObservationWithNameEqualTo("pulse.webhook.process");
    }

    @Test
    void githubWebhookDuplicateRecordsDuplicateMetricWithoutProcessing() throws Exception {
        Map<String, String> headers = Map.of(
                "x-github-event", "push",
                "x-github-delivery", "delivery-1"
        );
        byte[] body = "{}".getBytes();
        JsonNode payload = new ObjectMapper().readTree("""
                {"repository":{"full_name":"owner/repo"},"installation":{"id":"123"}}
                """);

        when(gitHubProvider.verifyAndParseWebhook(headers, body)).thenReturn(payload);
        when(gitHubProvider.extractDeliveryId(headers)).thenReturn("delivery-1");
        when(webhookDeliveryRepository.findByProviderAndDeliveryId("github", "delivery-1"))
                .thenReturn(Optional.of(WebhookDelivery.builder().provider("github").deliveryId("delivery-1").build()));

        integrationService.handleGitHubWebhook(headers, body);

        assertEquals(1.0, meterRegistry.get("pulse.webhook.deliveries")
                .tag("provider", "github")
                .tag("result", "duplicate")
                .counter()
                .count());
        verify(dashboardService, never()).ingestEvents(any());
    }

    @Test
    void githubWebhookFailureRecordsFailedMetric() throws Exception {
        Map<String, String> headers = Map.of(
                "x-github-event", "push",
                "x-github-delivery", "delivery-1"
        );
        byte[] body = "{}".getBytes();
        JsonNode payload = new ObjectMapper().readTree("""
                {"repository":{"full_name":"owner/repo"},"installation":{"id":"123"}}
                """);
        UserIntegration integration = githubIntegration(7L);
        IntegrationEventPayload event = new IntegrationEventPayload(
                "commit",
                "Pushed code",
                "event-1",
                LocalDateTime.now(),
                Map.of("repo", "owner/repo")
        );

        when(gitHubProvider.verifyAndParseWebhook(headers, body)).thenReturn(payload);
        when(gitHubProvider.extractDeliveryId(headers)).thenReturn("delivery-1");
        when(gitHubProvider.extractRepositoryFullName(payload)).thenReturn("owner/repo");
        when(gitHubProvider.mapWebhookEvents(headers, payload)).thenReturn(List.of(event));
        when(webhookDeliveryRepository.findByProviderAndDeliveryId("github", "delivery-1")).thenReturn(Optional.empty());
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(integrationRepository.findByProviderAndStatus("github", "connected")).thenReturn(List.of(integration));
        when(integrationRepository.findByProviderAndStatus("obsidian", "connected")).thenReturn(List.of());
        when(dashboardService.ingestEvents(any())).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> integrationService.handleGitHubWebhook(headers, body));

        assertEquals(1.0, meterRegistry.get("pulse.webhook.deliveries")
                .tag("provider", "github")
                .tag("result", "failed")
                .counter()
                .count());
        verify(webhookDeliveryRepository, Mockito.times(2)).save(any(WebhookDelivery.class));
    }

    private UserIntegration githubIntegration(Long userId) {
        return UserIntegration.builder()
                .userId(userId)
                .provider("github")
                .status("connected")
                .metadata(Map.of(
                        "installationId", "123",
                        "selectedResources", List.of(Map.of("id", "owner/repo", "name", "owner/repo", "type", "repository"))
                ))
                .build();
    }
}
