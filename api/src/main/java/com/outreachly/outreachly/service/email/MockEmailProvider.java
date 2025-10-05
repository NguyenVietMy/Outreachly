package com.outreachly.outreachly.service.email;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.service.EmailEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mock email provider for testing and development
 * Simulates email sending without actually sending emails
 */
@Service
@Slf4j
public class MockEmailProvider extends AbstractEmailProvider {

    public MockEmailProvider(EmailEventService emailEventService) {
        super(emailEventService);
    }

    @Override
    public EmailProviderType getProviderType() {
        return EmailProviderType.MOCK;
    }

    @Override
    protected EmailResponse doSendEmail(EmailRequest emailRequest) {
        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate occasional failures (5% failure rate)
        boolean shouldFail = Math.random() < 0.05;

        if (shouldFail) {
            log.warn("Mock provider simulating email send failure");
            return EmailResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .success(false)
                    .message("Mock email send failed (simulated)")
                    .timestamp(LocalDateTime.now())
                    .totalRecipients(emailRequest.getRecipients().size())
                    .successfulRecipients(0)
                    .failedRecipients(emailRequest.getRecipients())
                    .build();
        }

        // Simulate successful send
        String messageId = "mock-" + UUID.randomUUID().toString();
        log.info("Mock email sent successfully. MessageId: {}, Recipients: {}, Subject: {}",
                messageId, emailRequest.getRecipients().size(), emailRequest.getSubject());

        return EmailResponse.builder()
                .messageId(messageId)
                .success(true)
                .message("Mock email sent successfully")
                .timestamp(LocalDateTime.now())
                .totalRecipients(emailRequest.getRecipients().size())
                .successfulRecipients(emailRequest.getRecipients().size())
                .failedRecipients(new ArrayList<>())
                .build();
    }

    @Override
    public boolean verifyEmailAddress(String emailAddress) {
        log.info("Mock provider: Email verification always succeeds for {}", emailAddress);
        return true;
    }

    @Override
    public List<String> getVerifiedEmailAddresses() {
        return List.of("test@example.com", "noreply@outreachly.com");
    }

    @Override
    public boolean isHealthy() {
        return true; // Mock provider is always healthy
    }

    @Override
    public String getProviderInfo() {
        return String.format("Mock Provider (%s) - Always healthy, simulates 5%% failure rate",
                getProviderType().getDescription());
    }
}
