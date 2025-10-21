package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.ActivityFeed;
import com.outreachly.outreachly.repository.ActivityFeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityFeedService {

    private final ActivityFeedRepository activityFeedRepository;

    /**
     * Create a new activity feed entry
     */
    public ActivityFeed createActivity(
            UUID orgId,
            Long userId,
            ActivityFeed.ActivityType activityType,
            String title,
            String description,
            ActivityFeed.ActivityStatus status) {
        ActivityFeed activity = ActivityFeed.builder()
                .orgId(orgId)
                .userId(userId)
                .activityType(activityType)
                .title(title)
                .description(description)
                .status(status)
                .build();

        ActivityFeed savedActivity = activityFeedRepository.save(activity);
        log.info("Created activity feed entry: {} for org: {} by user: {}",
                activityType, orgId, userId);

        return savedActivity;
    }

    /**
     * Get activities for an organization with pagination
     */
    public Page<ActivityFeed> getActivitiesByOrgId(UUID orgId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return activityFeedRepository.findByOrgIdOrderByCreatedAtDesc(orgId, pageable);
    }

    /**
     * Get recent activities for an organization (last 7 days)
     */
    public List<ActivityFeed> getRecentActivitiesByOrgId(UUID orgId) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        return activityFeedRepository.findRecentActivitiesByOrgId(orgId, sevenDaysAgo);
    }

    /**
     * Get activities by organization and activity type
     */
    public Page<ActivityFeed> getActivitiesByOrgIdAndType(
            UUID orgId,
            ActivityFeed.ActivityType activityType,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return activityFeedRepository.findByOrgIdAndActivityTypeOrderByCreatedAtDesc(
                orgId, activityType, pageable);
    }

    /**
     * Get activities by organization and status
     */
    public Page<ActivityFeed> getActivitiesByOrgIdAndStatus(
            UUID orgId,
            ActivityFeed.ActivityStatus status,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return activityFeedRepository.findByOrgIdAndStatusOrderByCreatedAtDesc(
                orgId, status, pageable);
    }

    /**
     * Get activities by user
     */
    public Page<ActivityFeed> getActivitiesByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return activityFeedRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get activities by organization and user
     */
    public Page<ActivityFeed> getActivitiesByOrgIdAndUserId(
            UUID orgId,
            Long userId,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return activityFeedRepository.findByOrgIdAndUserIdOrderByCreatedAtDesc(
                orgId, userId, pageable);
    }

    /**
     * Count activities by organization and type
     */
    public long countActivitiesByOrgIdAndType(UUID orgId, ActivityFeed.ActivityType activityType) {
        return activityFeedRepository.countByOrgIdAndActivityType(orgId, activityType);
    }

    /**
     * Count activities by organization and status
     */
    public long countActivitiesByOrgIdAndStatus(UUID orgId, ActivityFeed.ActivityStatus status) {
        return activityFeedRepository.countByOrgIdAndStatus(orgId, status);
    }

    /**
     * Helper method to create import activity
     */
    public ActivityFeed createImportActivity(
            UUID orgId,
            Long userId,
            String filename,
            int processedRows,
            int totalRows,
            ActivityFeed.ActivityStatus status,
            String errorMessage) {
        String title = status == ActivityFeed.ActivityStatus.success ? "CSV import completed"
                : status == ActivityFeed.ActivityStatus.error ? "CSV import failed" : "CSV import in progress";

        String description = status == ActivityFeed.ActivityStatus.success
                ? String.format("Successfully processed %d leads from %s", processedRows, filename)
                : status == ActivityFeed.ActivityStatus.error ? String.format("Failed to import %s%s", filename,
                        errorMessage != null ? " - " + errorMessage : "")
                        : String.format("Processing %d leads from %s", totalRows, filename);

        return createActivity(orgId, userId, ActivityFeed.ActivityType.csv_import,
                title, description, status);
    }

    /**
     * Helper method to create campaign activity
     */
    public ActivityFeed createCampaignActivity(
            UUID orgId,
            Long userId,
            String campaignName,
            String action,
            int emailCount,
            ActivityFeed.ActivityStatus status) {
        String title = String.format("Campaign %s", action);
        String description = String.format("%s campaign %s %d emails",
                campaignName, action, emailCount);

        return createActivity(orgId, userId, ActivityFeed.ActivityType.campaign,
                title, description, status);
    }

    /**
     * Helper method to create domain activity
     */
    public ActivityFeed createDomainActivity(
            UUID orgId,
            Long userId,
            String domain,
            ActivityFeed.ActivityStatus status) {
        String title = "Domain configured";
        String description = String.format("Successfully configured %s", domain);

        return createActivity(orgId, userId, ActivityFeed.ActivityType.domain,
                title, description, status);
    }

    /**
     * Helper method to create checkpoint activity
     */
    public ActivityFeed createCheckpointActivity(
            UUID orgId,
            Long userId,
            String checkpointName,
            String action,
            int leadCount,
            ActivityFeed.ActivityStatus status) {
        String title = String.format("Checkpoint %s", action);
        String description = String.format("%s %s for %d leads",
                checkpointName, action, leadCount);

        return createActivity(orgId, userId, ActivityFeed.ActivityType.checkpoint,
                title, description, status);
    }
}
