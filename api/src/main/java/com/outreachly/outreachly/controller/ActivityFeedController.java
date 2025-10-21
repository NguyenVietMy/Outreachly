package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.ActivityFeed;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.ActivityFeedService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/activity-feed")
@RequiredArgsConstructor
@Slf4j
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;
    private final UserService userService;

    /**
     * Get activities for the current user's organization
     * GET /api/activity-feed
     */
    @GetMapping
    public ResponseEntity<Page<ActivityFeed>> getActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);
            if (user == null || user.getOrgId() == null) {
                return ResponseEntity.badRequest().build();
            }

            Page<ActivityFeed> activities = activityFeedService.getActivitiesByOrgId(
                    user.getOrgId(), page, size);

            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            log.error("Error getting activities", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recent activities for the current user's organization (last 7 days)
     * GET /api/activity-feed/recent
     */
    @GetMapping("/recent")
    public ResponseEntity<List<ActivityFeed>> getRecentActivities(Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);
            if (user == null || user.getOrgId() == null) {
                return ResponseEntity.badRequest().build();
            }

            List<ActivityFeed> activities = activityFeedService.getRecentActivitiesByOrgId(
                    user.getOrgId());

            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            log.error("Error getting recent activities", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get activities by type
     * GET /api/activity-feed/type/{type}
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<Page<ActivityFeed>> getActivitiesByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);
            if (user == null || user.getOrgId() == null) {
                return ResponseEntity.badRequest().build();
            }

            ActivityFeed.ActivityType activityType;
            try {
                activityType = ActivityFeed.ActivityType.valueOf(type.toLowerCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }

            Page<ActivityFeed> activities = activityFeedService.getActivitiesByOrgIdAndType(
                    user.getOrgId(), activityType, page, size);

            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            log.error("Error getting activities by type", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get activities by status
     * GET /api/activity-feed/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<ActivityFeed>> getActivitiesByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);
            if (user == null || user.getOrgId() == null) {
                return ResponseEntity.badRequest().build();
            }

            ActivityFeed.ActivityStatus activityStatus;
            try {
                activityStatus = ActivityFeed.ActivityStatus.valueOf(status.toLowerCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }

            Page<ActivityFeed> activities = activityFeedService.getActivitiesByOrgIdAndStatus(
                    user.getOrgId(), activityStatus, page, size);

            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            log.error("Error getting activities by status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get activity statistics for the current user's organization
     * GET /api/activity-feed/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getActivityStats(Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);
            if (user == null || user.getOrgId() == null) {
                return ResponseEntity.badRequest().build();
            }

            UUID orgId = user.getOrgId();

            Map<String, Object> stats = Map.of(
                    "totalImports", activityFeedService.countActivitiesByOrgIdAndType(
                            orgId, ActivityFeed.ActivityType.csv_import),
                    "totalCampaigns", activityFeedService.countActivitiesByOrgIdAndType(
                            orgId, ActivityFeed.ActivityType.campaign),
                    "totalDomains", activityFeedService.countActivitiesByOrgIdAndType(
                            orgId, ActivityFeed.ActivityType.domain),
                    "totalCheckpoints", activityFeedService.countActivitiesByOrgIdAndType(
                            orgId, ActivityFeed.ActivityType.checkpoint),
                    "successfulActivities", activityFeedService.countActivitiesByOrgIdAndStatus(
                            orgId, ActivityFeed.ActivityStatus.success),
                    "failedActivities", activityFeedService.countActivitiesByOrgIdAndStatus(
                            orgId, ActivityFeed.ActivityStatus.error));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting activity stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create a new activity (for internal use)
     * POST /api/activity-feed
     */
    @PostMapping
    public ResponseEntity<ActivityFeed> createActivity(
            @RequestBody CreateActivityRequest request,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);
            if (user == null || user.getOrgId() == null) {
                return ResponseEntity.badRequest().build();
            }

            ActivityFeed activity = activityFeedService.createActivity(
                    user.getOrgId(),
                    user.getId(),
                    request.getActivityType(),
                    request.getTitle(),
                    request.getDescription(),
                    request.getStatus());

            return ResponseEntity.ok(activity);
        } catch (Exception e) {
            log.error("Error creating activity", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private User getUserFromAuthentication(Authentication authentication) {
        String userEmail = authentication.getName();
        return userService.findByEmail(userEmail);
    }

    // Request DTO for creating activities
    public static class CreateActivityRequest {
        private ActivityFeed.ActivityType activityType;
        private String title;
        private String description;
        private ActivityFeed.ActivityStatus status;

        // Getters and setters
        public ActivityFeed.ActivityType getActivityType() {
            return activityType;
        }

        public void setActivityType(ActivityFeed.ActivityType activityType) {
            this.activityType = activityType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public ActivityFeed.ActivityStatus getStatus() {
            return status;
        }

        public void setStatus(ActivityFeed.ActivityStatus status) {
            this.status = status;
        }
    }
}
