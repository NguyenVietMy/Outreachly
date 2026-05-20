package com.pulse.pulse.personal.api;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.integrations.application.IntegrationService;
import com.pulse.pulse.personal.application.ChatService;
import com.pulse.pulse.personal.application.ChatService.ChatMessage;
import com.pulse.pulse.personal.application.ChatService.ChatResponse;
import com.pulse.pulse.personal.application.KnowledgeIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personal/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final KnowledgeIndexingService knowledgeIndexingService;
    private final IntegrationService integrationService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ChatResponseDto> chat(
            @RequestBody ChatRequestDto request,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<ChatMessage> history = request.history() != null
                ? request.history().stream()
                    .map(h -> new ChatMessage(h.role(), h.content()))
                    .toList()
                : List.of();

        ChatResponse response = chatService.chat(user.id(), request.message(), history);

        return ResponseEntity.ok(new ChatResponseDto(
                response.message(),
                response.sources().stream()
                        .map(s -> new SourceCitationDto(s.sourceType(), s.sourceKey(), s.score()))
                        .toList(),
                response.routingDecisions().stream()
                        .map(r -> new RoutingDecisionDto(r.sourceType(), r.decision(), r.reason()))
                        .toList()
        ));
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, String>> reindex(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        knowledgeIndexingService.reindexAll(user.id());

        try {
            String obsidianDiffs = integrationService.getObsidianVaultDiffs(user.id());
            if (obsidianDiffs != null && !obsidianDiffs.isBlank()) {
                knowledgeIndexingService.indexObsidianDiff(user.id(),
                        java.time.LocalDate.now().toString(), obsidianDiffs);
            }
        } catch (Exception e) {
            log.warn("Failed to index Obsidian diffs during reindex: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("status", "completed"));
    }

    @GetMapping("/index-status")
    public ResponseEntity<Map<String, Long>> indexStatus(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(chatService.getIndexStatus(user.id()));
    }

    private CurrentUserView getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.getCurrentUser(authentication.getName());
    }

    record ChatRequestDto(String message, List<ChatMessageDto> history) {}
    record ChatMessageDto(String role, String content) {}
    record ChatResponseDto(String message, List<SourceCitationDto> sources, List<RoutingDecisionDto> routingDecisions) {}
    record SourceCitationDto(String sourceType, String sourceKey, double score) {}
    record RoutingDecisionDto(String sourceType, String decision, String reason) {}
}
