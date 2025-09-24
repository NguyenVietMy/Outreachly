package com.outreachly.outreachly.service;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.EmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SesEmailService {

    private final SesClient sesClient;
    private final EmailEventService emailEventService;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    @Value("${aws.ses.from-name}")
    private String fromName;

    public EmailResponse sendEmail(EmailRequest emailRequest) {
        try {
            // Check for suppressed emails
            List<String> suppressedEmails = new ArrayList<>();
            List<String> validRecipients = new ArrayList<>();

            for (String recipient : emailRequest.getRecipients()) {
                if (emailEventService.isEmailSuppressed(recipient)) {
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

            // Build the email message
            Message message = Message.builder()
                    .subject(Content.builder()
                            .data(emailRequest.getSubject())
                            .charset("UTF-8")
                            .build())
                    .body(Body.builder()
                            .html(emailRequest.isHtml() ? Content.builder()
                                    .data(emailRequest.getContent())
                                    .charset("UTF-8")
                                    .build() : null)
                            .text(emailRequest.isHtml() ? null
                                    : Content.builder()
                                            .data(emailRequest.getContent())
                                            .charset("UTF-8")
                                            .build())
                            .build())
                    .build();

            // Build the destination
            Destination destination = Destination.builder()
                    .toAddresses(emailRequest.getRecipients())
                    .build();

            // Build the send email request
            SendEmailRequest sendEmailRequest = SendEmailRequest.builder()
                    .source(fromName + " <" + fromEmail + ">")
                    .destination(destination)
                    .message(message)
                    .replyToAddresses(
                            emailRequest.getReplyTo() != null ? List.of(emailRequest.getReplyTo()) : List.of())
                    .build();

            // Send the email
            SendEmailResponse response = sesClient.sendEmail(sendEmailRequest);

            log.info("Email sent successfully. MessageId: {}, Recipients: {}",
                    response.messageId(), emailRequest.getRecipients().size());

            return EmailResponse.builder()
                    .messageId(response.messageId())
                    .success(true)
                    .message("Email sent successfully")
                    .timestamp(LocalDateTime.now())
                    .totalRecipients(emailRequest.getRecipients().size() + suppressedEmails.size())
                    .successfulRecipients(emailRequest.getRecipients().size())
                    .failedRecipients(suppressedEmails)
                    .build();

        } catch (SesException e) {
            log.error("Failed to send email: {}", e.getMessage(), e);

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

    public EmailResponse sendBulkEmail(List<EmailRequest> emailRequests) {
        List<String> allRecipients = new ArrayList<>();
        List<String> failedRecipients = new ArrayList<>();
        int successfulCount = 0;

        for (EmailRequest emailRequest : emailRequests) {
            EmailResponse response = sendEmail(emailRequest);
            allRecipients.addAll(emailRequest.getRecipients());

            if (response.isSuccess()) {
                successfulCount += response.getSuccessfulRecipients();
            } else {
                failedRecipients.addAll(response.getFailedRecipients());
            }
        }

        return EmailResponse.builder()
                .success(failedRecipients.isEmpty())
                .message(failedRecipients.isEmpty() ? "All emails sent successfully" : "Some emails failed to send")
                .timestamp(LocalDateTime.now())
                .totalRecipients(allRecipients.size())
                .successfulRecipients(successfulCount)
                .failedRecipients(failedRecipients)
                .build();
    }

    public boolean verifyEmailAddress(String emailAddress) {
        try {
            VerifyEmailIdentityRequest request = VerifyEmailIdentityRequest.builder()
                    .emailAddress(emailAddress)
                    .build();

            sesClient.verifyEmailIdentity(request);
            log.info("Email verification request sent for: {}", emailAddress);
            return true;
        } catch (SesException e) {
            log.error("Failed to verify email address {}: {}", emailAddress, e.getMessage());
            return false;
        }
    }

    public List<String> getVerifiedEmailAddresses() {
        try {
            GetIdentityVerificationAttributesRequest request = GetIdentityVerificationAttributesRequest.builder()
                    .identities(List.of(fromEmail))
                    .build();

            GetIdentityVerificationAttributesResponse response = sesClient.getIdentityVerificationAttributes(request);

            return response.verificationAttributes().entrySet().stream()
                    .filter(entry -> entry.getValue().verificationStatus() == VerificationStatus.SUCCESS)
                    .map(entry -> entry.getKey())
                    .toList();
        } catch (SesException e) {
            log.error("Failed to get verified email addresses: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean isEmailSuppressed(String emailAddress) {
        return emailEventService.isEmailSuppressed(emailAddress);
    }

    public List<EmailEvent> getEmailHistory(String emailAddress) {
        return emailEventService.getEmailHistory(emailAddress);
    }
}
