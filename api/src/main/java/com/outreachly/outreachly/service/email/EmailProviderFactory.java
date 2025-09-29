package com.outreachly.outreachly.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory class for creating email provider instances based on configuration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailProviderFactory {

    private final Map<EmailProviderType, EmailProvider> emailProviders;

    @Value("${email.provider:aws-ses}")
    private String configuredProvider;

    /**
     * Get the configured email provider
     * 
     * @return The configured email provider instance
     */
    public EmailProvider getConfiguredProvider() {
        EmailProviderType providerType = parseProviderType(configuredProvider);
        return getProvider(providerType);
    }

    /**
     * Get a specific email provider by type
     * 
     * @param providerType The type of provider to get
     * @return The email provider instance
     */
    public EmailProvider getProvider(EmailProviderType providerType) {
        EmailProvider provider = emailProviders.get(providerType);
        if (provider == null) {
            log.error("Email provider not found: {}", providerType);
            throw new IllegalArgumentException("Email provider not found: " + providerType);
        }
        return provider;
    }

    /**
     * Get all available email providers
     * 
     * @return Map of all available providers
     */
    public Map<EmailProviderType, EmailProvider> getAllProviders() {
        return emailProviders;
    }

    /**
     * Check if a provider type is available
     * 
     * @param providerType The provider type to check
     * @return true if the provider is available
     */
    public boolean isProviderAvailable(EmailProviderType providerType) {
        return emailProviders.containsKey(providerType);
    }

    /**
     * Get the current configured provider type
     * 
     * @return The configured provider type
     */
    public EmailProviderType getConfiguredProviderType() {
        return parseProviderType(configuredProvider);
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
