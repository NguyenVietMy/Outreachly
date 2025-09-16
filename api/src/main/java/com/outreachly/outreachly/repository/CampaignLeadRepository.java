package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.CampaignLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignLeadRepository extends JpaRepository<CampaignLead, UUID> {

    // Find all campaign-lead relationships for a specific campaign
    List<CampaignLead> findByCampaignId(UUID campaignId);

    // Find all campaign-lead relationships for a specific lead
    List<CampaignLead> findByLeadId(UUID leadId);

    // Find specific campaign-lead relationship
    Optional<CampaignLead> findByCampaignIdAndLeadId(UUID campaignId, UUID leadId);

    // Find active campaign-lead relationships for a campaign
    @Query("SELECT cl FROM CampaignLead cl LEFT JOIN FETCH cl.lead WHERE cl.campaignId = :campaignId AND cl.status = 'active'")
    List<CampaignLead> findActiveByCampaignId(@Param("campaignId") UUID campaignId);

    // Find active campaign-lead relationships for a lead
    @Query("SELECT cl FROM CampaignLead cl LEFT JOIN FETCH cl.campaign WHERE cl.leadId = :leadId AND cl.status = 'active'")
    List<CampaignLead> findActiveByLeadId(@Param("leadId") UUID leadId);

    // Check if a lead is already in a campaign
    boolean existsByCampaignIdAndLeadId(UUID campaignId, UUID leadId);

    // Count active leads in a campaign
    @Query("SELECT COUNT(cl) FROM CampaignLead cl WHERE cl.campaignId = :campaignId AND cl.status = 'active'")
    long countActiveLeadsByCampaignId(@Param("campaignId") UUID campaignId);

    // Count active campaigns for a lead
    @Query("SELECT COUNT(cl) FROM CampaignLead cl WHERE cl.leadId = :leadId AND cl.status = 'active'")
    long countActiveCampaignsByLeadId(@Param("leadId") UUID leadId);
}
