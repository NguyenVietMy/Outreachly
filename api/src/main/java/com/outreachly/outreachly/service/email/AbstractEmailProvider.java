package com.outreachly.outreachly.service.email;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.service.EmailEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for email providers that provides common functionality
 * like suppression checking and email event tracking
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractEmailProvider implements EmailProvider {

    protected final EmailEventService emailEventService;

    @Override
    public EmailResponse sendEmail(EmailRequest emailRequest) {
        try {
            // Check for suppressed emails
            List<String> suppressedEmails = new ArrayList<>();
            List<String> validRecipients = new ArrayList<>();

            for (String recipient : emailRequest.getRecipients()) {
                if (isEmailSuppressed(recipient)) {
                    suppressedEmails.add(recipient);
                    log.warn("Email suppressed due to previous bounces/complaints: {}", recipient);
                } else {
                    validRecipients.add(recipient);
                }
            }

            if (validRecipients.isEmpty()) {
                return EmailResponse.builder()
                        .success(false)
                        .message("All recipients are suppressed")
                        .timestamp(LocalDateTime.now())
                        .totalRecipients(emailRequest.getRecipients().size())
                        .successfulRecipients(0)
                        .failedRecipients(suppressedEmails)
                        .build();
            }

            // Update email request to only include valid recipients
            emailRequest.setRecipients(validRecipients);

            // Delegate to provider-specific implementation
            EmailResponse response = doSendEmail(emailRequest);

            // Log the result
            if (response.isSuccess()) {
                log.info("Email sent successfully via {}. MessageId: {}, Recipients: {}",
                        getProviderType().getDisplayName(), response.getMessageId(),
                        emailRequest.getRecipients().size());
            } else {
                log.error("Failed to send email via {}: {}",
                        getProviderType().getDisplayName(), response.getMessage());
            }

            return response;

        } catch (Exception e) {
            log.error("Unexpected error sending email via {}: {}",
                    getProviderType().getDisplayName(), e.getMessage(), e);

            return EmailResponse.builder()
                    .success(false)
                    .message("Failed to send email: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .totalRecipients(emailRequest.getRecipients().size())
                    .successfulRecipients(0)
                    .failedRecipients(emailRequest.getRecipients())
                    .build();
        }
    }

    @Override
    public EmailResponse sendBulkEmail(List<EmailRequest> emailRequests) {
        List<String> allRecipients = new ArrayList<>();
        List<String> failedRecipients = new ArrayList<>();
        int successfulCount = 0;

        log.info("Starting bulk email send via {} for {} requests",
                getProviderType().getDisplayName(), emailRequests.size());

        for (EmailRequest emailRequest : emailRequests) {
            EmailResponse response = sendEmail(emailRequest);
            allRecipients.addAll(emailRequest.getRecipients());

            if (response.isSuccess()) {
                successfulCount += response.getSuccessfulRecipients();
            } else {
                failedRecipients.addAll(response.getFailedRecipients());
            }
        }

        log.info("Bulk email send completed via {}. Success: {}, Failed: {}",
                getProviderType().getDisplayName(), successfulCount, failedRecipients.size());

        return EmailResponse.builder()
                .success(failedRecipients.isEmpty())
                .message(failedRecipients.isEmpty() ? "All emails sent successfully" : "Some emails failed to send")
                .timestamp(LocalDateTime.now())
                .totalRecipients(allRecipients.size())
                .successfulRecipients(successfulCount)
                .failedRecipients(failedRecipients)
                .build();
    }

    @Override
    public boolean isEmailSuppressed(String emailAddress) {
        return emailEventService.isEmailSuppressed(emailAddress);
    }

    @Override
    public List<EmailEvent> getEmailHistory(String emailAddress) {
        return emailEventService.getEmailHistory(emailAddress);
    }

    @Override
    public boolean isHealthy() {
        try {
            // Basic health check - try to get verified addresses
            getVerifiedEmailAddresses();
            return true;
        } catch (Exception e) {
            log.warn("Email provider {} health check failed: {}",
                    getProviderType().getDisplayName(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderInfo() {
        return String.format("%s (%s) - Healthy: %s",
                getProviderType().getDisplayName(),
                getProviderType().getDescription(),
                isHealthy());
    }

    /**
     * Provider-specific email sending implementation
     * 
     * @param emailRequest The email request to send
     * @return EmailResponse with the result
     */
    protected abstract EmailResponse doSendEmail(EmailRequest emailRequest);
}
