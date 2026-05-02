package com.pulse.pulse.activity.application;

import com.pulse.pulse.activity.domain.IntegrationEvent;
import com.pulse.pulse.activity.infrastructure.persistence.GitHubRepositoryRepository;
import com.pulse.pulse.activity.infrastructure.persistence.IntegrationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.pulse.pulse.platform.ai.OpenAiService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final IntegrationEventRepository eventRepo;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final OpenAiService openAiService;

    public Map<String, Long> getMetrics(Long userId) {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        List<Object[]> rows = eventRepo.countByProviderSince(userId, startOfDay);

        Map<String, Long> result = new HashMap<>();
        result.put("github", 0L);
        result.put("obsidian", 0L);
        result.put("slack", 0L);
        result.put("linear", 0L);

        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }

        return result;
    }

    public Map<String, Map<String, Long>> getEventTypeBreakdown(Long userId) {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        List<Object[]> rows = eventRepo.countByProviderAndEventTypeSince(userId, startOfDay);

        Map<String, Map<String, Long>> result = new HashMap<>();
        for (Object[] row : rows) {
            String provider = (String) row[0];
            String eventType = (String) row[1];
            Long count = (Long) row[2];
            result.computeIfAbsent(provider, k -> new HashMap<>()).put(eventType, count);
        }
        return result;
    }

    public Map<String, Map<String, Long>> getTrend(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days).truncatedTo(ChronoUnit.DAYS);
        String[] providers = {"github", "obsidian", "slack", "linear"};

        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().minusDays(days - 1);
        for (int i = 0; i < days; i++) {
            result.put(start.plusDays(i).toString(), new HashMap<>());
        }

        for (String provider : providers) {
            List<Object[]> rows = eventRepo.countByProviderPerDay(userId, provider, since);
            for (Object[] row : rows) {
                String date = row[0].toString();
                Long count = (Long) row[1];
                Map<String, Long> dayMap = result.get(date);
                if (dayMap != null) {
                    dayMap.put(provider, count);
                }
            }
        }

        return result;
    }

    public List<ActivityEventView> getRecentActivity(Long userId, int limit) {
        return eventRepo.findByUserIdOrderByEventTimestampDesc(userId, PageRequest.of(0, limit)).stream()
                .map(this::toView)
                .toList();
    }

    public Map<String, Long> getActivityBySource(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Object[]> rows = eventRepo.countByProviderSince(userId, since);

        Map<String, Long> result = new HashMap<>();
        result.put("github", 0L);
        result.put("obsidian", 0L);
        result.put("slack", 0L);
        result.put("linear", 0L);

        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }

        return result;
    }

    public Map<String, Integer> getActivityHeatmap(Long userId, int months) {
        LocalDateTime since = LocalDateTime.now().minusMonths(months);
        List<Object[]> rows = eventRepo.countByUserIdGroupByDate(userId, since);

        Map<String, Integer> heatmap = new HashMap<>();
        for (Object[] row : rows) {
            heatmap.put(row[0].toString(), ((Long) row[1]).intValue());
        }
        return heatmap;
    }

    public List<ActivityEventView> getRecentEventsSince(Long userId, LocalDateTime since) {
        return eventRepo.findByUserIdAndEventTimestampAfterOrderByEventTimestampDesc(userId, since).stream()
                .map(this::toView)
                .toList();
    }

    public List<Integer> getActivitySparkline(Long userId, String provider) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Object[]> dailyCounts = eventRepo.countByProviderPerDay(userId, provider, since);

        Map<String, Long> countMap = new HashMap<>();
        for (Object[] row : dailyCounts) {
            countMap.put(row[0].toString(), (Long) row[1]);
        }

        List<Integer> sparkline = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String date = LocalDateTime.now().minusDays(i).toLocalDate().toString();
            sparkline.add(countMap.getOrDefault(date, 0L).intValue());
        }

        return sparkline;
    }

    public int ingestEvents(ActivityIngestCommand command) {
        int newEvents = 0;
        for (ActivityIngestItem item : command.items()) {
            if (!eventRepo.existsByUserIdAndProviderAndExternalId(
                    command.userId(), command.provider(), item.externalId())) {
                eventRepo.save(IntegrationEvent.builder()
                        .userId(command.userId())
                        .provider(command.provider())
                        .eventType(item.eventType())
                        .title(item.title())
                        .externalId(item.externalId())
                        .eventTimestamp(item.eventTimestamp())
                        .rawPayload(item.rawPayload())
                        .build());
                newEvents++;
            }
        }
        return newEvents;
    }

    public List<RepositorySnapshotView> getGitHubRepositories(Long userId) {
        return gitHubRepositoryRepository.findByUserIdOrderByPushedAtDesc(userId).stream()
                .map(this::toView)
                .toList();
    }

    public List<RepositorySnapshotView> getActiveGitHubRepositories(Long userId, LocalDateTime since) {
        return gitHubRepositoryRepository.findActiveByUserId(userId, since).stream()
                .map(this::toView)
                .toList();
    }

    public String getGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    public String getDateLabel() {
        return LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH));
    }

    public String generateDigest(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<IntegrationEvent> events = eventRepo
                .findByUserIdAndEventTimestampAfterOrderByEventTimestampDesc(userId, since);

        if (events.isEmpty()) {
            return "No activity recorded in the last 24 hours. Connect some integrations to start tracking your productivity.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Here is the CS student's activity from the last 24 hours:\n\n");
        for (IntegrationEvent e : events) {
            context.append(String.format("- [%s] %s (%s)\n",
                    e.getProvider(), e.getTitle(),
                    e.getEventTimestamp().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))));
        }

        try {
            return openAiService.generateDigest(context.toString()).block();
        } catch (Exception e) {
            log.error("Failed to generate AI digest", e);
            return "Could not generate digest at this time. You had " + events.size() + " activities in the last 24 hours.";
        }
    }

    private ActivityEventView toView(IntegrationEvent event) {
        return new ActivityEventView(
                event.getId(),
                event.getProvider(),
                event.getEventType(),
                event.getTitle(),
                event.getExternalId(),
                event.getEventTimestamp(),
                event.getRawPayload()
        );
    }

    private RepositorySnapshotView toView(com.pulse.pulse.activity.domain.GitHubRepository repo) {
        return new RepositorySnapshotView(
                repo.getUserId(),
                repo.getRepoFullName(),
                repo.getRepoName(),
                repo.getDescription(),
                repo.getPrimaryLanguage(),
                repo.getTopics(),
                repo.getLanguages(),
                repo.getPushedAt(),
                repo.getOpenIssuesCount(),
                repo.isFork(),
                repo.getDefaultBranch(),
                repo.getLastSyncedAt(),
                repo.getRecentCommits(),
                repo.getOpenPrs(),
                repo.getAssignedIssues(),
                repo.getReadmeContent()
        );
    }
}
