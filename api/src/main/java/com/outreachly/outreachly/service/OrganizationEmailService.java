package com.outreachly.outreachly.service;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.entity.OrganizationSettings;
import com.outreachly.outreachly.repository.OrganizationSettingsRepository;
import com.outreachly.outreachly.service.email.EmailProvider;
import com.outreachly.outreachly.service.email.EmailProviderFactory;
import com.outreachly.outreachly.service.email.EmailProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service that provides organization-specific email functionality
 * by integrating with the existing EmailProviderFactory and organization
 * settings
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationEmailService {

    private final EmailProviderFactory emailProviderFactory;
    private final OrganizationSettingsRepository settingsRepository;

    /**
     * Send email using the organization's configured provider
     */
    public EmailResponse sendEmail(UUID orgId, EmailRequest emailRequest) {
        EmailProvider provider = getOrganizationProvider(orgId);
        return provider.sendEmail(emailRequest);
    }

    /**
     * Send bulk emails using the organization's configured provider
     */
    public EmailResponse sendBulkEmail(UUID orgId, List<EmailRequest> emailRequests) {
        EmailProvider provider = getOrganizationProvider(orgId);
        return provider.sendBulkEmail(emailRequests);
    }

    /**
     * Send email using a specific provider for the organization
     */
    public EmailResponse sendEmail(UUID orgId, EmailRequest emailRequest, EmailProviderType providerType) {
        EmailProvider provider = emailProviderFactory.getProvider(providerType);
        return provider.sendEmail(emailRequest);
    }

    /**
     * Get the organization's configured email provider
     */
    public EmailProvider getOrganizationProvider(UUID orgId) {
        Optional<OrganizationSettings> settings = settingsRepository.findByOrgId(orgId);

        if (settings.isPresent()) {
            String providerString = settings.get().getEmailProvider();
            EmailProviderType providerType = parseProviderType(providerString);
            return emailProviderFactory.getProvider(providerType);
        } else {
            // Fall back to global configuration
            return emailProviderFactory.getConfiguredProvider();
        }
    }

    /**
     * Get the organization's configured provider type
     */
    public EmailProviderType getOrganizationProviderType(UUID orgId) {
        Optional<OrganizationSettings> settings = settingsRepository.findByOrgId(orgId);

        if (settings.isPresent()) {
            String providerString = settings.get().getEmailProvider();
            return parseProviderType(providerString);
        } else {
            return emailProviderFactory.getConfiguredProviderType();
        }
    }

    /**
     * Check if a specific provider is available
     */
    public boolean isProviderAvailable(EmailProviderType providerType) {
        return emailProviderFactory.isProviderAvailable(providerType);
    }

    /**
     * Get all available providers
     */
    public Map<EmailProviderType, EmailProvider> getAllProviders() {
        return emailProviderFactory.getAllProviders();
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

    /**
     * Verify email address using the organization's configured provider
     */
    public boolean verifyEmailAddress(UUID orgId, String emailAddress) {
        EmailProvider provider = getOrganizationProvider(orgId);
        return provider.verifyEmailAddress(emailAddress);
    }

    /**
     * Get verified email addresses from the organization's configured provider
     */
    public List<String> getVerifiedEmailAddresses(UUID orgId) {
        EmailProvider provider = getOrganizationProvider(orgId);
        return provider.getVerifiedEmailAddresses();
    }

    /**
     * Check if email is suppressed using the organization's configured provider
     */
    public boolean isEmailSuppressed(UUID orgId, String emailAddress) {
        EmailProvider provider = getOrganizationProvider(orgId);
        return provider.isEmailSuppressed(emailAddress);
    }

    /**
     * Get email history using the organization's configured provider
     */
    public List<EmailEvent> getEmailHistory(UUID orgId, String emailAddress) {
        EmailProvider provider = getOrganizationProvider(orgId);
        return provider.getEmailHistory(emailAddress);
    }

    private EmailProviderType parseProviderType(String providerString) {
        try {
            return EmailProviderType.valueOf(providerString.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid email provider configuration: {}. Falling back to AWS_SES", providerString);
            return EmailProviderType.AWS_SES;
        }
    }
}
