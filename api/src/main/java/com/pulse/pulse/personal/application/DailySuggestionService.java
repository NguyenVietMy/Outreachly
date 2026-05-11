package com.pulse.pulse.personal.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.activity.application.ActivityEventView;
import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.activity.application.RepositorySnapshotView;
import com.pulse.pulse.integrations.application.IntegrationService;
import com.pulse.pulse.personal.domain.AiTask;
import com.pulse.pulse.personal.domain.DailySuggestion;
import com.pulse.pulse.personal.domain.RoadmapItem;
import com.pulse.pulse.personal.domain.UserGoal;
import com.pulse.pulse.personal.infrastructure.persistence.AiTaskRepository;
import com.pulse.pulse.personal.infrastructure.persistence.DailySuggestionRepository;
import com.pulse.pulse.personal.infrastructure.persistence.RoadmapItemRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserGoalRepository;
import com.pulse.pulse.personal.infrastructure.persistence.UserProfileRepository;
import com.pulse.pulse.platform.ai.OpenAiService;
import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DailySuggestionService {

    private final DailySuggestionRepository suggestionRepo;
    private final UserProfileRepository profileRepo;
    private final UserGoalRepository goalRepo;
    private final DashboardService dashboardService;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final PulseObservability observability;
    private final IntegrationService integrationService;
    private final AiTaskRepository aiTaskRepo;
    private final RoadmapItemRepository roadmapRepo;

    private static final long CACHE_HOURS = 12;
    private static final long REGENERATE_COOLDOWN_HOURS = 1;

    public DailySuggestionService(DailySuggestionRepository suggestionRepo,
                                  UserProfileRepository profileRepo,
                                  UserGoalRepository goalRepo,
                                  DashboardService dashboardService,
                                  OpenAiService openAiService,
                                  ObjectMapper objectMapper,
                                  PulseObservability observability,
                                  IntegrationService integrationService,
                                  AiTaskRepository aiTaskRepo,
                                  RoadmapItemRepository roadmapRepo) {
        this.suggestionRepo = suggestionRepo;
        this.profileRepo = profileRepo;
        this.goalRepo = goalRepo;
        this.dashboardService = dashboardService;
        this.openAiService = openAiService;
        this.objectMapper = objectMapper;
        this.observability = observability;
        this.integrationService = integrationService;
        this.aiTaskRepo = aiTaskRepo;
        this.roadmapRepo = roadmapRepo;
    }

    public DailySuggestionView getSuggestionsForToday(Long userId) {
        Observation observation = observability.start("pulse.suggestions.generate");
        observability.low(observation, "operation", "today");
        observability.high(observation, "user.id", userId);

        try {
            return observability.scoped(observation, () -> {
                LocalDate today = LocalDate.now();
                Optional<DailySuggestion> cached = suggestionRepo.findByUserIdAndSuggestionDate(userId, today);

                if (cached.isPresent()) {
                    DailySuggestion existing = cached.get();
                    if (existing.getGeneratedAt().plusHours(CACHE_HOURS).isAfter(LocalDateTime.now())) {
                        observability.low(observation, "cache", "cache_hit");
                        observability.low(observation, "result", "cache_hit");
                        observability.counter("pulse.suggestions.requests",
                                        "operation", "today",
                                        "result", "cache_hit")
                                .increment();
                        return toView(existing);
                    }
                }

                observability.low(observation, "cache", "miss");
                DailySuggestionView result = generateSuggestions(userId, today, "today");
                observability.low(observation, "result", "generated");
                return result;
            });
        } catch (RuntimeException e) {
            observation.error(e);
            observability.low(observation, "result", "error");
            throw e;
        } finally {
            observation.stop();
        }
    }

    public DailySuggestionView regenerateSuggestions(Long userId) {
        Observation observation = observability.start("pulse.suggestions.generate");
        observability.low(observation, "operation", "regenerate");
        observability.high(observation, "user.id", userId);

        try {
            return observability.scoped(observation, () -> {
                LocalDate today = LocalDate.now();
                Optional<DailySuggestion> existing = suggestionRepo.findByUserIdAndSuggestionDate(userId, today);

                if (existing.isPresent()) {
                    DailySuggestion current = existing.get();
                    if (current.getGeneratedAt().plusHours(REGENERATE_COOLDOWN_HOURS).isAfter(LocalDateTime.now())) {
                        observability.low(observation, "cache", "cooldown");
                        observability.low(observation, "result", "cooldown");
                        observability.counter("pulse.suggestions.requests",
                                        "operation", "regenerate",
                                        "result", "cooldown")
                                .increment();
                        return null;
                    }
                }

                observability.low(observation, "cache", "regenerate");
                DailySuggestionView result = generateSuggestions(userId, today, "regenerate");
                observability.low(observation, "result", "generated");
                return result;
            });
        } catch (RuntimeException e) {
            observation.error(e);
            observability.low(observation, "result", "error");
            throw e;
        } finally {
            observation.stop();
        }
    }

    public LocalDateTime getNextAllowedRegenerateTime(Long userId) {
        LocalDate today = LocalDate.now();
        return suggestionRepo.findByUserIdAndSuggestionDate(userId, today)
                .map(s -> s.getGeneratedAt().plusHours(REGENERATE_COOLDOWN_HOURS))
                .orElse(null);
    }

    @Transactional
    private DailySuggestionView generateSuggestions(Long userId, LocalDate date, String operation) {
        Timer.Sample sample = observability.timerSample();
        String result = "error";
        String context = buildContextPrompt(userId);
        String contextHash = sha256(context);

        try {
            String response = openAiService.generateDailySuggestions(context).block();
            if (response == null) {
                throw new RuntimeException("Failed to generate daily suggestions");
            }

            List<Map<String, Object>> tasks;
            try {
                tasks = objectMapper.readValue(response, new TypeReference<>() {
                });
            } catch (Exception e) {
                log.error("Failed to parse daily suggestions response: {}", response, e);
                throw new RuntimeException("Failed to parse daily suggestions", e);
            }

            Optional<DailySuggestion> existing = suggestionRepo.findByUserIdAndSuggestionDate(userId, date);
            DailySuggestion suggestion;
            if (existing.isPresent()) {
                suggestion = existing.get();
                suggestion.setTasks(tasks);
                suggestion.setContextHash(contextHash);
                suggestion.setGeneratedAt(LocalDateTime.now());
            } else {
                suggestion = DailySuggestion.builder()
                        .userId(userId)
                        .suggestionDate(date)
                        .tasks(tasks)
                        .contextHash(contextHash)
                        .generatedAt(LocalDateTime.now())
                        .build();
            }

            result = "generated";
            return toView(suggestionRepo.save(suggestion));
        } finally {
            observability.counter("pulse.suggestions.requests",
                            "operation", operation,
                            "result", result)
                    .increment();
            observability.stopTimer(sample, "pulse.suggestions.generate.duration", "result", result);
        }
    }

    private String buildContextPrompt(Long userId) {
        StringBuilder sb = new StringBuilder();

        buildProfileBlock(sb, userId);
        buildGoalsBlock(sb, userId);
        buildStudyPlanBlock(sb, userId);
        buildRoadmapBlock(sb, userId);
        buildProjectsBlock(sb, userId);
        buildRecentEventsBlock(sb, userId);
        buildObsidianBlock(sb, userId);
        buildDateBlock(sb);

        return sb.toString();
    }

    private void buildProfileBlock(StringBuilder sb, Long userId) {
        profileRepo.findByUserId(userId).ifPresent(profile -> {
            sb.append("=== DEVELOPER PROFILE ===\n");
            if (profile.getProfileMarkdown() != null && !profile.getProfileMarkdown().isEmpty()) {
                sb.append(profile.getProfileMarkdown()).append("\n\n");
            }
            if (profile.getTargetRole() != null) {
                sb.append("Target role: ").append(profile.getTargetRole());
                if (profile.getGraduationYear() != null) {
                    sb.append(" (graduating ").append(profile.getGraduationYear()).append(")");
                }
                sb.append("\n");
            }
            Map<String, Object> scores = profile.getAxisScores();
            if (scores != null && !scores.isEmpty()) {
                sb.append("Readiness scores: ");
                scores.forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
                sb.append("\n");
            }
            sb.append("\n");
        });
    }

    private void buildGoalsBlock(StringBuilder sb, Long userId) {
        List<UserGoal> goals = goalRepo.findByUserIdAndStatus(userId, "active");
        if (goals.isEmpty()) {
            return;
        }

        sb.append("=== ACTIVE GOALS ===\n");
        for (UserGoal goal : goals) {
            sb.append("- ").append(goal.getTitle())
                    .append(" (").append(goal.getCurrentValue()).append("/").append(goal.getTargetValue())
                    .append(" ").append(goal.getUnit()).append(")");
            if (goal.getDeadline() != null) {
                sb.append(" deadline: ").append(goal.getDeadline());
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void buildProjectsBlock(StringBuilder sb, Long userId) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<RepositorySnapshotView> repos = dashboardService.getActiveGitHubRepositories(userId, sevenDaysAgo);
        if (repos.isEmpty()) {
            return;
        }

        sb.append("=== ACTIVE GITHUB PROJECTS ===\n");
        int count = 0;
        for (RepositorySnapshotView repo : repos) {
            if (count >= 5) {
                break;
            }
            sb.append("--- ").append(repo.repoFullName()).append(" ---\n");
            if (repo.description() != null) {
                sb.append("Description: ").append(repo.description()).append("\n");
            }

            Map<String, Object> langs = repo.languages();
            if (langs != null && !langs.isEmpty()) {
                long total = langs.values().stream()
                        .filter(v -> v instanceof Number)
                        .mapToLong(v -> ((Number) v).longValue())
                        .sum();
                if (total > 0) {
                    sb.append("Languages: ");
                    langs.entrySet().stream()
                            .filter(e -> e.getValue() instanceof Number)
                            .sorted((a, b) -> Long.compare(((Number) b.getValue()).longValue(), ((Number) a.getValue()).longValue()))
                            .limit(4)
                            .forEach(e -> {
                                long pct = ((Number) e.getValue()).longValue() * 100 / total;
                                sb.append(e.getKey()).append(" (").append(pct).append("%) ");
                            });
                    sb.append("\n");
                }
            }

            List<Map<String, Object>> commits = repo.recentCommits();
            if (commits != null && !commits.isEmpty()) {
                sb.append("Recent commits:\n");
                int commitCount = 0;
                for (Map<String, Object> commit : commits) {
                    if (commitCount >= 5) {
                        break;
                    }
                    sb.append("  - \"").append(commit.getOrDefault("message", "")).append("\"");
                    Object date = commit.get("date");
                    if (date != null) {
                        sb.append(" (").append(date).append(")");
                    }
                    sb.append("\n");
                    commitCount++;
                }
            }

            List<Map<String, Object>> prs = repo.openPrs();
            if (prs != null && !prs.isEmpty()) {
                sb.append("Open PRs:\n");
                for (Map<String, Object> pr : prs) {
                    sb.append("  - \"").append(pr.getOrDefault("title", "")).append("\"");
                    if (Boolean.TRUE.equals(pr.get("draft"))) {
                        sb.append(" [draft]");
                    }
                    sb.append(" (created: ").append(pr.getOrDefault("createdAt", "")).append(")");
                    sb.append("\n");
                }
            }

            List<Map<String, Object>> issues = repo.assignedIssues();
            if (issues != null && !issues.isEmpty()) {
                sb.append("Assigned issues:\n");
                for (Map<String, Object> issue : issues) {
                    sb.append("  - #").append(issue.getOrDefault("number", "")).append(" ")
                            .append(issue.getOrDefault("title", ""));
                    Object labels = issue.get("labels");
                    if (labels instanceof List && !((List<?>) labels).isEmpty()) {
                        sb.append(" [").append(String.join(", ", ((List<?>) labels).stream()
                                .map(Object::toString).collect(Collectors.toList()))).append("]");
                    }
                    sb.append("\n");
                }
            }

            if (repo.readmeContent() != null && !repo.readmeContent().isEmpty()) {
                String excerpt = repo.readmeContent();
                if (excerpt.length() > 500) {
                    excerpt = excerpt.substring(0, 500) + "...";
                }
                sb.append("README excerpt: ").append(excerpt).append("\n");
            }

            sb.append("\n");
            count++;
        }
    }

    private void buildRecentEventsBlock(StringBuilder sb, Long userId) {
        LocalDateTime fortyEightHoursAgo = LocalDateTime.now().minusHours(48);
        List<ActivityEventView> events = dashboardService.getRecentEventsSince(userId, fortyEightHoursAgo);

        if (events.isEmpty()) {
            return;
        }

        sb.append("=== RECENT ACTIVITY (48 HOURS) ===\n");
        int count = 0;
        for (ActivityEventView event : events) {
            if (count >= 20) {
                break;
            }
            sb.append("- [").append(event.provider()).append("] ").append(event.title())
                    .append(" (").append(event.eventTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append(")\n");
            count++;
        }
        sb.append("\n");
    }

    private void buildDateBlock(StringBuilder sb) {
        LocalDate today = LocalDate.now();
        sb.append("=== TODAY ===\n");
        sb.append("Date: ").append(today).append(" (")
                .append(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)).append(")\n");
    }

    private void buildStudyPlanBlock(StringBuilder sb, Long userId) {
        List<AiTask> uncompleted = aiTaskRepo.findByUserIdOrderByAxisAscOrderIndexAsc(userId)
                .stream()
                .filter(t -> !t.isCompleted())
                .limit(15)
                .toList();

        if (uncompleted.isEmpty()) return;

        sb.append("=== STUDY PLAN (UNCOMPLETED TASKS) ===\n");
        for (AiTask task : uncompleted) {
            sb.append("- [").append(task.getAxis()).append("] ").append(task.getTitle());
            if (task.getPriority() == 0) {
                sb.append(" [HIGH PRIORITY]");
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void buildRoadmapBlock(StringBuilder sb, Long userId) {
        List<RoadmapItem> items = roadmapRepo.findByUserIdOrderByFocusRankAsc(userId);
        if (items.isEmpty()) return;

        sb.append("=== CAREER ROADMAP ===\n");
        for (RoadmapItem item : items) {
            sb.append("- [").append(item.getStatus()).append("] ").append(item.getTitle());
            if (item.getPhase() != null) {
                sb.append(" (").append(item.getPhase()).append(")");
            }
            if (item.getDeadline() != null) {
                long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), item.getDeadline());
                sb.append(" — deadline: ").append(item.getDeadline())
                        .append(" (").append(daysUntil).append(" days away)");
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void buildObsidianBlock(StringBuilder sb, Long userId) {
        try {
            String diffs = integrationService.getObsidianVaultDiffs(userId);
            if (diffs != null && !diffs.isBlank()) {
                sb.append("=== OBSIDIAN DAILY NOTES (LAST 7 DAYS) ===\n");
                sb.append(diffs).append("\n\n");
            }
        } catch (Exception e) {
            log.warn("Could not fetch Obsidian diffs for daily suggestions: {}", e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private DailySuggestionView toView(DailySuggestion suggestion) {
        return new DailySuggestionView(
                suggestion.getSuggestionDate(),
                suggestion.getGeneratedAt(),
                suggestion.getTasks()
        );
    }
}
