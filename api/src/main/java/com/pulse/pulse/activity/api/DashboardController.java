package com.pulse.pulse.activity.api;

import com.pulse.pulse.activity.api.dto.ActivityItemDto;
import com.pulse.pulse.activity.api.dto.DashboardMetricsDto;
import com.pulse.pulse.activity.application.ActivityEventView;
import com.pulse.pulse.activity.application.DashboardService;
import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsDto> getMetrics(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        Map<String, Long> counts = dashboardService.getMetrics(user.id());
        Map<String, Map<String, Long>> breakdown = dashboardService.getEventTypeBreakdown(user.id());

        DashboardMetricsDto dto = new DashboardMetricsDto(
                counts.getOrDefault("github", 0L),
                counts.getOrDefault("obsidian", 0L),
                counts.getOrDefault("slack", 0L),
                counts.getOrDefault("linear", 0L),
                dashboardService.getGreeting(),
                dashboardService.getDateLabel(),
                breakdown
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/activity")
    public ResponseEntity<List<ActivityItemDto>> getActivity(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<ActivityEventView> events = dashboardService.getRecentActivity(user.id(), limit);
        List<ActivityItemDto> dtos = events.stream()
                .map(this::toActivityDto)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/activity-by-source")
    public ResponseEntity<Map<String, Long>> getActivityBySource(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(dashboardService.getActivityBySource(user.id()));
    }

    @GetMapping("/trend")
    public ResponseEntity<Map<String, Map<String, Long>>> getTrend(
            @RequestParam(defaultValue = "14") int days,
            Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        int clampedDays = Math.min(Math.max(days, 1), 90);
        return ResponseEntity.ok(dashboardService.getTrend(user.id(), clampedDays));
    }

    @PostMapping("/digest")
    public ResponseEntity<Map<String, Object>> generateDigest(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String digest = dashboardService.generateDigest(user.id());
        return ResponseEntity.ok(Map.of(
                "digest", digest,
                "generatedAt", LocalDateTime.now().toString()
        ));
    }

    private ActivityItemDto toActivityDto(ActivityEventView event) {
        long minutes = ChronoUnit.MINUTES.between(event.eventTimestamp(), LocalDateTime.now());
        String timeAgo;
        if (minutes < 1) timeAgo = "Just now";
        else if (minutes < 60) timeAgo = minutes + " min ago";
        else if (minutes < 1440) timeAgo = (minutes / 60) + " hr ago";
        else timeAgo = (minutes / 1440) + " days ago";

        return new ActivityItemDto(
                event.id().toString(),
                event.title(),
                event.provider(),
                event.eventType(),
                timeAgo,
                event.eventTimestamp().toString()
        );
    }

    private CurrentUserView getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.getCurrentUser(authentication.getName());
    }
}
