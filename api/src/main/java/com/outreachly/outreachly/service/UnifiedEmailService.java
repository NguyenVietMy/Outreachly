package com.outreachly.outreachly.service;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.service.email.EmailProvider;
import com.outreachly.outreachly.service.email.EmailProviderFactory;
import com.outreachly.outreachly.service.email.EmailProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Unified email service that provides a single interface for all email
 * operations
 * while supporting multiple email providers through the factory pattern
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedEmailService {

    private final EmailProviderFactory emailProviderFactory;

    /**
     * Send email using the configured provider
     */
    public EmailResponse sendEmail(EmailRequest emailRequest) {
        EmailProvider provider = emailProviderFactory.getConfiguredProvider();
        return provider.sendEmail(emailRequest);
    }

    /**
     * Send bulk emails using the configured provider
     */
    public EmailResponse sendBulkEmail(List<EmailRequest> emailRequests) {
        EmailProvider provider = emailProviderFactory.getConfiguredProvider();
        return provider.sendBulkEmail(emailRequests);
    }

    /**
     * Send email using a specific provider
     */
    public EmailResponse sendEmail(EmailRequest emailRequest, EmailProviderType providerType) {
        EmailProvider provider = emailProviderFactory.getProvider(providerType);
        return provider.sendEmail(emailRequest);
    }

    /**
     * Send bulk emails using a specific provider
     */
    public EmailResponse sendBulkEmail(List<EmailRequest> emailRequests, EmailProviderType providerType) {
        EmailProvider provider = emailProviderFactory.getProvider(providerType);
        return provider.sendBulkEmail(emailRequests);
    }

    /**
     * Verify email address using the configured provider
     */
    public boolean verifyEmailAddress(String emailAddress) {
        EmailProvider provider = emailProviderFactory.getConfiguredProvider();
        return provider.verifyEmailAddress(emailAddress);
    }

    /**
     * Get verified email addresses from the configured provider
     */
    public List<String> getVerifiedEmailAddresses() {
        EmailProvider provider = emailProviderFactory.getConfiguredProvider();
        return provider.getVerifiedEmailAddresses();
    }

    /**
     * Check if email is suppressed using the configured provider
     */
    public boolean isEmailSuppressed(String emailAddress) {
        EmailProvider provider = emailProviderFactory.getConfiguredProvider();
        return provider.isEmailSuppressed(emailAddress);
    }

    /**
     * Get email history using the configured provider
     */
    public List<EmailEvent> getEmailHistory(String emailAddress) {
        EmailProvider provider = emailProviderFactory.getConfiguredProvider();
        return provider.getEmailHistory(emailAddress);
    }

    /**
     * Get the current configured provider
     */
    public EmailProvider getCurrentProvider() {
        return emailProviderFactory.getConfiguredProvider();
    }

    /**
     * Get all available providers
     */
    public Map<EmailProviderType, EmailProvider> getAllProviders() {
        return emailProviderFactory.getAllProviders();
    }

    /**
     * Check if a specific provider is available
     */
    public boolean isProviderAvailable(EmailProviderType providerType) {
        return emailProviderFactory.isProviderAvailable(providerType);
    }

    /**
     * Get health status of all providers
     */
    public Map<EmailProviderType, Boolean> getProvidersHealthStatus() {
        Map<EmailProviderType, EmailProvider> providers = emailProviderFactory.getAllProviders();
        return providers.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().isHealthy()));
    }

    /**
     * Get detailed info about all providers
     */
    public Map<EmailProviderType, String> getProvidersInfo() {
        Map<EmailProviderType, EmailProvider> providers = emailProviderFactory.getAllProviders();
        return providers.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getProviderInfo()));
    }
}
