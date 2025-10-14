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

    // RLS-compatible methods with org verification
    @Query("SELECT cl FROM CampaignLead cl JOIN cl.campaign c WHERE cl.campaignId = :campaignId AND c.orgId = :orgId")
    List<CampaignLead> findByCampaignIdAndOrgId(@Param("campaignId") UUID campaignId, @Param("orgId") UUID orgId);

    @Query("SELECT cl FROM CampaignLead cl JOIN cl.lead l WHERE cl.leadId = :leadId AND l.orgId = :orgId")
    List<CampaignLead> findByLeadIdAndOrgId(@Param("leadId") UUID leadId, @Param("orgId") UUID orgId);

    @Query("SELECT cl FROM CampaignLead cl JOIN cl.campaign c JOIN cl.lead l WHERE cl.campaignId = :campaignId AND cl.leadId = :leadId AND c.orgId = :orgId AND l.orgId = :orgId")
    Optional<CampaignLead> findByCampaignIdAndLeadIdAndOrgId(@Param("campaignId") UUID campaignId,
            @Param("leadId") UUID leadId, @Param("orgId") UUID orgId);

    // Find active campaign-lead relationships for a campaign
    @Query("SELECT cl FROM CampaignLead cl LEFT JOIN FETCH cl.lead WHERE cl.campaignId = :campaignId AND cl.status = 'active'")
    List<CampaignLead> findActiveByCampaignId(@Param("campaignId") UUID campaignId);

    // Find active campaign-lead relationships for a lead
    @Query("SELECT cl FROM CampaignLead cl LEFT JOIN FETCH cl.campaign WHERE cl.leadId = :leadId AND cl.status = 'active'")
    List<CampaignLead> findActiveByLeadId(@Param("leadId") UUID leadId);

    // RLS-compatible active methods with org verification
    @Query("SELECT cl FROM CampaignLead cl LEFT JOIN FETCH cl.lead JOIN cl.campaign c WHERE cl.campaignId = :campaignId AND cl.status = 'active' AND c.orgId = :orgId")
    List<CampaignLead> findActiveByCampaignIdAndOrgId(@Param("campaignId") UUID campaignId, @Param("orgId") UUID orgId);

    @Query("SELECT cl FROM CampaignLead cl LEFT JOIN FETCH cl.campaign JOIN cl.lead l WHERE cl.leadId = :leadId AND cl.status = 'active' AND l.orgId = :orgId")
    List<CampaignLead> findActiveByLeadIdAndOrgId(@Param("leadId") UUID leadId, @Param("orgId") UUID orgId);

    // Check if a lead is already in a campaign
    boolean existsByCampaignIdAndLeadId(UUID campaignId, UUID leadId);

    // Count active leads in a campaign
    @Query("SELECT COUNT(cl) FROM CampaignLead cl WHERE cl.campaignId = :campaignId AND cl.status = 'active'")
    long countActiveLeadsByCampaignId(@Param("campaignId") UUID campaignId);

    // Count active campaigns for a lead
    @Query("SELECT COUNT(cl) FROM CampaignLead cl WHERE cl.leadId = :leadId AND cl.status = 'active'")
    long countActiveCampaignsByLeadId(@Param("leadId") UUID leadId);

    // RLS-compatible count methods with org verification
    @Query("SELECT COUNT(cl) FROM CampaignLead cl JOIN cl.campaign c WHERE cl.campaignId = :campaignId AND cl.status = 'active' AND c.orgId = :orgId")
    long countActiveLeadsByCampaignIdAndOrgId(@Param("campaignId") UUID campaignId, @Param("orgId") UUID orgId);

    @Query("SELECT COUNT(cl) FROM CampaignLead cl JOIN cl.lead l WHERE cl.leadId = :leadId AND cl.status = 'active' AND l.orgId = :orgId")
    long countActiveCampaignsByLeadIdAndOrgId(@Param("leadId") UUID leadId, @Param("orgId") UUID orgId);

    // Additional methods needed by CampaignService
    @Query("SELECT cl FROM CampaignLead cl WHERE cl.campaignId = :campaignId AND cl.leadId IN :leadIds")
    List<CampaignLead> findByCampaignIdAndLeadIdIn(@Param("campaignId") UUID campaignId,
            @Param("leadIds") List<UUID> leadIds);

    @Query("SELECT COUNT(cl) FROM CampaignLead cl WHERE cl.campaignId = :campaignId AND cl.status = :status")
    long countByCampaignIdAndStatus(@Param("campaignId") UUID campaignId,
            @Param("status") CampaignLead.CampaignLeadStatus status);
}
