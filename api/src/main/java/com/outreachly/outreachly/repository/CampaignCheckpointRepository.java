package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.CampaignCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignCheckpointRepository extends JpaRepository<CampaignCheckpoint, UUID> {

        // Find all checkpoints for a campaign
        @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId ORDER BY cp.scheduledDate, cp.timeOfDay")
        List<CampaignCheckpoint> findByCampaignIdOrderByDateAndTime(@Param("campaignId") UUID campaignId);

        // Find checkpoints by campaign and status
        @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId AND cp.status = :status ORDER BY cp.scheduledDate, cp.timeOfDay")
        List<CampaignCheckpoint> findByCampaignIdAndStatus(@Param("campaignId") UUID campaignId,
                        @Param("status") CampaignCheckpoint.CheckpointStatus status);

        // Find checkpoints by scheduled date
        @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.scheduledDate = :scheduledDate AND cp.status = 'active' ORDER BY cp.timeOfDay")
        List<CampaignCheckpoint> findActiveCheckpointsForDate(@Param("scheduledDate") LocalDate scheduledDate);

        // Find checkpoint by ID and verify campaign belongs to organization
        @Query("SELECT cp FROM CampaignCheckpoint cp JOIN cp.campaign c WHERE cp.id = :checkpointId AND c.orgId = :orgId")
        Optional<CampaignCheckpoint> findByIdAndOrgId(@Param("checkpointId") UUID checkpointId,
                        @Param("orgId") UUID orgId);

        // Find checkpoints for a campaign with organization verification
        @Query("SELECT cp FROM CampaignCheckpoint cp JOIN cp.campaign c WHERE cp.campaignId = :campaignId AND c.orgId = :orgId ORDER BY cp.scheduledDate, cp.timeOfDay")
        List<CampaignCheckpoint> findByCampaignIdAndOrgId(@Param("campaignId") UUID campaignId,
                        @Param("orgId") UUID orgId);

        // Count checkpoints by campaign
        @Query("SELECT COUNT(cp) FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId")
        long countByCampaignId(@Param("campaignId") UUID campaignId);

        // Count checkpoints by campaign and status
        @Query("SELECT COUNT(cp) FROM CampaignCheckpoint cp WHERE cp.campaignId = :campaignId AND cp.status = :status")
        long countByCampaignIdAndStatus(@Param("campaignId") UUID campaignId,
                        @Param("status") CampaignCheckpoint.CheckpointStatus status);

        // Delete all checkpoints for a campaign (cascade will handle checkpoint leads)
        void deleteByCampaignId(UUID campaignId);

        // Find checkpoints by status and scheduled time (for scheduler)
        @Query("SELECT cp FROM CampaignCheckpoint cp WHERE cp.status = :status AND cp.timeOfDay <= :currentTime")
        List<CampaignCheckpoint> findByStatusAndScheduledAtBefore(
                        @Param("status") CampaignCheckpoint.CheckpointStatus status,
                        @Param("currentTime") LocalTime currentTime);

        // Count checkpoints by status
        long countByStatus(CampaignCheckpoint.CheckpointStatus status);
}
