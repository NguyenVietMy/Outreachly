package com.pulse.pulse.personal.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.personal.domain.AiTask;
import com.pulse.pulse.personal.domain.RoadmapItem;
import com.pulse.pulse.personal.domain.UserGoal;
import com.pulse.pulse.personal.domain.UserProfile;
import com.pulse.pulse.personal.infrastructure.persistence.AiTaskRepository;
import com.pulse.pulse.personal.infrastructure.persistence.RoadmapItemRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import com.pulse.pulse.platform.ai.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapService {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "in_progress", "completed");

    private final RoadmapItemRepository roadmapRepo;
    private final UserProfileRepository profileRepo;
    private final UserGoalRepository goalRepo;
    private final AiTaskRepository aiTaskRepo;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    public List<RoadmapItemView> getRoadmap(Long userId) {
        return roadmapRepo.findByUserIdOrderByFocusRankAsc(userId).stream()
                .map(this::toView)
                .toList();
    }

    public boolean canGenerate(Long userId) {
        UserProfile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile == null || profile.getTargetRole() == null) return false;

        boolean hasResume = profile.getResumeText() != null && !profile.getResumeText().isBlank();
        boolean hasAssessment = (profile.getSystemDesignAnswers() != null && !profile.getSystemDesignAnswers().isEmpty())
                || (profile.getCoreCsAnswers() != null && !profile.getCoreCsAnswers().isEmpty());
        boolean hasLeetCode = profile.getLeetcodeUsername() != null;
        return hasResume || hasAssessment || hasLeetCode;
    }

    @Transactional
    public List<RoadmapItemView> generateRoadmap(Long userId) {
        if (!canGenerate(userId)) {
            throw new IllegalStateException("Insufficient data to generate roadmap");
        }

        String context = buildRoadmapContext(userId);
        String raw = openAiService.generateRoadmap(context).block();
        if (raw == null) {
            throw new RuntimeException("Failed to generate roadmap");
        }

        List<Map<String, Object>> items;
        try {
            items = objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse roadmap response: {}", raw, e);
            throw new RuntimeException("Failed to parse roadmap", e);
        }

        items = items.stream()
                .filter(m -> m.get("title") instanceof String s && !s.isBlank())
                .limit(6)
                .toList();

        if (items.isEmpty()) {
            log.error("Roadmap generation returned no valid items: {}", raw);
            throw new RuntimeException("Roadmap generation produced no valid items");
        }

        roadmapRepo.deleteByUserId(userId);

        List<RoadmapItem> saved = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            LocalDate deadline = null;
            Object deadlineVal = item.get("deadline");
            if (deadlineVal != null) {
                try {
                    deadline = LocalDate.parse(deadlineVal.toString());
                } catch (Exception ignored) {}
            }

            saved.add(roadmapRepo.save(RoadmapItem.builder()
                    .userId(userId)
                    .title(truncate((String) item.get("title"), 200))
                    .description((String) item.get("description"))
                    .phase((String) item.get("phase"))
                    .deadline(deadline)
                    .focusRank(i)
                    .aiRationale((String) item.get("rationale"))
                    .status("pending")
                    .build()));
        }

        return saved.stream().map(this::toView).toList();
    }

    @Transactional
    public List<RoadmapItemView> reorderRoadmap(Long userId, List<UUID> orderedIds) {
        List<RoadmapItem> items = roadmapRepo.findByUserIdOrderByFocusRankAsc(userId);
        Set<UUID> existingIds = items.stream().map(RoadmapItem::getId).collect(Collectors.toSet());
        Set<UUID> submittedIds = new HashSet<>(orderedIds);

        if (orderedIds.size() != existingIds.size() || !existingIds.equals(submittedIds)) {
            throw new IllegalArgumentException("Submitted IDs do not match existing roadmap items");
        }

        Map<UUID, RoadmapItem> byId = items.stream()
                .collect(Collectors.toMap(RoadmapItem::getId, Function.identity()));

        for (int i = 0; i < orderedIds.size(); i++) {
            RoadmapItem item = byId.get(orderedIds.get(i));
            item.setFocusRank(i);
            roadmapRepo.save(item);
        }

        return getRoadmap(userId);
    }

    @Transactional
    public RoadmapItemView updateStatus(Long userId, UUID itemId, String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Must be one of: " + VALID_STATUSES);
        }

        RoadmapItem item = roadmapRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Roadmap item not found"));
        if (!item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized");
        }

        item.setStatus(status);
        return toView(roadmapRepo.save(item));
    }

    @Transactional
    public RoadmapItemView updateItem(Long userId, UUID itemId, String title, String description, LocalDate deadline) {
        RoadmapItem item = roadmapRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Roadmap item not found"));
        if (!item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized");
        }

        if (title != null) {
            if (title.isBlank()) {
                throw new IllegalArgumentException("Title cannot be blank");
            }
            item.setTitle(truncate(title, 200));
        }
        item.setDescription(description);
        item.setDeadline(deadline);
        return toView(roadmapRepo.save(item));
    }

    @Transactional
    public RoadmapItemView createItem(Long userId, String title, String description, LocalDate deadline) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        List<RoadmapItem> existing = roadmapRepo.findByUserIdOrderByFocusRankAsc(userId);
        int nextRank = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getFocusRank() + 1;

        RoadmapItem item = roadmapRepo.save(RoadmapItem.builder()
                .userId(userId)
                .title(truncate(title, 200))
                .description(description)
                .deadline(deadline)
                .focusRank(nextRank)
                .status("pending")
                .build());
        return toView(item);
    }

    @Transactional
    public void deleteItem(Long userId, UUID itemId) {
        RoadmapItem item = roadmapRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Roadmap item not found"));
        if (!item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized");
        }
        roadmapRepo.deleteById(itemId);
    }

    private String buildRoadmapContext(Long userId) {
        UserProfile profile = profileRepo.findByUserId(userId).orElseThrow();
        StringBuilder sb = new StringBuilder();

        sb.append("=== TODAY ===\n");
        sb.append("Date: ").append(LocalDate.now()).append("\n\n");

        sb.append("=== STUDENT INFO ===\n");
        sb.append("Target role: ").append(profile.getTargetRole()).append("\n");
        if (profile.getGraduationYear() != null) {
            sb.append("Graduation year: ").append(profile.getGraduationYear()).append("\n");
        }
        sb.append("\n");

        Map<String, Object> scores = profile.getAxisScores();
        if (scores != null && !scores.isEmpty()) {
            sb.append("=== AXIS SCORES ===\n");
            scores.forEach((k, v) -> sb.append(k).append(": ").append(v).append(" "));
            sb.append("\n\n");
        }

        if (profile.getLeetcodeStats() != null && !profile.getLeetcodeStats().isEmpty()) {
            sb.append("=== LEETCODE STATS ===\n");
            Map<String, Object> lc = profile.getLeetcodeStats();
            sb.append(String.format("Total: %s (Easy: %s, Medium: %s, Hard: %s) DSA Score: %s/100\n\n",
                    lc.get("total"), lc.get("easy"), lc.get("medium"), lc.get("hard"), lc.get("dsaScore")));
        }

        if (profile.getResumeScoreBreakdown() != null && !profile.getResumeScoreBreakdown().isEmpty()) {
            sb.append("=== RESUME SCORE ===\n");
            Object score = profile.getResumeScoreBreakdown().get("total_score");
            Object decision = profile.getResumeScoreBreakdown().get("decision");
            if (score != null) sb.append("Score: ").append(score).append("/100");
            if (decision != null) sb.append(" Decision: ").append(decision);
            sb.append("\n\n");
        }

        List<UserGoal> goals = goalRepo.findByUserIdAndStatus(userId, "active");
        if (!goals.isEmpty()) {
            sb.append("=== ACTIVE GOALS ===\n");
            for (UserGoal g : goals) {
                sb.append("- ").append(g.getTitle());
                if (g.getDeadline() != null) sb.append(" (deadline: ").append(g.getDeadline()).append(")");
                sb.append("\n");
            }
            sb.append("\n");
        }

        List<AiTask> uncompleted = aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(userId).stream()
                .filter(t -> !t.isCompleted())
                .limit(10)
                .toList();
        if (!uncompleted.isEmpty()) {
            sb.append("=== STUDY PLAN (UNCOMPLETED) ===\n");
            for (AiTask t : uncompleted) {
                sb.append("- [").append(t.getAxis()).append("] ").append(t.getTitle()).append("\n");
            }
            sb.append("\n");
        }

        if (!profile.getProfileMarkdown().isBlank()) {
            sb.append("=== AI MEMORY ===\n");
            sb.append(profile.getProfileMarkdown()).append("\n");
        }

        return sb.toString();
    }

    private RoadmapItemView toView(RoadmapItem item) {
        return new RoadmapItemView(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getPhase(),
                item.getDeadline(),
                item.getFocusRank(),
                item.getAiRationale(),
                item.getStatus()
        );
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
