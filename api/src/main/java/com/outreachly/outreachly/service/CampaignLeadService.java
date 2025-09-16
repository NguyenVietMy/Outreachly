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
        // Check if relationship already exists
        if (campaignLeadRepository.existsByCampaignIdAndLeadId(campaignId, leadId)) {
            log.debug("Campaign-lead relationship already exists: campaign={}, lead={}", campaignId, leadId);
            return campaignLeadRepository.findByCampaignIdAndLeadId(campaignId, leadId)
                    .orElseThrow(() -> new RuntimeException("Campaign-lead relationship not found"));
        }

        CampaignLead campaignLead = CampaignLead.builder()
                .campaignId(campaignId)
                .leadId(leadId)
                .addedBy(addedBy)
                .status(CampaignLead.CampaignLeadStatus.active)
                .build();

        CampaignLead saved = campaignLeadRepository.save(campaignLead);
        log.info("Added lead {} to campaign {}", leadId, campaignId);
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
        List<UUID> leadIds = campaignLeadRepository.findActiveByCampaignId(campaignId)
                .stream()
                .map(CampaignLead::getLeadId)
                .toList();

        // Fetch leads with eager loading using the repository
        return leadIds.stream()
                .map(leadId -> leadRepository.findByIdWithCampaigns(leadId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
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
        int addedCount = 0;
        for (UUID leadId : leadIds) {
            try {
                addLeadToCampaign(campaignId, leadId, addedBy);
                addedCount++;
            } catch (Exception e) {
                log.error("Failed to add lead {} to campaign {}: {}", leadId, campaignId, e.getMessage());
            }
        }
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
