package com.outreachly.outreachly.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outreachly.outreachly.dto.EmailProviderConfigDto;
import com.outreachly.outreachly.dto.EmailProviderInfoDto;
import com.outreachly.outreachly.dto.OrganizationSettingsDto;
import com.outreachly.outreachly.entity.OrganizationSettings;
import com.outreachly.outreachly.repository.OrganizationSettingsRepository;
import com.outreachly.outreachly.service.email.EmailProvider;
import com.outreachly.outreachly.service.email.EmailProviderFactory;
import com.outreachly.outreachly.service.email.EmailProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final OrganizationSettingsRepository settingsRepository;
    private final EmailProviderFactory emailProviderFactory;
    private final ObjectMapper objectMapper;

    /**
     * Get organization settings for the given org ID
     */
    public OrganizationSettingsDto getOrganizationSettings(UUID orgId) {
        Optional<OrganizationSettings> settings = settingsRepository.findByOrgId(orgId);

        if (settings.isEmpty()) {
            // Create default settings if they don't exist
            return createDefaultSettings(orgId);
        }

        return convertToDto(settings.get());
    }

    /**
     * Update organization settings
     */
    @Transactional
    public OrganizationSettingsDto updateOrganizationSettings(UUID orgId, OrganizationSettingsDto settingsDto) {
        Optional<OrganizationSettings> existingSettings = settingsRepository.findByOrgId(orgId);

        OrganizationSettings settings;
        if (existingSettings.isPresent()) {
            settings = existingSettings.get();
            updateSettingsFromDto(settings, settingsDto);
        } else {
            settings = createSettingsFromDto(orgId, settingsDto);
        }

        OrganizationSettings savedSettings = settingsRepository.save(settings);
        log.info("Updated organization settings for org: {}", orgId);

        return convertToDto(savedSettings);
    }

    /**
     * Get available email providers with their status
     */
    public List<EmailProviderInfoDto> getAvailableEmailProviders(UUID orgId) {
        log.info("Getting available email providers for org: {}", orgId);

        Map<EmailProviderType, EmailProvider> providers = emailProviderFactory.getAllProviders();
        log.info("Found {} email providers: {}", providers.size(), providers.keySet());

        OrganizationSettingsDto currentSettings = getOrganizationSettings(orgId);
        log.info("Current settings for org {}: provider={}", orgId, currentSettings.getEmailProvider());

        List<EmailProviderInfoDto> result = providers.entrySet().stream()
                .map(entry -> {
                    EmailProviderType type = entry.getKey();
                    EmailProvider provider = entry.getValue();

                    boolean isActive = type.name().toLowerCase().replace("_", "-")
                            .equals(currentSettings.getEmailProvider());

                    EmailProviderInfoDto dto = EmailProviderInfoDto.builder()
                            .id(type.name().toLowerCase().replace("_", "-"))
                            .name(type.getDisplayName())
                            .type("api")
                            .isActive(isActive)
                            .isHealthy(provider.isHealthy())
                            .config(isActive ? currentSettings.getEmailProviderConfig() : null)
                            .description(type.getDescription())
                            .build();

                    log.debug("Provider {}: active={}, healthy={}", dto.getName(), dto.isActive(), dto.isHealthy());
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("Returning {} email provider DTOs", result.size());
        return result;
    }

    /**
     * Switch email provider for organization
     */
    @Transactional
    public OrganizationSettingsDto switchEmailProvider(UUID orgId, String providerId, EmailProviderConfigDto config) {
        // Validate provider exists
        EmailProviderType providerType = parseProviderType(providerId);
        if (!emailProviderFactory.isProviderAvailable(providerType)) {
            throw new IllegalArgumentException("Email provider not available: " + providerId);
        }

        // Get current settings or create new ones
        Optional<OrganizationSettings> existingSettings = settingsRepository.findByOrgId(orgId);
        OrganizationSettings settings;

        if (existingSettings.isPresent()) {
            settings = existingSettings.get();
            settings.setEmailProvider(providerId);
            settings.setEmailProviderConfig(convertConfigToJson(config));
        } else {
            settings = OrganizationSettings.builder()
                    .orgId(orgId)
                    .emailProvider(providerId)
                    .emailProviderConfig(convertConfigToJson(config))
                    .notificationSettings("{}")
                    .featureFlags("{}")
                    .build();
        }

        OrganizationSettings savedSettings = settingsRepository.save(settings);
        log.info("Switched email provider to {} for org: {}", providerId, orgId);

        return convertToDto(savedSettings);
    }

    /**
     * Test email provider configuration
     */
    public boolean testEmailProviderConfiguration(String providerId, EmailProviderConfigDto config) {
        try {
            EmailProviderType providerType = parseProviderType(providerId);
            EmailProvider provider = emailProviderFactory.getProvider(providerType);

            // For now, just check if provider is healthy
            // In a real implementation, you might want to send a test email
            return provider.isHealthy();
        } catch (Exception e) {
            log.error("Error testing email provider configuration: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private OrganizationSettingsDto createDefaultSettings(UUID orgId) {
        return OrganizationSettingsDto.builder()
                .emailProvider("resend")
                .emailProviderConfig(EmailProviderConfigDto.builder()
                        .apiKey("")
                        .fromEmail("")
                        .fromName("")
                        .build())
                .notificationSettings(new HashMap<>())
                .featureFlags(new HashMap<>())
                .build();
    }

    private OrganizationSettingsDto convertToDto(OrganizationSettings settings) {
        try {
            EmailProviderConfigDto config = objectMapper.readValue(
                    settings.getEmailProviderConfig(),
                    EmailProviderConfigDto.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> notificationSettings = objectMapper.readValue(
                    settings.getNotificationSettings(),
                    Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> featureFlags = objectMapper.readValue(
                    settings.getFeatureFlags(),
                    Map.class);

            return OrganizationSettingsDto.builder()
                    .emailProvider(settings.getEmailProvider())
                    .emailProviderConfig(config)
                    .notificationSettings(notificationSettings)
                    .featureFlags(featureFlags)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Error converting settings to DTO: {}", e.getMessage());
            return createDefaultSettings(settings.getOrgId());
        }
    }

    private void updateSettingsFromDto(OrganizationSettings settings, OrganizationSettingsDto dto) {
        settings.setEmailProvider(dto.getEmailProvider());
        settings.setEmailProviderConfig(convertConfigToJson(dto.getEmailProviderConfig()));
        settings.setNotificationSettings(convertToJson(dto.getNotificationSettings()));
        settings.setFeatureFlags(convertToJson(dto.getFeatureFlags()));
    }

    private OrganizationSettings createSettingsFromDto(UUID orgId, OrganizationSettingsDto dto) {
        return OrganizationSettings.builder()
                .orgId(orgId)
                .emailProvider(dto.getEmailProvider())
                .emailProviderConfig(convertConfigToJson(dto.getEmailProviderConfig()))
                .notificationSettings(convertToJson(dto.getNotificationSettings()))
                .featureFlags(convertToJson(dto.getFeatureFlags()))
                .build();
    }

    private String convertConfigToJson(EmailProviderConfigDto config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.error("Error converting config to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private String convertToJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Error converting data to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private EmailProviderType parseProviderType(String providerId) {
        try {
            return EmailProviderType.valueOf(providerId.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid email provider: " + providerId);
        }
    }
}
