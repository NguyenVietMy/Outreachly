package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.CampaignCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignCheckpointRepository extends JpaRepository<CampaignCheckpoint, UUID> {

    // Find all checkpoints for a campaign
    @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId ORDER BY cp.dayOfWeek, cp.timeOfDay")
    List<CampaignCheckpoint> findByCampaignIdOrderByDayAndTime(@Param("campaignId") UUID campaignId);

    // Find checkpoints by campaign and status
    @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId AND cp.status = :status ORDER BY cp.dayOfWeek, cp.timeOfDay")
    List<CampaignCheckpoint> findByCampaignIdAndStatus(@Param("campaignId") UUID campaignId,
            @Param("status") CampaignCheckpoint.CheckpointStatus status);

    // Find checkpoints by day of week
    @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.dayOfWeek = :dayOfWeek AND cp.status = 'active' ORDER BY cp.timeOfDay")
    List<CampaignCheckpoint> findByDayOfWeekAndActive(@Param("dayOfWeek") DayOfWeek dayOfWeek);

    // Find checkpoint by ID and verify campaign belongs to organization
    @Query("SELECT cp FROM CampaignCheckpoint cp JOIN cp.campaign c WHERE cp.id = :checkpointId AND c.orgId = :orgId")
    Optional<CampaignCheckpoint> findByIdAndOrgId(@Param("checkpointId") UUID checkpointId, @Param("orgId") UUID orgId);

    // Find checkpoints for a campaign with organization verification
    @Query("SELECT cp FROM CampaignCheckpoint cp JOIN cp.campaign c WHERE cp.campaignId = :campaignId AND c.orgId = :orgId ORDER BY cp.dayOfWeek, cp.timeOfDay")
    List<CampaignCheckpoint> findByCampaignIdAndOrgId(@Param("campaignId") UUID campaignId, @Param("orgId") UUID orgId);

    // Count checkpoints by campaign
    @Query("SELECT COUNT(cp) FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId")
    long countByCampaignId(@Param("campaignId") UUID campaignId);

    // Count checkpoints by campaign and status
    @Query("SELECT COUNT(cp) FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId AND cp.status = :status")
    long countByCampaignIdAndStatus(@Param("campaignId") UUID campaignId,
            @Param("status") CampaignCheckpoint.CheckpointStatus status);

    // Find active checkpoints that should be processed (for scheduling service)
    @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.status = 'active' AND cp.dayOfWeek = :dayOfWeek ORDER BY cp.timeOfDay")
    List<CampaignCheckpoint> findActiveCheckpointsForDay(@Param("dayOfWeek") DayOfWeek dayOfWeek);

    // Delete all checkpoints for a campaign (cascade will handle checkpoint leads)
    void deleteByCampaignId(UUID campaignId);
}
