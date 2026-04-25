package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.ActivityItemDto;
import com.outreachly.outreachly.dto.DashboardMetricsDto;
import com.outreachly.outreachly.entity.IntegrationEvent;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.DashboardService;
import com.outreachly.outreachly.service.UserService;
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
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        Map<String, Long> counts = dashboardService.getMetrics(user.getId());

        DashboardMetricsDto dto = new DashboardMetricsDto(
                counts.getOrDefault("github", 0L),
                counts.getOrDefault("obsidian", 0L),
                counts.getOrDefault("slack", 0L),
                counts.getOrDefault("linear", 0L),
                dashboardService.getGreeting(),
                dashboardService.getDateLabel()
        );

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/activity")
    public ResponseEntity<List<ActivityItemDto>> getActivity(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        List<IntegrationEvent> events = dashboardService.getRecentActivity(user.getId(), limit);
        List<ActivityItemDto> dtos = events.stream()
                .map(this::toActivityDto)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/activity-by-source")
    public ResponseEntity<Map<String, Long>> getActivityBySource(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(dashboardService.getActivityBySource(user.getId()));
    }

    @PostMapping("/digest")
    public ResponseEntity<Map<String, Object>> generateDigest(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) return ResponseEntity.status(401).build();

        String digest = dashboardService.generateDigest(user.getId());
        return ResponseEntity.ok(Map.of(
                "digest", digest,
                "generatedAt", LocalDateTime.now().toString()
        ));
    }

    private ActivityItemDto toActivityDto(IntegrationEvent event) {
        long minutes = ChronoUnit.MINUTES.between(event.getEventTimestamp(), LocalDateTime.now());
        String timeAgo;
        if (minutes < 1) timeAgo = "Just now";
        else if (minutes < 60) timeAgo = minutes + " min ago";
        else if (minutes < 1440) timeAgo = (minutes / 60) + " hr ago";
        else timeAgo = (minutes / 1440) + " days ago";

        return new ActivityItemDto(
                event.getId().toString(),
                event.getTitle(),
                event.getProvider(),
                event.getEventType(),
                timeAgo,
                event.getEventTimestamp().toString()
        );
    }

    private User getUser(Authentication authentication) {
        if (authentication == null) return null;
        return userService.findByEmail(authentication.getName());
    }
}
