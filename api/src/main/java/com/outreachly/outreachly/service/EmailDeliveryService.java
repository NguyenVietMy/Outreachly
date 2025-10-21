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
    private final OrganizationEmailService organizationEmailService;
    private final RateLimitService rateLimitService;
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

        // Check rate limit before sending emails
        String campaignCreatorId = getCampaignCreatorId(checkpoint);
        if (!rateLimitService.canSendEmails(campaignCreatorId, checkpoint.getOrgId().toString(),
                checkpointLeads.size())) {
            log.warn("🚫 Rate limit exceeded for checkpoint: {} - {} emails requested, but quota exceeded",
                    checkpoint.getName(), checkpointLeads.size());

            // Mark checkpoint as paused due to rate limit
            checkpoint.setStatus(CampaignCheckpoint.CheckpointStatus.paused);
            campaignRepository.save(checkpoint.getCampaign());

            throw new RuntimeException(
                    "Rate limit exceeded. Checkpoint paused. Please wait before sending more emails.");
        }

        log.info("✅ Rate limit check passed for checkpoint: {} - {} emails can be sent",
                checkpoint.getName(), checkpointLeads.size());

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

        // Process each lead with rate limiting
        int successCount = 0;
        int failureCount = 0;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < checkpointLeads.size(); i++) {
            CampaignCheckpointLead checkpointLead = checkpointLeads.get(i);

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

                log.debug("Email sent successfully to: {} ({}/{})", lead.getEmail(), i + 1, checkpointLeads.size());

                // Rate limiting: Ensure we don't exceed 1 request per second for Resend
                if (i < checkpointLeads.size() - 1) { // Don't delay after the last email
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long expectedTime = (i + 1) * 1000; // 1000ms per request = 1 request per second

                    if (elapsedTime < expectedTime) {
                        long delayNeeded = expectedTime - elapsedTime;
                        Thread.sleep(delayNeeded);
                    }
                }

            } catch (Exception e) {
                log.error("Failed to send email to lead: {}", checkpointLead.getLeadId(), e);
                markLeadAsFailed(checkpointLead, e.getMessage());
                failureCount++;

                // If it's a rate limit error, add extra delay before continuing
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    log.warn("Rate limit hit, adding extra delay before continuing...");
                    try {
                        Thread.sleep(1000); // Wait 1 second before continuing
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted while waiting for rate limit", ie);
                    }
                }
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
            String campaignCreatorId = getCampaignCreatorId(checkpoint);

            // Record SENT event before API call
            deliveryTrackingService.recordEmailDelivered(
                    messageId,
                    lead.getEmail(),
                    checkpoint.getCampaignId().toString(),
                    campaignCreatorId,
                    checkpoint.getOrgId().toString());

            // Send email using the configured provider
            if (checkpoint.getEmailProvider() == CampaignCheckpoint.EmailProvider.GMAIL) {
                // Send via Gmail API using campaign creator's OAuth2 token
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
            } else {
                // Send via OrganizationEmailService (SES, Resend, etc.)
                com.outreachly.outreachly.dto.EmailRequest emailRequest = new com.outreachly.outreachly.dto.EmailRequest();
                emailRequest.setSubject(personalizedSubject);
                emailRequest.setContent(personalizedBody);
                emailRequest.setRecipients(List.of(lead.getEmail()));
                emailRequest.setHtml(isHtml);
                emailRequest.setCampaignId(checkpoint.getCampaignId().toString());

                // Convert checkpoint email provider to EmailProviderType
                com.outreachly.outreachly.service.email.EmailProviderType providerType = checkpoint
                        .getEmailProvider() == CampaignCheckpoint.EmailProvider.RESEND
                                ? com.outreachly.outreachly.service.email.EmailProviderType.RESEND
                                : com.outreachly.outreachly.service.email.EmailProviderType.AWS_SES;

                organizationEmailService.sendEmail(checkpoint.getOrgId(), emailRequest, providerType);
            }

            log.debug("Email sent successfully to: {} with messageId: {} via provider: {}",
                    lead.getEmail(), messageId, checkpoint.getEmailProvider());

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
     * Get campaign creator ID for rate limiting
     */
    private String getCampaignCreatorId(CampaignCheckpoint checkpoint) {
        try {
            return campaignRepository.findById(checkpoint.getCampaignId())
                    .map(campaign -> campaign.getCreatedBy().toString())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to get campaign creator for rate limiting: {}", e.getMessage());
            return null;
        }
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
