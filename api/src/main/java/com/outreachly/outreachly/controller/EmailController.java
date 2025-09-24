package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.service.SesEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final SesEmailService emailService;

    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(
            @Valid @RequestBody EmailRequest emailRequest,
            Authentication authentication) {

        log.info("Sending email to {} recipients for user: {}",
                emailRequest.getRecipients().size(),
                authentication.getName());

        EmailResponse response = emailService.sendEmail(emailRequest);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<EmailResponse> sendBulkEmail(
            @Valid @RequestBody List<EmailRequest> emailRequests,
            Authentication authentication) {

        log.info("Sending bulk email with {} requests for user: {}",
                emailRequests.size(),
                authentication.getName());

        EmailResponse response = emailService.sendBulkEmail(emailRequests);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> getRateLimit(Authentication authentication) {
        // Simple rate limiting implementation
        // In production, you'd use Redis or similar for distributed rate limiting
        Map<String, Object> rateLimitInfo = new HashMap<>();
        rateLimitInfo.put("remaining", 100); // This would be calculated based on user's actual usage
        rateLimitInfo.put("resetTime", null);
        rateLimitInfo.put("limit", 100);

        return ResponseEntity.ok(rateLimitInfo);
    }

    @PostMapping("/verify/{email}")
    public ResponseEntity<Map<String, Object>> verifyEmailAddress(
            @PathVariable String email,
            Authentication authentication) {

        log.info("Verifying email address: {} for user: {}", email, authentication.getName());

        try {
            // In a real implementation, you would call AWS SES to verify the email
            // For now, we'll return a mock response
            Map<String, Object> response = new HashMap<>();
            response.put("email", email);
            response.put("verified", false);
            response.put("message",
                    "Email verification request sent. Please check your inbox and click the verification link.");
            response.put("requiresVerification", true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to verify email: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("email", email);
            errorResponse.put("verified", false);
            errorResponse.put("message", "Failed to send verification email: " + e.getMessage());
            errorResponse.put("requiresVerification", true);

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/verification-status")
    public ResponseEntity<Map<String, Object>> getVerificationStatus(Authentication authentication) {
        Map<String, Object> status = new HashMap<>();
        status.put("sandboxMode", true);
        status.put("message", "AWS SES is in sandbox mode. All email addresses must be verified before sending.");
        status.put("instructions",
                "To send emails to unverified addresses, request production access from AWS SES console.");

        return ResponseEntity.ok(status);
    }

    @GetMapping("/verified")
    public ResponseEntity<List<String>> getVerifiedEmailAddresses(Authentication authentication) {

        log.info("Getting verified email addresses for user: {}", authentication.getName());

        List<String> verifiedEmails = emailService.getVerifiedEmailAddresses();

        return ResponseEntity.ok(verifiedEmails);
    }

    @GetMapping("/suppressed/{email}")
    public ResponseEntity<Boolean> isEmailSuppressed(
            @PathVariable String email,
            Authentication authentication) {

        log.info("Checking if email is suppressed: {} for user: {}", email, authentication.getName());

        boolean suppressed = emailService.isEmailSuppressed(email);

        return ResponseEntity.ok(suppressed);
    }

    @GetMapping("/history/{email}")
    public ResponseEntity<List<EmailEvent>> getEmailHistory(
            @PathVariable String email,
            Authentication authentication) {

        log.info("Getting email history for: {} for user: {}", email, authentication.getName());

        List<EmailEvent> history = emailService.getEmailHistory(email);

        return ResponseEntity.ok(history);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Email service is healthy");
    }
}
