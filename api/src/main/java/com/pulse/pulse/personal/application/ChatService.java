package com.pulse.pulse.personal.application;

import com.pulse.pulse.activity.domain.GitHubRepository;
import com.pulse.pulse.activity.domain.GitHubRepositoryReader;
import com.pulse.pulse.personal.domain.AiTask;
import com.pulse.pulse.personal.domain.RoadmapItem;
import com.pulse.pulse.personal.domain.UserGoal;
import com.pulse.pulse.personal.domain.UserProfile;
import com.pulse.pulse.personal.infrastructure.persistence.*;
import com.pulse.pulse.platform.ai.AnthropicService;
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

    private static final int RESUME_INLINE_THRESHOLD = 6000;
    private static final int GITHUB_INLINE_THRESHOLD = 3;
    private static final int OBSIDIAN_INLINE_THRESHOLD = 5000;
    private static final int ITEMS_INLINE_THRESHOLD = 20;
    private static final int MAX_HISTORY_TURNS = 10;
    private static final int RAG_TOP_K = 5;

    private final UserProfileRepository profileRepo;
    private final UserGoalRepository goalRepo;
    private final AiTaskRepository aiTaskRepo;
    private final RoadmapItemRepository roadmapRepo;
    private final GitHubRepositoryReader githubRepo;
    private final KnowledgeChunkRepository chunkRepo;
    private final HybridRetrievalService retrievalService;
    private final AnthropicService anthropicService;
    private final PulseObservability observability;

    public ChatResponse chat(Long userId, String message, List<ChatMessage> history) {
        return observability.observe("pulse.rag.chat", obs -> {
            observability.high(obs, "user.id", userId);
            observability.high(obs, "message.length", message.length());
            observability.high(obs, "history.size", history != null ? history.size() : 0);
        }, () -> {
            UserProfile profile = profileRepo.findByUserId(userId).orElse(null);

            List<RoutingDecision> routingDecisions = new ArrayList<>();
            StringBuilder context = new StringBuilder();

            context.append("=== STUDENT PROFILE ===\n");
            if (profile != null) {
                if (profile.getProfileMarkdown() != null) {
                    context.append(profile.getProfileMarkdown()).append("\n");
                }
                context.append("Target Role: ").append(profile.getTargetRole() != null ? profile.getTargetRole() : "not set").append("\n");
                if (profile.getGraduationYear() != null) {
                    context.append("Graduation Year: ").append(profile.getGraduationYear()).append("\n");
                }
                if (profile.getAxisScores() != null) {
                    context.append("Axis Scores: ").append(profile.getAxisScores()).append("\n");
                }
            }
            context.append("\n");

            List<String> retrievalSourceTypes = new ArrayList<>();

            // --- Resume: smart route ---
            if (profile != null && profile.getResumeText() != null && !profile.getResumeText().isBlank()) {
                int resumeLen = profile.getResumeText().length();
                if (resumeLen < RESUME_INLINE_THRESHOLD) {
                    context.append("=== RESUME ===\n").append(profile.getResumeText()).append("\n\n");
                    routingDecisions.add(new RoutingDecision("resume", "inline",
                            resumeLen + " chars, below " + RESUME_INLINE_THRESHOLD + " threshold"));
                } else {
                    retrievalSourceTypes.add("resume_section");
                    routingDecisions.add(new RoutingDecision("resume", "retrieved",
                            resumeLen + " chars, exceeds " + RESUME_INLINE_THRESHOLD + " threshold"));
                }
            }

            // --- LeetCode: always inline ---
            if (profile != null && profile.getLeetcodeStats() != null && !profile.getLeetcodeStats().isEmpty()) {
                context.append("=== LEETCODE ===\n").append(profile.getLeetcodeStats()).append("\n\n");
            }

            // --- Goals, Tasks, Roadmap: smart route ---
            List<UserGoal> goals = goalRepo.findByUserId(userId);
            List<AiTask> tasks = aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(userId).stream()
                    .filter(t -> !t.isCompleted()).toList();
            List<RoadmapItem> roadmap = roadmapRepo.findByUserIdOrderByFocusRankAsc(userId);
            int totalItems = goals.size() + tasks.size() + roadmap.size();

            if (totalItems <= ITEMS_INLINE_THRESHOLD) {
                if (!goals.isEmpty()) {
                    context.append("=== GOALS ===\n");
                    for (UserGoal g : goals) {
                        context.append("- ").append(g.getTitle())
                                .append(" (").append(g.getCurrentValue()).append("/").append(g.getTargetValue()).append(" ").append(g.getUnit()).append(")")
                                .append(g.getDeadline() != null ? " due " + g.getDeadline() : "")
                                .append(" [").append(g.getStatus()).append("]\n");
                    }
                    context.append("\n");
                }
                if (!tasks.isEmpty()) {
                    context.append("=== STUDY PLAN (UNCOMPLETED TASKS) ===\n");
                    for (AiTask t : tasks) {
                        context.append("- [").append(t.getAxis()).append("] ").append(t.getTitle()).append("\n");
                    }
                    context.append("\n");
                }
                if (!roadmap.isEmpty()) {
                    context.append("=== CAREER ROADMAP ===\n");
                    for (RoadmapItem r : roadmap) {
                        context.append("- ").append(r.getTitle())
                                .append(" (").append(r.getPhase()).append(")")
                                .append(r.getDeadline() != null ? " due " + r.getDeadline() : "")
                                .append(" [").append(r.getStatus()).append("]\n");
                    }
                    context.append("\n");
                }
                routingDecisions.add(new RoutingDecision("goals_tasks_roadmap", "inline",
                        totalItems + " items, below " + ITEMS_INLINE_THRESHOLD + " threshold"));
            } else {
                retrievalSourceTypes.add("goal");
                retrievalSourceTypes.add("task");
                retrievalSourceTypes.add("roadmap");
                routingDecisions.add(new RoutingDecision("goals_tasks_roadmap", "retrieved",
                        totalItems + " items, exceeds " + ITEMS_INLINE_THRESHOLD + " threshold"));
            }

            // --- GitHub READMEs: smart route ---
            List<GitHubRepository> repos = githubRepo.findByUserIdOrderByPushedAtDesc(userId);
            long reposWithReadme = repos.stream()
                    .filter(r -> r.getReadmeContent() != null && !r.getReadmeContent().isBlank())
                    .count();

            if (reposWithReadme <= GITHUB_INLINE_THRESHOLD) {
                for (GitHubRepository repo : repos) {
                    if (repo.getReadmeContent() != null && !repo.getReadmeContent().isBlank()) {
                        context.append("=== PROJECT: ").append(repo.getRepoFullName()).append(" ===\n");
                        if (repo.getDescription() != null) context.append(repo.getDescription()).append("\n");
                        if (repo.getPrimaryLanguage() != null) context.append("Language: ").append(repo.getPrimaryLanguage()).append("\n");
                        context.append(repo.getReadmeContent(), 0, Math.min(repo.getReadmeContent().length(), 2000));
                        context.append("\n\n");
                    }
                }
                routingDecisions.add(new RoutingDecision("github_readmes", "inline",
                        reposWithReadme + " repos, below " + GITHUB_INLINE_THRESHOLD + " threshold"));
            } else {
                retrievalSourceTypes.add("github_readme");
                routingDecisions.add(new RoutingDecision("github_readmes", "retrieved",
                        reposWithReadme + " repos, exceeds " + GITHUB_INLINE_THRESHOLD + " threshold"));
            }

            // --- Obsidian: always retrieve via RAG (temporal data) ---
            retrievalSourceTypes.add("obsidian_diff");
            routingDecisions.add(new RoutingDecision("obsidian_notes", "retrieved",
                    "temporal data, always retrieved for relevance"));

            // --- Hybrid Retrieval for non-inlined sources ---
            List<HybridRetrievalService.RetrievedChunk> retrieved = List.of();
            List<SourceCitation> sources = new ArrayList<>();

            if (!retrievalSourceTypes.isEmpty()) {
                retrieved = retrievalService.retrieve(userId, message, RAG_TOP_K, retrievalSourceTypes);

                if (!retrieved.isEmpty()) {
                    context.append("=== RETRIEVED KNOWLEDGE ===\n");
                    for (HybridRetrievalService.RetrievedChunk chunk : retrieved) {
                        String label = formatSourceLabel(chunk.sourceType(), chunk.sourceKey());
                        context.append("[Source: ").append(label).append("]\n");
                        context.append(chunk.content()).append("\n\n");
                        sources.add(new SourceCitation(chunk.sourceType(), chunk.sourceKey(), chunk.rrfScore()));
                    }
                }
            }

            // --- Build messages ---
            String systemPrompt = "You are a personal CS career coach for this student. " +
                    "Answer their question using ONLY the information provided below about the student. " +
                    "Be specific and reference real data from the context. " +
                    "Do not invent information not present in the context. " +
                    "If the context doesn't contain enough information to answer, say so.\n\n" +
                    context;

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));

            if (history != null) {
                int start = Math.max(0, history.size() - MAX_HISTORY_TURNS * 2);
                for (int i = start; i < history.size(); i++) {
                    ChatMessage msg = history.get(i);
                    messages.add(Map.of("role", msg.role(), "content", msg.content()));
                }
            }
            messages.add(Map.of("role", "user", "content", message));

            String response = anthropicService.generateChat(messages).block();

            return new ChatResponse(response, sources, routingDecisions);
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

    private String formatSourceLabel(String sourceType, String sourceKey) {
        return switch (sourceType) {
            case "github_readme" -> sourceKey + " (GitHub README)";
            case "obsidian_diff" -> sourceKey + " (Study Notes)";
            case "resume_section" -> sourceKey + " (Resume)";
            case "goal" -> "Goal (ID: " + sourceKey + ")";
            case "task" -> "Study Task (ID: " + sourceKey + ")";
            case "roadmap" -> "Roadmap Item (ID: " + sourceKey + ")";
            default -> sourceType + ": " + sourceKey;
        };
    }

    public record ChatMessage(String role, String content) {}
    public record SourceCitation(String sourceType, String sourceKey, double score) {}
    public record RoutingDecision(String sourceType, String decision, String reason) {}
    public record ChatResponse(String message, List<SourceCitation> sources, List<RoutingDecision> routingDecisions) {}
}
