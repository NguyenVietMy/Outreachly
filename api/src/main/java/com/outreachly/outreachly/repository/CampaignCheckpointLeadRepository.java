package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.CampaignCheckpointLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignCheckpointLeadRepository extends JpaRepository<CampaignCheckpointLead, UUID> {

    // Find all checkpoint leads for a specific checkpoint
    List<CampaignCheckpointLead> findByCheckpointId(UUID checkpointId);

    // Find all checkpoint leads for a specific lead
    List<CampaignCheckpointLead> findByLeadId(UUID leadId);

    // Find checkpoint leads by status
    @Query("SELECT ccl FROM CampaignCheckpointLead ccl WHERE ccl.checkpointId = :checkpointId AND ccl.status = :status")
    List<CampaignCheckpointLead> findByCheckpointIdAndStatus(@Param("checkpointId") UUID checkpointId,
            @Param("status") CampaignCheckpointLead.DeliveryStatus status);

    // Find specific checkpoint lead
    Optional<CampaignCheckpointLead> findByCheckpointIdAndLeadId(UUID checkpointId, UUID leadId);

    // Find checkpoint leads scheduled for a specific time range (for processing)
    @Query("SELECT ccl FROM CampaignCheckpointLead ccl WHERE ccl.status = 'pending' AND ccl.scheduledAt <= :scheduledBefore ORDER BY ccl.scheduledAt")
    List<CampaignCheckpointLead> findPendingLeadsScheduledBefore(
            @Param("scheduledBefore") LocalDateTime scheduledBefore);

    // Find checkpoint leads that need to be sent now
    @Query("SELECT ccl FROM CampaignCheckpointLead ccl JOIN ccl.checkpoint cp WHERE ccl.status = 'pending' AND ccl.scheduledAt <= :now AND cp.status = 'active' ORDER BY ccl.scheduledAt")
    List<CampaignCheckpointLead> findReadyToSend(@Param("now") LocalDateTime now);

    // Count checkpoint leads by status for a checkpoint
    @Query("SELECT COUNT(ccl) FROM CampaignCheckpointLead ccl WHERE ccl.checkpointId = :checkpointId AND ccl.status = :status")
    long countByCheckpointIdAndStatus(@Param("checkpointId") UUID checkpointId,
            @Param("status") CampaignCheckpointLead.DeliveryStatus status);

    // Count total checkpoint leads for a checkpoint
    long countByCheckpointId(UUID checkpointId);

    // Find checkpoint leads with organization verification
    @Query("SELECT ccl FROM CampaignCheckpointLead ccl JOIN ccl.checkpoint cp JOIN cp.campaign c WHERE ccl.checkpointId = :checkpointId AND c.orgId = :orgId")
    List<CampaignCheckpointLead> findByCheckpointIdAndOrgId(@Param("checkpointId") UUID checkpointId,
            @Param("orgId") UUID orgId);

    // Find checkpoint leads for a campaign (across all checkpoints)
    @Query("SELECT ccl FROM CampaignCheckpointLead ccl JOIN ccl.checkpoint cp WHERE cp.campaignId = :campaignId")
    List<CampaignCheckpointLead> findByCampaignId(@Param("campaignId") UUID campaignId);

    // Find failed checkpoint leads for retry
    @Query("SELECT ccl FROM CampaignCheckpointLead ccl WHERE ccl.status = 'failed' AND ccl.sentAt >= :since ORDER BY ccl.sentAt DESC")
    List<CampaignCheckpointLead> findFailedLeadsSince(@Param("since") LocalDateTime since);

    // Delete checkpoint leads for a specific checkpoint
    void deleteByCheckpointId(UUID checkpointId);

    // Delete checkpoint leads for specific leads in a checkpoint
    void deleteByCheckpointIdAndLeadIdIn(UUID checkpointId, List<UUID> leadIds);
}
