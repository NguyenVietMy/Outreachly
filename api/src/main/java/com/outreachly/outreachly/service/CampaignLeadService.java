package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.CampaignLead;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.repository.CampaignLeadRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignLeadService {

    private final CampaignLeadRepository campaignLeadRepository;
    private final LeadRepository leadRepository;

    /**
     * Add a lead to a campaign
     */
    @Transactional
    public CampaignLead addLeadToCampaign(UUID campaignId, UUID leadId, Long addedBy) {
        log.debug("Attempting to add lead {} to campaign {} by user {}", leadId, campaignId, addedBy);

        // Check if relationship already exists
        Optional<CampaignLead> existingOpt = campaignLeadRepository.findByCampaignIdAndLeadId(campaignId, leadId);
        if (existingOpt.isPresent()) {
            CampaignLead existing = existingOpt.get();
            log.debug("Campaign-lead relationship already exists: campaign={}, lead={}, status={}",
                    campaignId, leadId, existing.getStatus());

            // If the relationship exists but is not active, reactivate it
            if (existing.getStatus() != CampaignLead.CampaignLeadStatus.active) {
                log.info("Reactivating existing campaign-lead relationship: campaign={}, lead={}, old_status={}",
                        campaignId, leadId, existing.getStatus());
                existing.setStatus(CampaignLead.CampaignLeadStatus.active);
                existing.setAddedBy(addedBy); // Update who added it
                CampaignLead reactivated = campaignLeadRepository.save(existing);
                log.info("Successfully reactivated lead {} in campaign {} by user {} - Relationship ID: {}",
                        leadId, campaignId, addedBy, reactivated.getId());
                return reactivated;
            } else {
                log.info("Lead {} is already active in campaign {} - Relationship ID: {}",
                        leadId, campaignId, existing.getId());
                return existing;
            }
        }

        // Create new relationship
        CampaignLead campaignLead = CampaignLead.builder()
                .campaignId(campaignId)
                .leadId(leadId)
                .addedBy(addedBy)
                .status(CampaignLead.CampaignLeadStatus.active)
                .build();

        CampaignLead saved = campaignLeadRepository.save(campaignLead);
        log.info("Successfully created new campaign-lead relationship: campaign={}, lead={}, user={}, ID={}",
                campaignId, leadId, addedBy, saved.getId());
        return saved;
    }

    /**
     * Remove a lead from a campaign (soft delete by setting status to removed)
     */
    @Transactional
    public void removeLeadFromCampaign(UUID campaignId, UUID leadId) {
        campaignLeadRepository.findByCampaignIdAndLeadId(campaignId, leadId)
                .ifPresentOrElse(
                        campaignLead -> {
                            campaignLead.setStatus(CampaignLead.CampaignLeadStatus.removed);
                            campaignLeadRepository.save(campaignLead);
                            log.info("Removed lead {} from campaign {}", leadId, campaignId);
                        },
                        () -> log.warn("Campaign-lead relationship not found: campaign={}, lead={}", campaignId,
                                leadId));
    }

    /**
     * Get all active leads for a campaign
     */
    public List<Lead> getActiveLeadsForCampaign(UUID campaignId) {
        log.debug("Fetching active leads for campaign: {}", campaignId);

        List<CampaignLead> campaignLeads = campaignLeadRepository.findActiveByCampaignId(campaignId);
        log.debug("Found {} campaign-lead relationships for campaign {}", campaignLeads.size(), campaignId);

        List<UUID> leadIds = campaignLeads.stream()
                .map(CampaignLead::getLeadId)
                .toList();

        log.debug("Lead IDs to fetch: {}", leadIds);

        // Fetch leads with eager loading using the repository
        List<Lead> leads = leadIds.stream()
                .map(leadId -> {
                    Optional<Lead> lead = leadRepository.findByIdWithCampaigns(leadId);
                    if (lead.isEmpty()) {
                        log.warn("Lead not found for ID: {}", leadId);
                    }
                    return lead;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        log.info("Successfully fetched {} leads for campaign {}", leads.size(), campaignId);
        return leads;
    }

    /**
     * Get all active campaigns for a lead
     */
    public List<CampaignLead> getActiveCampaignsForLead(UUID leadId) {
        return campaignLeadRepository.findActiveByLeadId(leadId);
    }

    /**
     * Check if a lead is in a campaign
     */
    public boolean isLeadInCampaign(UUID campaignId, UUID leadId) {
        return campaignLeadRepository.existsByCampaignIdAndLeadId(campaignId, leadId);
    }

    /**
     * Get campaign-lead relationship by IDs
     */
    public CampaignLead getCampaignLeadRelationship(UUID campaignId, UUID leadId) {
        return campaignLeadRepository.findByCampaignIdAndLeadId(campaignId, leadId)
                .orElse(null);
    }

    /**
     * Update the status of a campaign-lead relationship
     */
    @Transactional
    public CampaignLead updateCampaignLeadStatus(UUID campaignId, UUID leadId, CampaignLead.CampaignLeadStatus status) {
        CampaignLead campaignLead = campaignLeadRepository.findByCampaignIdAndLeadId(campaignId, leadId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign-lead relationship not found"));

        campaignLead.setStatus(status);
        return campaignLeadRepository.save(campaignLead);
    }

    /**
     * Add multiple leads to a campaign
     */
    @Transactional
    public int addLeadsToCampaign(UUID campaignId, List<UUID> leadIds, Long addedBy) {
        log.info("Starting bulk add operation - Campaign: {}, Lead count: {}, Added by: {}",
                campaignId, leadIds.size(), addedBy);

        int addedCount = 0;
        int reactivatedCount = 0;
        int alreadyActiveCount = 0;
        int errorCount = 0;

        for (UUID leadId : leadIds) {
            try {
                // Check current status before adding
                Optional<CampaignLead> existing = campaignLeadRepository.findByCampaignIdAndLeadId(campaignId, leadId);
                CampaignLead.CampaignLeadStatus previousStatus = existing.map(CampaignLead::getStatus).orElse(null);

                addLeadToCampaign(campaignId, leadId, addedBy);
                addedCount++;

                // Track what happened
                if (previousStatus == null) {
                    log.debug("Created new relationship for lead {}", leadId);
                } else if (previousStatus == CampaignLead.CampaignLeadStatus.active) {
                    alreadyActiveCount++;
                    log.debug("Lead {} was already active", leadId);
                } else {
                    reactivatedCount++;
                    log.debug("Reactivated lead {} from status {}", leadId, previousStatus);
                }
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to add lead {} to campaign {}: {}", leadId, campaignId, e.getMessage());
            }
        }

        log.info(
                "Bulk add operation completed - Campaign: {}, Total processed: {}, New: {}, Reactivated: {}, Already active: {}, Errors: {}",
                campaignId, leadIds.size(), addedCount - reactivatedCount - alreadyActiveCount, reactivatedCount,
                alreadyActiveCount, errorCount);

        return addedCount;
    }

    /**
     * Remove multiple leads from a campaign
     */
    @Transactional
    public int removeLeadsFromCampaign(UUID campaignId, List<UUID> leadIds) {
        int removedCount = 0;
        for (UUID leadId : leadIds) {
            try {
                removeLeadFromCampaign(campaignId, leadId);
                removedCount++;
            } catch (Exception e) {
                log.error("Failed to remove lead {} from campaign {}: {}", leadId, campaignId, e.getMessage());
            }
        }
        return removedCount;
    }

    /**
     * Get count of active leads in a campaign
     */
    public long getActiveLeadCountForCampaign(UUID campaignId) {
        return campaignLeadRepository.countActiveLeadsByCampaignId(campaignId);
    }

    /**
     * Get count of active campaigns for a lead
     */
    public long getActiveCampaignCountForLead(UUID leadId) {
        return campaignLeadRepository.countActiveCampaignsByLeadId(leadId);
    }
}
