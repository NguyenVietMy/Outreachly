package com.pulse.pulse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.pulse.entity.*;
import com.pulse.pulse.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DailySuggestionService {

    private final DailySuggestionRepository suggestionRepo;
    private final GitHubRepositoryRepository repoRepo;
    private final UserProfileRepository profileRepo;
    private final UserGoalRepository goalRepo;
    private final IntegrationEventRepository eventRepo;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    private static final long CACHE_HOURS = 12;
    private static final long REGENERATE_COOLDOWN_HOURS = 1;

    public DailySuggestionService(DailySuggestionRepository suggestionRepo,
                                   GitHubRepositoryRepository repoRepo,
                                   UserProfileRepository profileRepo,
                                   UserGoalRepository goalRepo,
                                   IntegrationEventRepository eventRepo,
                                   OpenAiService openAiService,
                                   ObjectMapper objectMapper) {
        this.suggestionRepo = suggestionRepo;
        this.repoRepo = repoRepo;
        this.profileRepo = profileRepo;
        this.goalRepo = goalRepo;
        this.eventRepo = eventRepo;
        this.openAiService = openAiService;
        this.objectMapper = objectMapper;
    }

    public DailySuggestion getSuggestionsForToday(Long userId) {
        LocalDate today = LocalDate.now();
        Optional<DailySuggestion> cached = suggestionRepo.findByUserIdAndSuggestionDate(userId, today);

        if (cached.isPresent()) {
            DailySuggestion existing = cached.get();
            if (existing.getGeneratedAt().plusHours(CACHE_HOURS).isAfter(LocalDateTime.now())) {
                return existing;
            }
        }

        return generateSuggestions(userId, today);
    }

    public DailySuggestion regenerateSuggestions(Long userId) {
        LocalDate today = LocalDate.now();
        Optional<DailySuggestion> existing = suggestionRepo.findByUserIdAndSuggestionDate(userId, today);

        if (existing.isPresent()) {
            DailySuggestion current = existing.get();
            if (current.getGeneratedAt().plusHours(REGENERATE_COOLDOWN_HOURS).isAfter(LocalDateTime.now())) {
                return null;
            }
        }

        return generateSuggestions(userId, today);
    }

    public LocalDateTime getNextAllowedRegenerateTime(Long userId) {
        LocalDate today = LocalDate.now();
        return suggestionRepo.findByUserIdAndSuggestionDate(userId, today)
                .map(s -> s.getGeneratedAt().plusHours(REGENERATE_COOLDOWN_HOURS))
                .orElse(null);
    }

    @Transactional
    private DailySuggestion generateSuggestions(Long userId, LocalDate date) {
        String context = buildContextPrompt(userId);
        String contextHash = sha256(context);

        String response = openAiService.generateDailySuggestions(context).block();
        if (response == null) {
            throw new RuntimeException("Failed to generate daily suggestions");
        }

        List<Map<String, Object>> tasks;
        try {
            tasks = objectMapper.readValue(response, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse daily suggestions response: {}", response, e);
            throw new RuntimeException("Failed to parse daily suggestions");
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

        return suggestionRepo.save(suggestion);
    }

    private String buildContextPrompt(Long userId) {
        StringBuilder sb = new StringBuilder();

        buildProfileBlock(sb, userId);
        buildGoalsBlock(sb, userId);
        buildProjectsBlock(sb, userId);
        buildRecentEventsBlock(sb, userId);
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
        if (goals.isEmpty()) return;

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
        List<GitHubRepository> repos = repoRepo.findActiveByUserId(userId, sevenDaysAgo);
        if (repos.isEmpty()) return;

        sb.append("=== ACTIVE GITHUB PROJECTS ===\n");
        int count = 0;
        for (GitHubRepository repo : repos) {
            if (count >= 5) break;
            sb.append("--- ").append(repo.getRepoFullName()).append(" ---\n");
            if (repo.getDescription() != null) {
                sb.append("Description: ").append(repo.getDescription()).append("\n");
            }

            Map<String, Object> langs = repo.getLanguages();
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

            List<Map<String, Object>> commits = repo.getRecentCommits();
            if (commits != null && !commits.isEmpty()) {
                sb.append("Recent commits:\n");
                int commitCount = 0;
                for (Map<String, Object> commit : commits) {
                    if (commitCount >= 5) break;
                    sb.append("  - \"").append(commit.getOrDefault("message", "")).append("\"");
                    Object date = commit.get("date");
                    if (date != null) sb.append(" (").append(date).append(")");
                    sb.append("\n");
                    commitCount++;
                }
            }

            List<Map<String, Object>> prs = repo.getOpenPrs();
            if (prs != null && !prs.isEmpty()) {
                sb.append("Open PRs:\n");
                for (Map<String, Object> pr : prs) {
                    sb.append("  - \"").append(pr.getOrDefault("title", "")).append("\"");
                    if (Boolean.TRUE.equals(pr.get("draft"))) sb.append(" [draft]");
                    sb.append(" (created: ").append(pr.getOrDefault("createdAt", "")).append(")");
                    sb.append("\n");
                }
            }

            List<Map<String, Object>> issues = repo.getAssignedIssues();
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

            if (repo.getReadmeContent() != null && !repo.getReadmeContent().isEmpty()) {
                String excerpt = repo.getReadmeContent();
                if (excerpt.length() > 500) excerpt = excerpt.substring(0, 500) + "...";
                sb.append("README excerpt: ").append(excerpt).append("\n");
            }

            sb.append("\n");
            count++;
        }
    }

    private void buildRecentEventsBlock(StringBuilder sb, Long userId) {
        LocalDateTime fortyEightHoursAgo = LocalDateTime.now().minusHours(48);
        List<IntegrationEvent> events = eventRepo
                .findByUserIdAndEventTimestampAfterOrderByEventTimestampDesc(userId, fortyEightHoursAgo);

        if (events.isEmpty()) return;

        sb.append("=== RECENT ACTIVITY (48 HOURS) ===\n");
        int count = 0;
        for (IntegrationEvent event : events) {
            if (count >= 20) break;
            sb.append("- [").append(event.getProvider()).append("] ").append(event.getTitle())
                    .append(" (").append(event.getEventTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append(")\n");
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

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
