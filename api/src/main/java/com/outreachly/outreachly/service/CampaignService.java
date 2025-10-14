package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.Campaign;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.CampaignLead;
import com.outreachly.outreachly.repository.CampaignRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.CampaignLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final LeadRepository leadRepository;
    private final CampaignLeadRepository campaignLeadRepository;

    /**
     * Create a new campaign
     */
    public Campaign createCampaign(UUID orgId, String name, String description) {
        log.info("Creating campaign '{}' for organization {}", name, orgId);

        Campaign campaign = Campaign.builder()
                .orgId(orgId)
                .name(name)
                .description(description)
                .status(Campaign.CampaignStatus.active)
                .build();

        Campaign savedCampaign = campaignRepository.save(campaign);
        log.info("Created campaign with ID: {}", savedCampaign.getId());

        return savedCampaign;
    }

    /**
     * Get all campaigns for an organization
     */
    @Transactional(readOnly = true)
    public List<Campaign> getAllCampaigns(UUID orgId) {
        log.debug("Fetching all campaigns for organization {}", orgId);
        return campaignRepository.findByOrgIdOrderByCreatedAtDescSimple(orgId);
    }

    /**
     * Get a specific campaign by ID
     */
    @Transactional(readOnly = true)
    public Optional<Campaign> getCampaign(UUID campaignId, UUID orgId) {
        log.debug("Fetching campaign {} for organization {}", campaignId, orgId);
        return campaignRepository.findByIdAndOrgId(campaignId, orgId);
    }

    /**
     * Update campaign details
     */
    public Campaign updateCampaign(UUID campaignId, UUID orgId, String name, String description,
            Campaign.CampaignStatus status) {
        log.info("Updating campaign {} for organization {}", campaignId, orgId);

        Campaign campaign = campaignRepository.findByIdAndOrgId(campaignId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        if (name != null) {
            campaign.setName(name);
        }
        if (description != null) {
            campaign.setDescription(description);
        }
        if (status != null) {
            campaign.setStatus(status);
        }

        Campaign updatedCampaign = campaignRepository.save(campaign);
        log.info("Updated campaign {}", campaignId);

        return updatedCampaign;
    }

    /**
     * Delete a campaign
     */
    public void deleteCampaign(UUID campaignId, UUID orgId) {
        log.info("Deleting campaign {} for organization {}", campaignId, orgId);

        Campaign campaign = campaignRepository.findByIdAndOrgId(campaignId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        campaignRepository.delete(campaign);
        log.info("Deleted campaign {}", campaignId);
    }

    /**
     * Add leads to a campaign
     */
    public void addLeadsToCampaign(UUID campaignId, UUID orgId, List<UUID> leadIds) {
        log.info("Adding {} leads to campaign {} for organization {}", leadIds.size(), campaignId, orgId);

        Campaign campaign = campaignRepository.findByIdAndOrgId(campaignId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        List<Lead> leads = leadRepository.findByIdInAndOrgId(leadIds, orgId);

        if (leads.size() != leadIds.size()) {
            log.warn("Some leads not found. Requested: {}, Found: {}", leadIds.size(), leads.size());
        }

        // Check for existing associations to avoid duplicates
        List<UUID> existingLeadIds = campaignLeadRepository.findByCampaignId(campaignId)
                .stream()
                .filter(cl -> cl.getStatus() != CampaignLead.CampaignLeadStatus.removed)
                .map(cl -> cl.getLead().getId())
                .collect(Collectors.toList());

        List<CampaignLead> newAssociations = leads.stream()
                .filter(lead -> !existingLeadIds.contains(lead.getId()))
                .map(lead -> CampaignLead.builder()
                        .campaign(campaign)
                        .lead(lead)
                        .status(CampaignLead.CampaignLeadStatus.active)
                        .addedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        campaignLeadRepository.saveAll(newAssociations);
        log.info("Added {} new leads to campaign {}", newAssociations.size(), campaignId);
    }

    /**
     * Remove leads from a campaign
     */
    public void removeLeadsFromCampaign(UUID campaignId, UUID orgId, List<UUID> leadIds) {
        log.info("Removing {} leads from campaign {} for organization {}", leadIds.size(), campaignId, orgId);

        // Verify campaign belongs to organization
        campaignRepository.findByIdAndOrgId(campaignId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        List<CampaignLead> associations = campaignLeadRepository.findByCampaignIdAndLeadIdIn(campaignId, leadIds);

        // Mark as removed instead of deleting for audit trail
        associations.forEach(association -> {
            association.setStatus(CampaignLead.CampaignLeadStatus.removed);
        });

        campaignLeadRepository.saveAll(associations);
        log.info("Removed {} leads from campaign {}", associations.size(), campaignId);
    }

    /**
     * Get all leads in a campaign
     */
    @Transactional(readOnly = true)
    public List<Lead> getCampaignLeads(UUID campaignId, UUID orgId) {
        log.debug("Fetching leads for campaign {} in organization {}", campaignId, orgId);

        // Verify campaign belongs to organization
        campaignRepository.findByIdAndOrgId(campaignId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        return campaignLeadRepository.findByCampaignId(campaignId)
                .stream()
                .filter(cl -> cl.getStatus() == CampaignLead.CampaignLeadStatus.active)
                .map(CampaignLead::getLead)
                .collect(Collectors.toList());
    }

    /**
     * Get campaign statistics
     */
    @Transactional(readOnly = true)
    public CampaignStats getCampaignStats(UUID campaignId, UUID orgId) {
        log.debug("Fetching stats for campaign {} in organization {}", campaignId, orgId);

        // Verify campaign belongs to organization
        campaignRepository.findByIdAndOrgId(campaignId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        long totalLeads = campaignLeadRepository.countByCampaignIdAndStatus(
                campaignId, CampaignLead.CampaignLeadStatus.active);

        // TODO: Implement actual email tracking stats
        // For now, return basic stats
        return CampaignStats.builder()
                .campaignId(campaignId)
                .totalLeads(totalLeads)
                .emailsSent(0L) // Will be implemented with checkpoint system
                .emailsDelivered(0L) // Will be implemented with delivery tracking
                .emailsFailed(0L) // Will be implemented with delivery tracking
                .build();
    }

    /**
     * Pause a campaign
     */
    public Campaign pauseCampaign(UUID campaignId, UUID orgId) {
        log.info("Pausing campaign {} for organization {}", campaignId, orgId);
        return updateCampaign(campaignId, orgId, null, null, Campaign.CampaignStatus.paused);
    }

    /**
     * Resume a campaign
     */
    public Campaign resumeCampaign(UUID campaignId, UUID orgId) {
        log.info("Resuming campaign {} for organization {}", campaignId, orgId);
        return updateCampaign(campaignId, orgId, null, null, Campaign.CampaignStatus.active);
    }

    /**
     * Complete a campaign
     */
    public Campaign completeCampaign(UUID campaignId, UUID orgId) {
        log.info("Completing campaign {} for organization {}", campaignId, orgId);
        return updateCampaign(campaignId, orgId, null, null, Campaign.CampaignStatus.completed);
    }

    /**
     * Campaign statistics DTO
     */
    public static class CampaignStats {
        private UUID campaignId;
        private long totalLeads;
        private long emailsSent;
        private long emailsDelivered;
        private long emailsFailed;

        public static CampaignStatsBuilder builder() {
            return new CampaignStatsBuilder();
        }

        // Getters
        public UUID getCampaignId() {
            return campaignId;
        }

        public long getTotalLeads() {
            return totalLeads;
        }

        public long getEmailsSent() {
            return emailsSent;
        }

        public long getEmailsDelivered() {
            return emailsDelivered;
        }

        public long getEmailsFailed() {
            return emailsFailed;
        }

        // Builder pattern
        public static class CampaignStatsBuilder {
            private UUID campaignId;
            private long totalLeads;
            private long emailsSent;
            private long emailsDelivered;
            private long emailsFailed;

            public CampaignStatsBuilder campaignId(UUID campaignId) {
                this.campaignId = campaignId;
                return this;
            }

            public CampaignStatsBuilder totalLeads(long totalLeads) {
                this.totalLeads = totalLeads;
                return this;
            }

            public CampaignStatsBuilder emailsSent(long emailsSent) {
                this.emailsSent = emailsSent;
                return this;
            }

            public CampaignStatsBuilder emailsDelivered(long emailsDelivered) {
                this.emailsDelivered = emailsDelivered;
                return this;
            }

            public CampaignStatsBuilder emailsFailed(long emailsFailed) {
                this.emailsFailed = emailsFailed;
                return this;
            }

            public CampaignStats build() {
                CampaignStats stats = new CampaignStats();
                stats.campaignId = this.campaignId;
                stats.totalLeads = this.totalLeads;
                stats.emailsSent = this.emailsSent;
                stats.emailsDelivered = this.emailsDelivered;
                stats.emailsFailed = this.emailsFailed;
                return stats;
            }
        }
    }
}
