package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.service.GmailService;
import com.outreachly.outreachly.service.LeadDataService;
import com.outreachly.outreachly.service.DeliveryTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
@Slf4j
public class GmailController {

    private final GmailService gmailService;
    private final LeadDataService leadDataService;
    private final DeliveryTrackingService deliveryTrackingService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmail(
            @RequestBody GmailRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate authentication
            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(401).body(response);
            }

            // Validate request
            if (request.getTo() == null || request.getTo().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Recipient email is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Subject is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Email body is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Get lead data for personalization
            Map<String, String> leadData;
            try {
                leadData = leadDataService.getLeadDataForEmail(
                        request.getTo(), authentication.getName());
            } catch (Exception e) {
                log.warn("Failed to fetch lead data for email: {}, using basic data", request.getTo(), e);
                // Fallback to basic data if lead service fails
                leadData = new HashMap<>();
                leadData.put("email", request.getTo());
                leadData.put("firstName", extractFirstNameFromEmail(request.getTo()));
            }

            // Process variables in email body if it contains variables
            String processedBody = processEmailVariables(request.getBody(), leadData);

            // Generate unique message ID for tracking
            String messageId = "gmail_" + System.currentTimeMillis() + "_" +
                    java.util.UUID.randomUUID().toString().substring(0, 8);

            // Send email via Gmail API
            try {
                gmailService.sendEmail(request.getTo(), request.getSubject(), processedBody,
                        request.isHtml(), request.getFrom());

                // Record successful delivery
                deliveryTrackingService.recordEmailDelivered(
                        messageId,
                        request.getTo(),
                        request.getCampaignId(),
                        getUserId(authentication),
                        getOrgId(authentication));

                response.put("success", true);
                response.put("message", "Email sent successfully via Gmail API");
                response.put("to", request.getTo());
                response.put("subject", request.getSubject());
                response.put("provider", "Gmail API");
                response.put("messageId", messageId);

                log.info("Gmail API email sent successfully to: {} by user: {}",
                        request.getTo(), authentication.getName());

            } catch (Exception gmailException) {
                // Record failed delivery
                deliveryTrackingService.recordEmailRejected(
                        messageId,
                        request.getTo(),
                        request.getCampaignId(),
                        getUserId(authentication),
                        getOrgId(authentication),
                        gmailException.getMessage());

                throw gmailException; // Re-throw to be caught by outer catch block
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send email via Gmail API to: {}", request.getTo(), e);

            response.put("success", false);
            response.put("message", "Failed to send email via Gmail API: " + e.getMessage());
            response.put("provider", "Gmail API");

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Process email variables like {{firstName}}, {{lastName}}, etc.
     */
    private String processEmailVariables(String emailBody, Map<String, String> leadData) {
        if (emailBody == null || leadData == null) {
            return emailBody;
        }

        String processedBody = emailBody;

        // Replace all variables in the format {{variableName}}
        for (Map.Entry<String, String> entry : leadData.entrySet()) {
            String variableName = entry.getKey();
            String variableValue = entry.getValue() != null ? entry.getValue() : "";

            // Replace {{variableName}} with the actual value
            String placeholder = "{{" + variableName + "}}";
            processedBody = processedBody.replace(placeholder, variableValue);

            // Also handle case-insensitive replacement
            String placeholderLower = "{{" + variableName.toLowerCase() + "}}";
            processedBody = processedBody.replace(placeholderLower, variableValue);
        }

        return processedBody;
    }

    /**
     * Extract first name from email address as fallback
     */
    private String extractFirstNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "there";
        }

        String localPart = email.split("@")[0];

        // Handle common patterns
        if (localPart.contains(".")) {
            String[] parts = localPart.split("\\.");
            if (parts.length > 0) {
                return capitalizeFirstLetter(parts[0]);
            }
        }

        if (localPart.contains("_")) {
            String[] parts = localPart.split("_");
            if (parts.length > 0) {
                return capitalizeFirstLetter(parts[0]);
            }
        }

        if (localPart.contains("-")) {
            String[] parts = localPart.split("-");
            if (parts.length > 0) {
                return capitalizeFirstLetter(parts[0]);
            }
        }

        // If no separators, use the whole local part
        return capitalizeFirstLetter(localPart);
    }

    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<Map<String, Object>> sendBulkEmails(
            @RequestBody BulkGmailRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate authentication
            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(401).body(response);
            }

            // Validate request
            if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
                response.put("success", false);
                response.put("message", "Recipients list is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Subject is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Email body is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Process bulk emails
            BulkEmailResult result = processBulkEmails(request, authentication.getName());

            response.put("success", true);
            response.put("message", "Bulk email processing completed");
            response.put("totalRecipients", result.getTotalRecipients());
            response.put("successfulSends", result.getSuccessfulSends());
            response.put("failedSends", result.getFailedSends());
            response.put("results", result.getResults());
            response.put("provider", "Gmail API");

            log.info("Bulk email processing completed - Total: {}, Successful: {}, Failed: {}",
                    result.getTotalRecipients(), result.getSuccessfulSends(), result.getFailedSends());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process bulk emails", e);

            response.put("success", false);
            response.put("message", "Failed to process bulk emails: " + e.getMessage());
            response.put("provider", "Gmail API");

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Process bulk emails with individual lead data for each recipient
     */
    private BulkEmailResult processBulkEmails(BulkGmailRequest request, String userEmail) {
        BulkEmailResult result = new BulkEmailResult();

        for (String recipient : request.getRecipients()) {
            try {
                // Get lead data for this specific recipient
                Map<String, String> leadData;
                try {
                    leadData = leadDataService.getLeadDataForEmail(recipient, userEmail);
                } catch (Exception e) {
                    log.warn("Failed to fetch lead data for email: {}, using basic data", recipient, e);
                    leadData = new HashMap<>();
                    leadData.put("email", recipient);
                    leadData.put("firstName", extractFirstNameFromEmail(recipient));
                }

                // Process variables in email body
                String processedBody = processEmailVariables(request.getBody(), leadData);

                // Generate unique message ID for tracking
                String messageId = "gmail_bulk_" + System.currentTimeMillis() + "_" +
                        java.util.UUID.randomUUID().toString().substring(0, 8);

                // Send individual email
                try {
                    gmailService.sendEmail(recipient, request.getSubject(), processedBody,
                            request.isHtml(), request.getFrom());

                    // Record successful delivery
                    deliveryTrackingService.recordEmailDelivered(
                            messageId,
                            recipient,
                            request.getCampaignId(),
                            request.getUserId(),
                            request.getOrgId());

                    // Record success
                    result.addSuccess(recipient, "Email sent successfully");

                } catch (Exception gmailException) {
                    // Record failed delivery
                    deliveryTrackingService.recordEmailRejected(
                            messageId,
                            recipient,
                            request.getCampaignId(),
                            request.getUserId(),
                            request.getOrgId(),
                            gmailException.getMessage());

                    throw gmailException; // Re-throw to be caught by outer catch block
                }

            } catch (Exception e) {
                log.error("Failed to send email to: {}", recipient, e);
                result.addFailure(recipient, "Failed to send email: " + e.getMessage());
            }
        }

        return result;
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testGmailConnection(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(401).body(response);
            }

            // First check if we have Gmail access
            if (!gmailService.hasGmailAccess()) {
                response.put("success", false);
                response.put("message", "Gmail API access not granted. Please re-authenticate with Gmail permissions.");
                response.put("requiresReauth", true);
                response.put("provider", "Gmail API");
                return ResponseEntity.status(403).body(response);
            }

            // Test with a simple email to the user's own email
            String userEmail = authentication.getName();
            gmailService.sendEmail(userEmail, "Gmail API Test",
                    "This is a test email to verify Gmail API integration is working correctly.\n\n" +
                            "If you receive this email, your Gmail API setup is working properly!",
                    false, null);

            response.put("success", true);
            response.put("message", "Gmail API test email sent successfully");
            response.put("testEmail", userEmail);
            response.put("provider", "Gmail API");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Gmail API test failed for user: {}", authentication.getName(), e);

            response.put("success", false);
            response.put("message", "Gmail API test failed: " + e.getMessage());
            response.put("provider", "Gmail API");

            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGmailStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(401).body(response);
            }

            // Check if user has Gmail API access
            boolean hasAccess = gmailService.hasGmailAccess();

            response.put("success", true);
            response.put("hasGmailAccess", hasAccess);
            response.put("provider", "Gmail API");
            response.put("user", authentication.getName());

            if (hasAccess) {
                response.put("message", "Gmail API access is available");
            } else {
                response.put("message", "Gmail API access not available - please re-authenticate");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to check Gmail API status for user: {}", authentication.getName(), e);

            response.put("success", false);
            response.put("message", "Failed to check Gmail API status: " + e.getMessage());
            response.put("provider", "Gmail API");

            return ResponseEntity.status(500).body(response);
        }
    }

    // Inner class for Gmail API request body
    public static class GmailRequest {
        private String to;
        private String subject;
        private String body;
        private boolean html = false;
        private String from;
        private String campaignId;
        private String userId;
        private String orgId;

        // Constructors
        public GmailRequest() {
        }

        public GmailRequest(String to, String subject, String body) {
            this.to = to;
            this.subject = subject;
            this.body = body;
        }

        // Getters and setters
        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public boolean isHtml() {
            return html;
        }

        public void setHtml(boolean html) {
            this.html = html;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getCampaignId() {
            return campaignId;
        }

        public void setCampaignId(String campaignId) {
            this.campaignId = campaignId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }
    }

    // Inner class for bulk Gmail API request body
    public static class BulkGmailRequest {
        private java.util.List<String> recipients;
        private String subject;
        private String body;
        private boolean html = false;
        private String from;
        private String campaignId;
        private String userId;
        private String orgId;

        // Constructors
        public BulkGmailRequest() {
        }

        public BulkGmailRequest(java.util.List<String> recipients, String subject, String body) {
            this.recipients = recipients;
            this.subject = subject;
            this.body = body;
        }

        // Getters and setters
        public java.util.List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(java.util.List<String> recipients) {
            this.recipients = recipients;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public boolean isHtml() {
            return html;
        }

        public void setHtml(boolean html) {
            this.html = html;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getCampaignId() {
            return campaignId;
        }

        public void setCampaignId(String campaignId) {
            this.campaignId = campaignId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }
    }

    // Inner class for bulk email result
    public static class BulkEmailResult {
        private int totalRecipients = 0;
        private int successfulSends = 0;
        private int failedSends = 0;
        private java.util.List<EmailResult> results = new java.util.ArrayList<>();

        public void addSuccess(String email, String message) {
            results.add(new EmailResult(email, true, message));
            successfulSends++;
            totalRecipients++;
        }

        public void addFailure(String email, String message) {
            results.add(new EmailResult(email, false, message));
            failedSends++;
            totalRecipients++;
        }

        // Getters
        public int getTotalRecipients() {
            return totalRecipients;
        }

        public int getSuccessfulSends() {
            return successfulSends;
        }

        public int getFailedSends() {
            return failedSends;
        }

        public java.util.List<EmailResult> getResults() {
            return results;
        }
    }

    // Inner class for individual email result
    public static class EmailResult {
        private String email;
        private boolean success;
        private String message;

        public EmailResult(String email, boolean success, String message) {
            this.email = email;
            this.success = success;
            this.message = message;
        }

        // Getters
        public String getEmail() {
            return email;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Extract user ID from authentication
     */
    private String getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        // Try to extract user ID from authentication principal
        // This might need adjustment based on your authentication setup
        try {
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User = (org.springframework.security.oauth2.core.user.OAuth2User) authentication
                        .getPrincipal();
                return oauth2User.getAttribute("sub"); // Google user ID
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from authentication", e);
        }

        return null;
    }

    /**
     * Extract organization ID from authentication
     */
    private String getOrgId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        // Try to extract org ID from authentication principal
        // This might need adjustment based on your authentication setup
        try {
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User = (org.springframework.security.oauth2.core.user.OAuth2User) authentication
                        .getPrincipal();
                return oauth2User.getAttribute("org_id"); // Organization ID if available
            }
        } catch (Exception e) {
            log.warn("Failed to extract org ID from authentication", e);
        }

        return null;
    }
}
