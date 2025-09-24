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

import java.util.List;

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

    @PostMapping("/verify/{email}")
    public ResponseEntity<Boolean> verifyEmailAddress(
            @PathVariable String email,
            Authentication authentication) {

        log.info("Verifying email address: {} for user: {}", email, authentication.getName());

        boolean success = emailService.verifyEmailAddress(email);

        return ResponseEntity.ok(success);
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
