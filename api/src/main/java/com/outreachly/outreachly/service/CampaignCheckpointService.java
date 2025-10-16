package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.CampaignCheckpoint;
import com.outreachly.outreachly.entity.CampaignCheckpointLead;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.repository.CampaignRepository;
import com.outreachly.outreachly.repository.CampaignCheckpointRepository;
import com.outreachly.outreachly.repository.CampaignCheckpointLeadRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.CampaignLeadRepository;
import com.outreachly.outreachly.entity.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CampaignCheckpointService {

    private final CampaignCheckpointRepository checkpointRepository;
    private final CampaignCheckpointLeadRepository checkpointLeadRepository;
    private final CampaignRepository campaignRepository;
    private final LeadRepository leadRepository;
    private final TemplateService templateService;
    private final CampaignLeadRepository campaignLeadRepository;

    /**
     * Create a new campaign checkpoint
     */
    public CampaignCheckpoint createCheckpoint(UUID campaignId, UUID orgId, String name,
            LocalDate scheduledDate, LocalTime timeOfDay,
            UUID emailTemplateId, List<UUID> leadIds) {
        log.info("Creating checkpoint '{}' for campaign {} on {} at {}", name, campaignId, scheduledDate, timeOfDay);

        // Verify campaign belongs to organization
        campaignRepository.findByIdAndOrgId(campaignId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        // Verify email template belongs to organization (if provided)
        if (emailTemplateId != null) {
            Template template = templateService.getTemplate(orgId, emailTemplateId);
            if (template == null) {
                throw new IllegalArgumentException("Email template not found");
            }
            if (template.getPlatform() != Template.Platform.EMAIL) {
                throw new IllegalArgumentException("Template must be an EMAIL template");
            }
        }

        // Create the checkpoint
        CampaignCheckpoint checkpoint = CampaignCheckpoint.builder()
                .campaignId(campaignId)
                .orgId(orgId)
                .name(name)
                .scheduledDate(scheduledDate)
                .timeOfDay(timeOfDay)
                .emailTemplateId(emailTemplateId)
                .status(CampaignCheckpoint.CheckpointStatus.pending)
                .build();

        CampaignCheckpoint savedCheckpoint = checkpointRepository.save(checkpoint);
        log.info("Created checkpoint with ID: {}", savedCheckpoint.getId());

        // Add leads to the checkpoint if provided
        if (leadIds != null && !leadIds.isEmpty()) {
            addLeadsToCheckpoint(savedCheckpoint.getId(), orgId, leadIds);
        }

        return savedCheckpoint;
    }

    /**
     * Get all checkpoints for a campaign
     */
    @Transactional(readOnly = true)
    public List<CampaignCheckpoint> getCampaignCheckpoints(UUID campaignId, UUID orgId) {
        log.debug("Fetching checkpoints for campaign {} in organization {}", campaignId, orgId);
        return checkpointRepository.findByCampaignIdAndOrgId(campaignId, orgId);
    }

    /**
     * Get a specific checkpoint
     */
    @Transactional(readOnly = true)
    public Optional<CampaignCheckpoint> getCheckpoint(UUID checkpointId, UUID orgId) {
        log.debug("Fetching checkpoint {} for organization {}", checkpointId, orgId);
        return checkpointRepository.findByIdAndOrgId(checkpointId, orgId);
    }

    /**
     * Update checkpoint details
     */
    public CampaignCheckpoint updateCheckpoint(UUID checkpointId, UUID orgId, String name,
            LocalDate scheduledDate, LocalTime timeOfDay,
            UUID emailTemplateId, CampaignCheckpoint.CheckpointStatus status) {
        log.info("Updating checkpoint {} for organization {}", checkpointId, orgId);

        CampaignCheckpoint checkpoint = checkpointRepository.findByIdAndOrgId(checkpointId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        // Verify email template belongs to organization (if provided)
        if (emailTemplateId != null) {
            Template template = templateService.getTemplate(orgId, emailTemplateId);
            if (template == null) {
                throw new IllegalArgumentException("Email template not found");
            }
            if (template.getPlatform() != Template.Platform.EMAIL) {
                throw new IllegalArgumentException("Template must be an EMAIL template");
            }
        }

        if (name != null) {
            checkpoint.setName(name);
        }
        if (scheduledDate != null) {
            checkpoint.setScheduledDate(scheduledDate);
        }
        if (timeOfDay != null) {
            checkpoint.setTimeOfDay(timeOfDay);
        }
        if (emailTemplateId != null) {
            checkpoint.setEmailTemplateId(emailTemplateId);
        }
        if (status != null) {
            checkpoint.setStatus(status);
        }

        CampaignCheckpoint updatedCheckpoint = checkpointRepository.save(checkpoint);
        log.info("Updated checkpoint {}", checkpointId);

        return updatedCheckpoint;
    }

    /**
     * Delete a checkpoint
     */
    public void deleteCheckpoint(UUID checkpointId, UUID orgId) {
        log.info("Deleting checkpoint {} for organization {}", checkpointId, orgId);

        CampaignCheckpoint checkpoint = checkpointRepository.findByIdAndOrgId(checkpointId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        checkpointRepository.delete(checkpoint);
        log.info("Deleted checkpoint {}", checkpointId);
    }

    /**
     * Add leads to a checkpoint
     */
    public void addLeadsToCheckpoint(UUID checkpointId, UUID orgId, List<UUID> leadIds) {
        log.info("Adding {} leads to checkpoint {}", leadIds.size(), checkpointId);

        // Get checkpoint to access day and time for scheduling
        CampaignCheckpoint checkpoint = checkpointRepository.findByIdAndOrgId(checkpointId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        // Get campaign ID from checkpoint
        UUID campaignId = checkpoint.getCampaignId();

        // Find campaign leads for this campaign
        List<com.outreachly.outreachly.entity.CampaignLead> campaignLeads = campaignLeadRepository
                .findByCampaignIdAndOrgId(campaignId, orgId);

        // Filter to only the requested lead IDs
        List<Lead> leads = campaignLeads.stream()
                .filter(cl -> leadIds.contains(cl.getLeadId()))
                .map(com.outreachly.outreachly.entity.CampaignLead::getLead)
                .collect(Collectors.toList());

        if (leads.size() != leadIds.size()) {
            log.warn("Some leads not found. Requested: {}, Found: {}", leadIds.size(), leads.size());
        }

        // Check for existing associations to avoid duplicates
        List<UUID> existingLeadIds = checkpointLeadRepository.findByCheckpointId(checkpointId)
                .stream()
                .map(CampaignCheckpointLead::getLeadId)
                .collect(Collectors.toList());

        List<CampaignCheckpointLead> newAssociations = leads.stream()
                .filter(lead -> !existingLeadIds.contains(lead.getId()))
                .map(lead -> {
                    LocalDateTime scheduledAt = calculateScheduledTime(checkpoint.getScheduledDate(),
                            checkpoint.getTimeOfDay());
                    return CampaignCheckpointLead.builder()
                            .checkpointId(checkpointId)
                            .leadId(lead.getId())
                            .orgId(orgId)
                            .status(CampaignCheckpointLead.DeliveryStatus.pending)
                            .scheduledAt(scheduledAt)
                            .build();
                })
                .collect(Collectors.toList());

        checkpointLeadRepository.saveAll(newAssociations);
        log.info("Added {} new leads to checkpoint {}", newAssociations.size(), checkpointId);
    }

    /**
     * Remove leads from a checkpoint
     */
    public void removeLeadsFromCheckpoint(UUID checkpointId, UUID orgId, List<UUID> leadIds) {
        log.info("Removing {} leads from checkpoint {} for organization {}", leadIds.size(), checkpointId, orgId);

        // Verify checkpoint belongs to organization
        checkpointRepository.findByIdAndOrgId(checkpointId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        checkpointLeadRepository.deleteByCheckpointIdAndLeadIdIn(checkpointId, leadIds);
        log.info("Removed {} leads from checkpoint {}", leadIds.size(), checkpointId);
    }

    /**
     * Get all leads in a checkpoint
     */
    @Transactional(readOnly = true)
    public List<CampaignCheckpointLead> getCheckpointLeads(UUID checkpointId, UUID orgId) {
        log.debug("Fetching leads for checkpoint {} in organization {}", checkpointId, orgId);

        // Verify checkpoint belongs to organization
        checkpointRepository.findByIdAndOrgId(checkpointId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        return checkpointLeadRepository.findByCheckpointIdAndOrgId(checkpointId, orgId);
    }

    /**
     * Get checkpoint statistics
     */
    @Transactional(readOnly = true)
    public CheckpointStats getCheckpointStats(UUID checkpointId, UUID orgId) {
        log.debug("Fetching stats for checkpoint {} in organization {}", checkpointId, orgId);

        // Verify checkpoint belongs to organization
        checkpointRepository.findByIdAndOrgId(checkpointId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        long totalLeads = checkpointLeadRepository.countByCheckpointId(checkpointId);
        long pendingLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpointId, CampaignCheckpointLead.DeliveryStatus.pending);
        long sentLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpointId, CampaignCheckpointLead.DeliveryStatus.sent);
        long deliveredLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpointId, CampaignCheckpointLead.DeliveryStatus.delivered);
        long failedLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpointId, CampaignCheckpointLead.DeliveryStatus.failed);

        return CheckpointStats.builder()
                .checkpointId(checkpointId)
                .totalLeads(totalLeads)
                .pendingLeads(pendingLeads)
                .sentLeads(sentLeads)
                .deliveredLeads(deliveredLeads)
                .failedLeads(failedLeads)
                .build();
    }

    /**
     * Activate a checkpoint (start scheduling emails)
     */
    public CampaignCheckpoint activateCheckpoint(UUID checkpointId, UUID orgId) {
        log.info("Activating checkpoint {} for organization {}", checkpointId, orgId);
        return updateCheckpoint(checkpointId, orgId, null, null, null, null,
                CampaignCheckpoint.CheckpointStatus.active);
    }

    /**
     * Pause a checkpoint
     */
    public CampaignCheckpoint pauseCheckpoint(UUID checkpointId, UUID orgId) {
        log.info("Pausing checkpoint {} for organization {}", checkpointId, orgId);
        return updateCheckpoint(checkpointId, orgId, null, null, null, null,
                CampaignCheckpoint.CheckpointStatus.paused);
    }

    /**
     * Complete a checkpoint
     */
    public CampaignCheckpoint completeCheckpoint(UUID checkpointId, UUID orgId) {
        log.info("Completing checkpoint {} for organization {}", checkpointId, orgId);
        return updateCheckpoint(checkpointId, orgId, null, null, null, null,
                CampaignCheckpoint.CheckpointStatus.completed);
    }

    /**
     * Get checkpoints ready to be processed (for scheduling service)
     */
    @Transactional(readOnly = true)
    public List<CampaignCheckpointLead> getReadyToSendLeads() {
        LocalDateTime now = LocalDateTime.now();
        return checkpointLeadRepository.findReadyToSend(now);
    }

    /**
     * Mark a checkpoint lead as sent
     */
    public void markLeadAsSent(UUID checkpointLeadId, UUID orgId) {
        log.debug("Marking checkpoint lead {} as sent for organization {}", checkpointLeadId, orgId);

        CampaignCheckpointLead checkpointLead = checkpointLeadRepository.findById(checkpointLeadId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint lead not found"));

        // Verify organization ownership
        if (!checkpointLead.getOrgId().equals(orgId)) {
            throw new IllegalArgumentException("Checkpoint lead not found");
        }

        checkpointLead.markAsSent();
        checkpointLeadRepository.save(checkpointLead);
    }

    /**
     * Mark a checkpoint lead as delivered
     */
    public void markLeadAsDelivered(UUID checkpointLeadId, UUID orgId) {
        log.debug("Marking checkpoint lead {} as delivered for organization {}", checkpointLeadId, orgId);

        CampaignCheckpointLead checkpointLead = checkpointLeadRepository.findById(checkpointLeadId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint lead not found"));

        // Verify organization ownership
        if (!checkpointLead.getOrgId().equals(orgId)) {
            throw new IllegalArgumentException("Checkpoint lead not found");
        }

        checkpointLead.markAsDelivered();
        checkpointLeadRepository.save(checkpointLead);
    }

    /**
     * Mark a checkpoint lead as failed
     */
    public void markLeadAsFailed(UUID checkpointLeadId, UUID orgId, String errorMessage) {
        log.debug("Marking checkpoint lead {} as failed for organization {}: {}", checkpointLeadId, orgId,
                errorMessage);

        CampaignCheckpointLead checkpointLead = checkpointLeadRepository.findById(checkpointLeadId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint lead not found"));

        // Verify organization ownership
        if (!checkpointLead.getOrgId().equals(orgId)) {
            throw new IllegalArgumentException("Checkpoint lead not found");
        }

        checkpointLead.markAsFailed(errorMessage);
        checkpointLeadRepository.save(checkpointLead);
    }

    /**
     * Calculate the scheduled time for a given date and time
     */
    private LocalDateTime calculateScheduledTime(LocalDate scheduledDate, LocalTime timeOfDay) {
        return scheduledDate.atTime(timeOfDay);
    }

    /**
     * Checkpoint statistics DTO
     */
    public static class CheckpointStats {
        private UUID checkpointId;
        private long totalLeads;
        private long pendingLeads;
        private long sentLeads;
        private long deliveredLeads;
        private long failedLeads;

        public static CheckpointStatsBuilder builder() {
            return new CheckpointStatsBuilder();
        }

        // Getters
        public UUID getCheckpointId() {
            return checkpointId;
        }

        public long getTotalLeads() {
            return totalLeads;
        }

        public long getPendingLeads() {
            return pendingLeads;
        }

        public long getSentLeads() {
            return sentLeads;
        }

        public long getDeliveredLeads() {
            return deliveredLeads;
        }

        public long getFailedLeads() {
            return failedLeads;
        }

        // Calculated properties
        public double getSuccessRate() {
            if (totalLeads == 0)
                return 0.0;
            return ((double) (sentLeads + deliveredLeads) / totalLeads) * 100.0;
        }

        public double getFailureRate() {
            if (totalLeads == 0)
                return 0.0;
            return ((double) failedLeads / totalLeads) * 100.0;
        }

        // Builder pattern
        public static class CheckpointStatsBuilder {
            private UUID checkpointId;
            private long totalLeads;
            private long pendingLeads;
            private long sentLeads;
            private long deliveredLeads;
            private long failedLeads;

            public CheckpointStatsBuilder checkpointId(UUID checkpointId) {
                this.checkpointId = checkpointId;
                return this;
            }

            public CheckpointStatsBuilder totalLeads(long totalLeads) {
                this.totalLeads = totalLeads;
                return this;
            }

            public CheckpointStatsBuilder pendingLeads(long pendingLeads) {
                this.pendingLeads = pendingLeads;
                return this;
            }

            public CheckpointStatsBuilder sentLeads(long sentLeads) {
                this.sentLeads = sentLeads;
                return this;
            }

            public CheckpointStatsBuilder deliveredLeads(long deliveredLeads) {
                this.deliveredLeads = deliveredLeads;
                return this;
            }

            public CheckpointStatsBuilder failedLeads(long failedLeads) {
                this.failedLeads = failedLeads;
                return this;
            }

            public CheckpointStats build() {
                CheckpointStats stats = new CheckpointStats();
                stats.checkpointId = this.checkpointId;
                stats.totalLeads = this.totalLeads;
                stats.pendingLeads = this.pendingLeads;
                stats.sentLeads = this.sentLeads;
                stats.deliveredLeads = this.deliveredLeads;
                stats.failedLeads = this.failedLeads;
                return stats;
            }
        }
    }
}
