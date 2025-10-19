package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.OrganizationEmailService;
import com.outreachly.outreachly.service.RateLimitService;
import com.outreachly.outreachly.service.UserService;
import com.outreachly.outreachly.service.email.EmailProviderType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final OrganizationEmailService emailService;
    private final UserService userService;
    private final RateLimitService rateLimitService;
    // Removed unused CsvImportService field

    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(
            @Valid @RequestBody EmailRequest emailRequest,
            Authentication authentication) {

        UUID orgId = getOrgIdFromAuthentication(authentication);
        User user = getUser(authentication);

        // Check rate limit before sending
        if (!rateLimitService.canSendEmails(user.getId().toString(),
                user.getOrgId() != null ? user.getOrgId().toString() : null,
                emailRequest.getRecipients().size())) {
            return ResponseEntity.status(429).body(EmailResponse.builder()
                    .success(false)
                    .message("Rate limit exceeded. Please wait before sending more emails.")
                    .build());
        }

        log.info("Sending email to {} recipients for user: {} in org: {}",
                emailRequest.getRecipients().size(),
                authentication.getName(),
                orgId);

        EmailResponse response = emailService.sendEmail(orgId, emailRequest);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<EmailResponse> sendBulkEmail(
            @Valid @RequestBody List<EmailRequest> emailRequests,
            Authentication authentication) {

        UUID orgId = getOrgIdFromAuthentication(authentication);
        User user = getUser(authentication);

        // Calculate total recipients
        int totalRecipients = emailRequests.stream()
                .mapToInt(req -> req.getRecipients().size())
                .sum();

        // Check rate limit before sending
        if (!rateLimitService.canSendEmails(user.getId().toString(),
                user.getOrgId() != null ? user.getOrgId().toString() : null,
                totalRecipients)) {
            return ResponseEntity.status(429).body(EmailResponse.builder()
                    .success(false)
                    .message("Rate limit exceeded. Please wait before sending more emails.")
                    .build());
        }

        log.info("Sending bulk email with {} requests for user: {} in org: {}",
                emailRequests.size(),
                authentication.getName(),
                orgId);

        EmailResponse response = emailService.sendBulkEmail(orgId, emailRequests);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> getRateLimit(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        RateLimitService.RateLimitInfo rateLimitInfo = rateLimitService.getRateLimitInfo(
                user.getId().toString(),
                user.getOrgId() != null ? user.getOrgId().toString() : null);

        Map<String, Object> response = new HashMap<>();
        response.put("remaining", rateLimitInfo.getRemaining());
        response.put("limit", rateLimitInfo.getLimit());
        response.put("resetTime", rateLimitInfo.getResetTime());
        response.put("resetTimeSeconds", rateLimitInfo.getResetTimeSeconds());

        return ResponseEntity.ok(response);
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
        UUID orgId = getOrgIdFromAuthentication(authentication);
        log.info("Getting verified email addresses for user: {} in org: {}", authentication.getName(), orgId);

        List<String> verifiedEmails = emailService.getVerifiedEmailAddresses(orgId);

        return ResponseEntity.ok(verifiedEmails);
    }

    @GetMapping("/suppressed/{email}")
    public ResponseEntity<Boolean> isEmailSuppressed(
            @PathVariable String email,
            Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        log.info("Checking if email is suppressed: {} for user: {} in org: {}", email, authentication.getName(), orgId);

        boolean suppressed = emailService.isEmailSuppressed(orgId, email);

        return ResponseEntity.ok(suppressed);
    }

    @GetMapping("/history/{email}")
    public ResponseEntity<List<EmailEvent>> getEmailHistory(
            @PathVariable String email,
            Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        log.info("Getting email history for: {} for user: {} in org: {}", email, authentication.getName(), orgId);

        List<EmailEvent> history = emailService.getEmailHistory(orgId, email);

        return ResponseEntity.ok(history);
    }

    @GetMapping("/history")
    public ResponseEntity<List<EmailEvent>> getEmailHistory(Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        User user = getUser(authentication);
        log.info("Getting email history for user: {} in org: {}", authentication.getName(), orgId);

        List<EmailEvent> history = emailService.getEmailHistory(orgId, user.getId().toString());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEmailStats(Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        User user = getUser(authentication);
        log.info("Getting email stats for user: {} in org: {}", authentication.getName(), orgId);

        Map<String, Object> stats = emailService.getEmailStats(orgId, user.getId().toString());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders(Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        Map<String, Object> response = new HashMap<>();
        response.put("currentProvider", emailService.getOrganizationProviderType(orgId).getDisplayName());
        response.put("availableProviders", emailService.getProvidersInfo());
        response.put("healthStatus", emailService.getProvidersHealthStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/domains")
    public ResponseEntity<List<Map<String, Object>>> getRegisteredDomains(Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        User user = getUser(authentication);

        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        List<Map<String, Object>> domains = emailService.getRegisteredDomains(orgId, user.getId());
        return ResponseEntity.ok(domains);
    }

    @PostMapping("/send/{provider}")
    public ResponseEntity<EmailResponse> sendEmailWithProvider(
            @PathVariable String provider,
            @Valid @RequestBody EmailRequest emailRequest,
            Authentication authentication) {

        UUID orgId = getOrgIdFromAuthentication(authentication);
        try {
            EmailProviderType providerType = EmailProviderType.valueOf(provider.toUpperCase().replace("-", "_"));
            log.info("Sending email via {} to {} recipients for user: {} in org: {}",
                    providerType.getDisplayName(),
                    emailRequest.getRecipients().size(),
                    authentication.getName(),
                    orgId);

            EmailResponse response = emailService.sendEmail(orgId, emailRequest, providerType);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid provider: " + provider);
            errorResponse.put("availableProviders", emailService.getAllProviders().keySet());
            return ResponseEntity.badRequest().body(EmailResponse.builder()
                    .success(false)
                    .message("Invalid provider: " + provider)
                    .build());
        }
    }

    // Helper method to extract org ID from authentication
    private UUID getOrgIdFromAuthentication(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) {
            log.error("User not found for authentication: {}",
                    authentication != null ? authentication.getName() : null);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        if (user.getOrgId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Organization required");
        }
        return user.getOrgId();
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userService.findByEmail(authentication.getName());
    }
}
