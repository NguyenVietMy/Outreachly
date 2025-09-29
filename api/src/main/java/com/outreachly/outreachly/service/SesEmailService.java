package com.outreachly.outreachly.service;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.service.email.AbstractEmailProvider;
import com.outreachly.outreachly.service.email.EmailProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SesEmailService extends AbstractEmailProvider {

    private final SesClient sesClient;
    private final EmailEventService emailEventService;

    public SesEmailService(SesClient sesClient, EmailEventService emailEventService) {
        super(emailEventService);
        this.sesClient = sesClient;
        this.emailEventService = emailEventService;
    }

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    @Value("${aws.ses.from-name}")
    private String fromName;

    @Override
    public EmailProviderType getProviderType() {
        return EmailProviderType.AWS_SES;
    }

    @Override
    protected EmailResponse doSendEmail(EmailRequest emailRequest) {
        try {
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

            return EmailResponse.builder()
                    .messageId(response.messageId())
                    .success(true)
                    .message("Email sent successfully")
                    .timestamp(LocalDateTime.now())
                    .totalRecipients(emailRequest.getRecipients().size())
                    .successfulRecipients(emailRequest.getRecipients().size())
                    .failedRecipients(new ArrayList<>())
                    .build();

        } catch (SesException e) {
            log.error("SES failed to send email: {}", e.getMessage(), e);

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

    @Override
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
}
