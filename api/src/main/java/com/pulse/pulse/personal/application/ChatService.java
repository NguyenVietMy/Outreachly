package com.pulse.pulse.personal.application;

import com.pulse.pulse.personal.infrastructure.persistence.KnowledgeChunkRepository;
import com.pulse.pulse.platform.agent.AgentClient;
import com.pulse.pulse.platform.observability.PulseObservability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final KnowledgeChunkRepository chunkRepo;
    private final AgentClient agentClient;
    private final PulseObservability observability;

    public ChatResponse chat(Long userId, String message, List<ChatMessage> history) {
        return observability.observe("pulse.rag.chat", obs -> {
            observability.high(obs, "user.id", userId);
            observability.high(obs, "message.length", message.length());
            observability.high(obs, "history.size", history != null ? history.size() : 0);
        }, () -> {
            List<AgentClient.Message> agentHistory = history != null
                    ? history.stream().map(m -> new AgentClient.Message(m.role(), m.content())).toList()
                    : List.<AgentClient.Message>of();

            AgentClient.AgentChatResponse response = agentClient.chat(userId, message, agentHistory);

            return new ChatResponse(
                    response.message(),
                    response.sources().stream()
                            .map(s -> new SourceCitation(s.sourceType(), s.sourceKey(), s.score()))
                            .toList(),
                    // The agent's trajectory is what the frontend renders as routing decisions.
                    response.trajectory().stream()
                            .map(t -> new RoutingDecision(t.sourceType(), t.decision(), t.reason()))
                            .toList());
        });
    }

    public Map<String, Long> getIndexStatus(Long userId) {
        List<Object[]> counts = chunkRepo.countGroupedBySourceType(userId);
        Map<String, Long> status = new LinkedHashMap<>();
        for (Object[] row : counts) {
            status.put((String) row[0], ((Number) row[1]).longValue());
        }
        return status;
    }

    public record ChatMessage(String role, String content) {}
    public record SourceCitation(String sourceType, String sourceKey, double score) {}
    public record RoutingDecision(String sourceType, String decision, String reason) {}
    public record ChatResponse(String message, List<SourceCitation> sources, List<RoutingDecision> routingDecisions) {}
}
