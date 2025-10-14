package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.CampaignCheckpoint;
import com.outreachly.outreachly.entity.CampaignCheckpointLead;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.Template;
import com.outreachly.outreachly.repository.CampaignCheckpointLeadRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for delivering emails through campaign checkpoints.
 * Handles email sending, error tracking, and delivery monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

    private final CampaignCheckpointLeadRepository checkpointLeadRepository;
    private final LeadRepository leadRepository;
    private final TemplateRepository templateRepository;

    /**
     * Send emails for a specific checkpoint
     */
    @Transactional
    public void sendCheckpointEmails(CampaignCheckpoint checkpoint) {
        log.info("Starting email delivery for checkpoint: {} (ID: {})",
                checkpoint.getName(), checkpoint.getId());

        // Get all leads for this checkpoint
        List<CampaignCheckpointLead> checkpointLeads = checkpointLeadRepository.findByCheckpointId(checkpoint.getId());

        if (checkpointLeads.isEmpty()) {
            log.warn("No leads found for checkpoint: {}", checkpoint.getName());
            return;
        }

        log.info("Found {} leads for checkpoint: {}", checkpointLeads.size(), checkpoint.getName());

        // Get email template if specified
        Template emailTemplate = null;
        if (checkpoint.getEmailTemplateId() != null) {
            emailTemplate = templateRepository.findById(checkpoint.getEmailTemplateId())
                    .orElse(null);

            if (emailTemplate == null) {
                log.warn("Email template not found for checkpoint: {} (Template ID: {})",
                        checkpoint.getName(), checkpoint.getEmailTemplateId());
            }
        }

        // Process each lead
        int successCount = 0;
        int failureCount = 0;

        for (CampaignCheckpointLead checkpointLead : checkpointLeads) {
            try {
                // Get lead details
                Lead lead = leadRepository.findById(checkpointLead.getLeadId())
                        .orElse(null);

                if (lead == null) {
                    log.warn("Lead not found: {}", checkpointLead.getLeadId());
                    markLeadAsFailed(checkpointLead, "Lead not found");
                    failureCount++;
                    continue;
                }

                // Send email
                sendEmailToLead(lead, emailTemplate, checkpoint);

                // Mark as sent
                markLeadAsSent(checkpointLead);
                successCount++;

                log.debug("Email sent successfully to: {}", lead.getEmail());

            } catch (Exception e) {
                log.error("Failed to send email to lead: {}", checkpointLead.getLeadId(), e);
                markLeadAsFailed(checkpointLead, e.getMessage());
                failureCount++;
            }
        }

        log.info("Email delivery completed for checkpoint: {}. Success: {}, Failures: {}",
                checkpoint.getName(), successCount, failureCount);
    }

    /**
     * Send email to a specific lead
     */
    private void sendEmailToLead(Lead lead, Template template, CampaignCheckpoint checkpoint) {
        // TODO: Implement actual email sending logic
        // This is where you would integrate with your email provider (SendGrid, SES,
        // etc.)

        log.info("Sending email to: {} using template: {}",
                lead.getEmail(),
                template != null ? template.getName() : "No template");

        // For now, simulate email sending
        simulateEmailSending(lead, template, checkpoint);
    }

    /**
     * Simulate email sending (replace with real implementation)
     */
    private void simulateEmailSending(Lead lead, Template template, CampaignCheckpoint checkpoint) {
        // Simulate API call delay
        try {
            Thread.sleep(100); // 100ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate occasional failures (5% failure rate)
        if (Math.random() < 0.05) {
            throw new RuntimeException("Simulated email sending failure");
        }

        log.debug("Simulated email sent to: {}", lead.getEmail());
    }

    /**
     * Mark a lead as successfully sent
     */
    @Transactional
    public void markLeadAsSent(CampaignCheckpointLead checkpointLead) {
        checkpointLead.setStatus(CampaignCheckpointLead.DeliveryStatus.sent);
        checkpointLead.setSentAt(LocalDateTime.now());
        checkpointLead.setUpdatedAt(LocalDateTime.now());
        checkpointLeadRepository.save(checkpointLead);
    }

    /**
     * Mark a lead as delivered (webhook from email provider)
     */
    @Transactional
    public void markLeadAsDelivered(UUID checkpointLeadId) {
        CampaignCheckpointLead checkpointLead = checkpointLeadRepository.findById(checkpointLeadId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint lead not found"));

        checkpointLead.setStatus(CampaignCheckpointLead.DeliveryStatus.delivered);
        checkpointLead.setUpdatedAt(LocalDateTime.now());
        checkpointLeadRepository.save(checkpointLead);

        log.info("Email delivered for lead: {}", checkpointLead.getLeadId());
    }

    /**
     * Mark a lead as failed to send
     */
    @Transactional
    public void markLeadAsFailed(CampaignCheckpointLead checkpointLead, String errorMessage) {
        checkpointLead.setStatus(CampaignCheckpointLead.DeliveryStatus.failed);
        checkpointLead.setErrorMessage(errorMessage);
        checkpointLead.setUpdatedAt(LocalDateTime.now());
        checkpointLeadRepository.save(checkpointLead);

        log.error("Email failed for lead: {} - {}", checkpointLead.getLeadId(), errorMessage);
    }

    /**
     * Retry sending emails for a specific checkpoint
     */
    @Transactional
    public void retryCheckpointEmails(UUID checkpointId) {
        // TODO: Implement proper checkpoint retrieval
        // For now, this is a placeholder - you'll need to inject
        // CampaignCheckpointRepository
        log.info("Retry functionality for checkpoint: {} - TODO: Implement", checkpointId);
    }

    /**
     * Get delivery statistics for a checkpoint
     */
    public DeliveryStats getCheckpointDeliveryStats(UUID checkpointId) {
        long totalLeads = checkpointLeadRepository.countByCheckpointId(checkpointId);
        long sentLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpointId, CampaignCheckpointLead.DeliveryStatus.sent);
        long deliveredLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpointId, CampaignCheckpointLead.DeliveryStatus.delivered);
        long failedLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpointId, CampaignCheckpointLead.DeliveryStatus.failed);

        double successRate = totalLeads > 0 ? (double) deliveredLeads / totalLeads * 100 : 0;
        double failureRate = totalLeads > 0 ? (double) failedLeads / totalLeads * 100 : 0;

        return DeliveryStats.builder()
                .checkpointId(checkpointId)
                .totalLeads(totalLeads)
                .sentLeads(sentLeads)
                .deliveredLeads(deliveredLeads)
                .failedLeads(failedLeads)
                .successRate(successRate)
                .failureRate(failureRate)
                .build();
    }

    /**
     * Delivery statistics for monitoring
     */
    @lombok.Data
    @lombok.Builder
    public static class DeliveryStats {
        private UUID checkpointId;
        private long totalLeads;
        private long sentLeads;
        private long deliveredLeads;
        private long failedLeads;
        private double successRate;
        private double failureRate;
    }
}
