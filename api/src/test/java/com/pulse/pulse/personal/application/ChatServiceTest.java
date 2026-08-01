package com.pulse.pulse.personal.application;

import com.pulse.pulse.personal.infrastructure.persistence.KnowledgeChunkRepository;
import com.pulse.pulse.platform.agent.AgentClient;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private AgentClient agentClient;
    private TestObservationRegistry observationRegistry;
    private ChatService service;

    @BeforeEach
    void setUp() {
        agentClient = Mockito.mock(AgentClient.class);
        observationRegistry = TestObservationRegistry.create();
        service = new ChatService(
                Mockito.mock(KnowledgeChunkRepository.class),
                agentClient,
                new PulseObservability(observationRegistry, new SimpleMeterRegistry()));
    }

    @Test
    void mapsAgentResponseOntoTheFrontendContract() {
        when(agentClient.chat(eq(1L), eq("how am I doing?"), any())).thenReturn(
                new AgentClient.AgentChatResponse(
                        "You are on track.",
                        List.of(new AgentClient.Citation("github_readme", "user/pulse", 0.031)),
                        List.of(new AgentClient.TrajectoryStep("plan", "retrieve", "project question"))));

        ChatService.ChatResponse response = service.chat(1L, "how am I doing?",
                List.of(new ChatService.ChatMessage("user", "hi")));

        assertEquals("You are on track.", response.message());
        assertEquals(List.of(new ChatService.SourceCitation("github_readme", "user/pulse", 0.031)),
                response.sources());
        assertEquals(List.of(new ChatService.RoutingDecision("plan", "retrieve", "project question")),
                response.routingDecisions());
    }

    @Test
    void forwardsHistoryToTheAgent() {
        when(agentClient.chat(any(), any(), any())).thenReturn(
                new AgentClient.AgentChatResponse("ok", List.of(), List.of()));

        service.chat(1L, "and now?", List.of(
                new ChatService.ChatMessage("user", "hi"),
                new ChatService.ChatMessage("assistant", "hello")));

        verify(agentClient).chat(1L, "and now?", List.of(
                new AgentClient.Message("user", "hi"),
                new AgentClient.Message("assistant", "hello")));
    }

    @Test
    void emitsTheRagChatObservation() {
        when(agentClient.chat(any(), any(), any())).thenReturn(
                new AgentClient.AgentChatResponse("ok", List.of(), List.of()));

        service.chat(1L, "hello", List.of());

        assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("pulse.rag.chat")
                .that()
                .hasHighCardinalityKeyValue("user.id", "1")
                .hasHighCardinalityKeyValue("message.length", "5")
                .hasLowCardinalityKeyValue("result", "success");
    }
}
