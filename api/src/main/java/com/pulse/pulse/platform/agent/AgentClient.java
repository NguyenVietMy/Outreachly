package com.pulse.pulse.platform.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * HTTP client for the Python agent service. The wire contract is camelCase on both sides, so these
 * records map 1:1 onto the agent's Pydantic models in {@code agent/src/agent/models.py}.
 */
@Component
@Slf4j
public class AgentClient {

    private static final AgentChatResponse DEGRADED = new AgentChatResponse(
            "The assistant is temporarily unavailable. Please try again in a moment.",
            List.of(), List.of());

    private final WebClient webClient;
    private final Duration timeout;

    public AgentClient(WebClient.Builder webClientBuilder,
                       @Value("${pulse.agent.url}") String baseUrl,
                       @Value("${pulse.internal.token}") String internalToken,
                       @Value("${pulse.agent.timeout-seconds}") long timeoutSeconds) {
        // The auto-configured builder carries client observations, which is what injects the
        // traceparent header the agent continues the trace from — do not set it by hand.
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Returns a degraded response rather than throwing when the agent is unreachable, times out or
     * answers with an error — chat degrades, it does not 500.
     */
    public AgentChatResponse chat(Long userId, String message, List<Message> history) {
        try {
            AgentChatResponse response = webClient.post()
                    .uri("/chat")
                    .bodyValue(new AgentChatRequest(userId, message, history))
                    .retrieve()
                    .bodyToMono(AgentChatResponse.class)
                    .timeout(timeout)
                    .block();
            return response != null ? response : DEGRADED;
        } catch (RuntimeException e) {
            log.error("Agent chat call failed for user {}: {}", userId, e.getMessage());
            return DEGRADED;
        }
    }

    public record Message(String role, String content) {}
    public record Citation(String sourceType, String sourceKey, double score) {}
    public record TrajectoryStep(String sourceType, String decision, String reason) {}
    public record AgentChatResponse(String message, List<Citation> sources,
                                    List<TrajectoryStep> trajectory) {}

    record AgentChatRequest(Long userId, String message, List<Message> history) {}
}
