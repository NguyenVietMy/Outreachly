package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.ActivityFeed;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityFeedRepository extends JpaRepository<ActivityFeed, UUID> {

    // Find activities by organization
    Page<ActivityFeed> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);

    // Find activities by organization and activity type
    Page<ActivityFeed> findByOrgIdAndActivityTypeOrderByCreatedAtDesc(
            UUID orgId,
            ActivityFeed.ActivityType activityType,
            Pageable pageable);

    // Find activities by organization and status
    Page<ActivityFeed> findByOrgIdAndStatusOrderByCreatedAtDesc(
            UUID orgId,
            ActivityFeed.ActivityStatus status,
            Pageable pageable);

    // Find recent activities (last N days)
    @Query("SELECT af FROM ActivityFeed af WHERE af.orgId = :orgId AND af.createdAt >= :since ORDER BY af.createdAt DESC")
    List<ActivityFeed> findRecentActivitiesByOrgId(
            @Param("orgId") UUID orgId,
            @Param("since") LocalDateTime since);

    // Count activities by organization and type
    long countByOrgIdAndActivityType(UUID orgId, ActivityFeed.ActivityType activityType);

    // Count activities by organization and status
    long countByOrgIdAndStatus(UUID orgId, ActivityFeed.ActivityStatus status);

    // Find activities by user
    Page<ActivityFeed> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Find activities by organization and user
    Page<ActivityFeed> findByOrgIdAndUserIdOrderByCreatedAtDesc(UUID orgId, Long userId, Pageable pageable);
}
