package com.outreachly.outreachly.service.email;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.UserResendConfig;
import com.outreachly.outreachly.service.EmailEventService;
import com.outreachly.outreachly.service.UserResendConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;

/**
 * Resend email provider implementation
 * Documentation: https://resend.com/docs/api-reference/emails/send-email
 */
@Service
@Slf4j
public class ResendEmailProvider extends AbstractEmailProvider {

    private final WebClient webClient;
    private final UserResendConfigService userResendConfigService;

    public ResendEmailProvider(EmailEventService emailEventService, WebClient webClient,
            UserResendConfigService userResendConfigService) {
        super(emailEventService);
        this.webClient = webClient;
        this.userResendConfigService = userResendConfigService;
    }

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:}")
    private String fromEmail;

    // Rate limiting for Resend API (2 requests per second)
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("ResendEmailProvider initialized with API key: {}",
                apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, 8) + "..." : "NOT SET");
        log.info("ResendEmailProvider from email: {}", fromEmail);
    }

    @Override
    public EmailProviderType getProviderType() {
        return EmailProviderType.RESEND;
    }

    @Override
    protected EmailResponse doSendEmail(EmailRequest emailRequest) {
        try {
            // Try to get user-specific configuration first
            String userApiKey = apiKey;
            String userFromEmail = fromEmail;
            String userFromName = null;

            // Check if emailRequest has userId (for user-specific config)
            if (emailRequest.getCampaignId() != null) {
                // Try to get user config from campaign or other context
                // For now, use global config as fallback
                log.info("Using global Resend configuration");
            }

            // Check if API key is set
            if (userApiKey == null || userApiKey.isEmpty()) {
                log.error("Resend API key is not set!");
                return EmailResponse.builder()
                        .success(false)
                        .message("Resend API key is not configured. Please configure in Settings.")
                        .timestamp(LocalDateTime.now())
                        .totalRecipients(emailRequest.getRecipients().size())
                        .successfulRecipients(0)
                        .failedRecipients(emailRequest.getRecipients())
                        .build();
            }

            // Check if from email is set
            if (userFromEmail == null || userFromEmail.isEmpty()) {
                log.error("Resend from email is not set!");
                return EmailResponse.builder()
                        .success(false)
                        .message("Resend from email is not configured. Please configure in Settings.")
                        .timestamp(LocalDateTime.now())
                        .totalRecipients(emailRequest.getRecipients().size())
                        .successfulRecipients(0)
                        .failedRecipients(emailRequest.getRecipients())
                        .build();
            }

            return sendEmailWithConfig(emailRequest, userApiKey, userFromEmail, userFromName);

        } catch (Exception e) {
            log.error("Resend failed to send email: {}", e.getMessage(), e);

            return EmailResponse.builder()
                    .success(false)
                    .message("Failed to send email via Resend: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .totalRecipients(emailRequest.getRecipients().size())
                    .successfulRecipients(0)
                    .failedRecipients(emailRequest.getRecipients())
                    .build();
        }
    }

    /**
     * Send email with user-specific configuration
     */
    public EmailResponse sendEmailWithUserConfig(EmailRequest emailRequest, Long userId) {
        try {
            // Get user's Resend configuration
            Optional<UserResendConfig> userConfig = userResendConfigService.getActiveConfig(userId);

            if (userConfig.isEmpty()) {
                log.warn("No Resend configuration found for user: {}, using global config", userId);
                return doSendEmail(emailRequest);
            }

            UserResendConfig config = userConfig.get();
            log.info("Using user-specific Resend configuration for user: {}", userId);

            return sendEmailWithConfig(
                    emailRequest,
                    config.getApiKey(),
                    config.getFromEmail(),
                    config.getFromName());

        } catch (Exception e) {
            log.error("Failed to send email with user config for user: {}", userId, e);
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

    /**
     * Rate limiting to ensure we don't exceed Resend's rate limits
     * Uses synchronized method to ensure thread safety across multiple checkpoints
     */
    private synchronized void enforceRateLimit() {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastRequestTime.get();
        long timeSinceLastRequest = currentTime - lastTime;

        // Use 1 second between requests to be safe
        if (timeSinceLastRequest < 1000) {
            long delayNeeded = 1000 - timeSinceLastRequest;
            try {
                Thread.sleep(delayNeeded);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while rate limiting", e);
            }
        }

        // Update the last request time
        lastRequestTime.set(System.currentTimeMillis());
    }

    /**
     * Core email sending logic with specific configuration
     */
    private EmailResponse sendEmailWithConfig(EmailRequest emailRequest, String userApiKey, String userFromEmail,
            String userFromName) {
        try {
            // Build Resend API request
            Map<String, Object> requestBody = new HashMap<>();

            // Set from address with optional name
            String fromAddress = userFromName != null && !userFromName.isEmpty()
                    ? userFromName + " <" + userFromEmail + ">"
                    : userFromEmail;

            requestBody.put("from", fromAddress);
            requestBody.put("to", emailRequest.getRecipients());
            requestBody.put("subject", emailRequest.getSubject());

            if (emailRequest.isHtml()) {
                requestBody.put("html", emailRequest.getContent());
            } else {
                requestBody.put("text", emailRequest.getContent());
            }

            if (emailRequest.getReplyTo() != null) {
                requestBody.put("reply_to", emailRequest.getReplyTo());
            }

            log.info("Sending email via Resend to: {}, from: {}", emailRequest.getRecipients(), fromAddress);

            // Enforce rate limiting before making API call
            enforceRateLimit();

            // Send email via Resend API
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("https://api.resend.com/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("Resend API error: {} {}", clientResponse.statusCode(),
                                        clientResponse.statusCode().value());
                                return clientResponse.bodyToMono(String.class)
                                        .flatMap(errorBody -> {
                                            log.error("Resend error response: {}", errorBody);
                                            return Mono.error(new RuntimeException("Resend API error: "
                                                    + clientResponse.statusCode() + " - " + errorBody));
                                        });
                            })
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("id")) {
                return EmailResponse.builder()
                        .messageId((String) response.get("id"))
                        .success(true)
                        .message("Email sent successfully via Resend")
                        .timestamp(LocalDateTime.now())
                        .totalRecipients(emailRequest.getRecipients().size())
                        .successfulRecipients(emailRequest.getRecipients().size())
                        .failedRecipients(new ArrayList<>())
                        .build();
            } else {
                throw new RuntimeException("Invalid response from Resend API");
            }

        } catch (Exception e) {
            log.error("Resend failed to send email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email via Resend", e);
        }
    }

    @Override
    public boolean verifyEmailAddress(String emailAddress) {
        // Resend doesn't require email verification like SES
        // They handle verification automatically
        log.info("Resend doesn't require manual email verification: {}", emailAddress);
        return true;
    }

    @Override
    public List<String> getVerifiedEmailAddresses() {
        // Resend doesn't have a concept of "verified" addresses
        // They allow sending from any domain you own
        return List.of(fromEmail);
    }

    @Override
    public boolean isHealthy() {
        try {
            // Check if API key is configured
            if (apiKey == null || apiKey.isEmpty()) {
                return false;
            }

            // Could add a health check API call here if Resend provides one
            return true;
        } catch (Exception e) {
            log.warn("Resend health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderInfo() {
        return String.format("Resend (%s) - From: %s, Healthy: %s",
                getProviderType().getDescription(),
                fromEmail,
                isHealthy());
    }
}
