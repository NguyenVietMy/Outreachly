package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.service.GmailService;
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

            // Send email via Gmail API
            gmailService.sendEmail(request.getTo(), request.getSubject(), request.getBody(),
                    request.isHtml(), request.getFrom());

            response.put("success", true);
            response.put("message", "Email sent successfully via Gmail API");
            response.put("to", request.getTo());
            response.put("subject", request.getSubject());
            response.put("provider", "Gmail API");

            log.info("Gmail API email sent successfully to: {} by user: {}",
                    request.getTo(), authentication.getName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send email via Gmail API to: {}", request.getTo(), e);

            response.put("success", false);
            response.put("message", "Failed to send email via Gmail API: " + e.getMessage());
            response.put("provider", "Gmail API");

            return ResponseEntity.status(500).body(response);
        }
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
    }
}
