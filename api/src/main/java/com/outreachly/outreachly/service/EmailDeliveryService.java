package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.CampaignCheckpoint;
import com.outreachly.outreachly.entity.CampaignCheckpointLead;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.Template;
import com.outreachly.outreachly.repository.CampaignCheckpointLeadRepository;
import com.outreachly.outreachly.repository.CampaignRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.TemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final GmailService gmailService;
    private final DeliveryTrackingService deliveryTrackingService;
    private final ObjectMapper objectMapper;
    private final CampaignRepository campaignRepository;

    /**
     * Send emails for a specific checkpoint
     */
    @Transactional
    public void sendCheckpointEmails(CampaignCheckpoint checkpoint) {
        // Get all leads for this checkpoint
        List<CampaignCheckpointLead> checkpointLeads = checkpointLeadRepository.findByCheckpointId(checkpoint.getId());

        if (checkpointLeads.isEmpty()) {
            log.warn("No leads found for checkpoint: {}", checkpoint.getName());
            return;
        }

        log.info("Sending emails to {} leads for checkpoint: {}", checkpointLeads.size(), checkpoint.getName());

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

        if (successCount > 0 || failureCount > 0) {
            log.info("Email delivery completed for checkpoint: {}. Success: {}, Failures: {}",
                    checkpoint.getName(), successCount, failureCount);
        }
    }

    /**
     * Send email to a specific lead using Gmail API
     */
    public void sendEmailToLead(Lead lead, Template template, CampaignCheckpoint checkpoint) {
        try {
            // Parse template JSON to get subject and body
            String subject = "No Subject";
            String body = "No content";
            boolean isHtml = false;

            if (template != null && template.getContentJson() != null) {
                JsonNode templateJson = objectMapper.readTree(template.getContentJson());
                subject = templateJson.has("subject") ? templateJson.get("subject").asText() : "No Subject";
                body = templateJson.has("body") ? templateJson.get("body").asText() : "No content";
                isHtml = templateJson.has("isHtml") ? templateJson.get("isHtml").asBoolean() : false;
            }

            // Create lead data map for personalization
            Map<String, String> leadData = createLeadDataMap(lead);

            // Personalize subject and body
            String personalizedSubject = personalizeContent(subject, leadData);
            String personalizedBody = personalizeContent(body, leadData);

            // Generate unique message ID for tracking
            String messageId = "campaign_" + checkpoint.getId().toString().substring(0, 8) +
                    "_" + System.currentTimeMillis() + "_" +
                    java.util.UUID.randomUUID().toString().substring(0, 8);

            // Get campaign creator for tracking
            String campaignCreatorId = null;
            try {
                campaignCreatorId = campaignRepository.findById(checkpoint.getCampaignId())
                        .map(campaign -> campaign.getCreatedBy().toString())
                        .orElse(null);
            } catch (Exception e) {
                log.warn("Failed to get campaign creator for tracking: {}", e.getMessage());
            }

            // Record SENT event before API call
            deliveryTrackingService.recordEmailDelivered(
                    messageId,
                    lead.getEmail(),
                    checkpoint.getCampaignId().toString(),
                    campaignCreatorId,
                    checkpoint.getOrgId().toString());

            // Send email via Gmail API using campaign creator's OAuth2 token
            Long campaignCreatorUserId = null;
            if (campaignCreatorId != null) {
                try {
                    campaignCreatorUserId = Long.parseLong(campaignCreatorId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid campaign creator ID format: {}", campaignCreatorId);
                }
            }

            gmailService.sendEmail(lead.getEmail(), personalizedSubject, personalizedBody, isHtml, null,
                    campaignCreatorUserId);

            log.debug("Email sent successfully to: {} with messageId: {}", lead.getEmail(), messageId);

        } catch (Exception e) {
            log.error("Failed to send email to: {}", lead.getEmail(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Create lead data map for personalization
     */
    private Map<String, String> createLeadDataMap(Lead lead) {
        Map<String, String> leadData = new HashMap<>();
        leadData.put("email", lead.getEmail() != null ? lead.getEmail() : "");
        leadData.put("firstName", lead.getFirstName() != null ? lead.getFirstName() : "");
        leadData.put("lastName", lead.getLastName() != null ? lead.getLastName() : "");
        leadData.put("position", lead.getPosition() != null ? lead.getPosition() : "");
        leadData.put("phone", lead.getPhone() != null ? lead.getPhone() : "");
        leadData.put("domain", lead.getDomain() != null ? lead.getDomain() : "");
        leadData.put("linkedinUrl", lead.getLinkedinUrl() != null ? lead.getLinkedinUrl() : "");
        leadData.put("twitter", lead.getTwitter() != null ? lead.getTwitter() : "");
        leadData.put("department", lead.getDepartment() != null ? lead.getDepartment() : "");
        leadData.put("seniority", lead.getSeniority() != null ? lead.getSeniority() : "");

        // Add computed fields
        leadData.put("fullName", (lead.getFirstName() + " " + lead.getLastName()).trim());
        leadData.put("first_name", lead.getFirstName() != null ? lead.getFirstName() : "");
        leadData.put("last_name", lead.getLastName() != null ? lead.getLastName() : "");

        return leadData;
    }

    /**
     * Personalize content by replacing variables like {{firstName}} with actual
     * values
     */
    private String personalizeContent(String content, Map<String, String> leadData) {
        if (content == null) {
            return "";
        }

        String result = content;
        for (Map.Entry<String, String> entry : leadData.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
        }

        return result;
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
