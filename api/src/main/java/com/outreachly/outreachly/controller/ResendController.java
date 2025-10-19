package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.OrganizationEmailService;
import com.outreachly.outreachly.service.UserService;
import com.outreachly.outreachly.service.email.EmailProviderType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for Resend-specific email operations
 * Provides backward compatibility for frontend using /api/resend/send
 */
@RestController
@RequestMapping("/api/resend")
@RequiredArgsConstructor
@Slf4j
public class ResendController {

    private final OrganizationEmailService emailService;
    private final UserService userService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmail(
            @Valid @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            UUID orgId = getOrgIdFromAuthentication(authentication);
            log.info("Sending email via Resend to {} recipients for user: {} in org: {}",
                    request.get("to"),
                    authentication.getName(),
                    orgId);

            // Convert the request to EmailRequest format
            EmailRequest emailRequest = convertToEmailRequest(request);

            // Send email using the organization service (which includes event tracking)
            EmailResponse response = emailService.sendEmail(orgId, emailRequest, EmailProviderType.RESEND);

            // Convert response back to the expected format
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.isSuccess());
            result.put("message", response.getMessage());
            result.put("messageId", response.getMessageId());
            result.put("timestamp", response.getTimestamp());
            result.put("provider", "Resend");

            if (response.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(500).body(result);
            }

        } catch (Exception e) {
            log.error("Failed to send email via Resend: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to send email via Resend: " + e.getMessage());
            errorResponse.put("provider", "Resend");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Convert the frontend request format to EmailRequest
     */
    private EmailRequest convertToEmailRequest(Map<String, Object> request) {
        EmailRequest emailRequest = new EmailRequest();

        // Handle recipients - could be string or array
        Object toObj = request.get("to");
        if (toObj instanceof String) {
            emailRequest.setRecipients(java.util.List.of((String) toObj));
        } else if (toObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<String> recipients = (java.util.List<String>) toObj;
            emailRequest.setRecipients(recipients);
        } else {
            emailRequest.setRecipients(java.util.List.of());
        }

        // Set other fields
        emailRequest.setSubject((String) request.get("subject"));
        emailRequest.setContent((String) request.get("body"));
        emailRequest.setHtml((Boolean) request.getOrDefault("html", true));
        emailRequest.setReplyTo((String) request.get("reply_to"));

        return emailRequest;
    }

    /**
     * Get organization ID from authentication
     */
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

    /**
     * Get user from authentication
     */
    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userService.findByEmail(authentication.getName());
    }
}
