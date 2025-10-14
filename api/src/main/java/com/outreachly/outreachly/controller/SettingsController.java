package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.EmailProviderConfigDto;
import com.outreachly.outreachly.dto.EmailProviderInfoDto;
import com.outreachly.outreachly.dto.OrganizationSettingsDto;
import com.outreachly.outreachly.service.TimeService;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.SettingsService;
import com.outreachly.outreachly.service.UserService;
import com.outreachly.outreachly.service.email.EmailProvider;
import com.outreachly.outreachly.service.email.EmailProviderFactory;
import com.outreachly.outreachly.service.email.EmailProviderType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final SettingsService settingsService;
    private final UserService userService;
    private final EmailProviderFactory emailProviderFactory;
    private final TimeService timeService;

    /**
     * Get organization settings
     */
    @GetMapping
    public ResponseEntity<OrganizationSettingsDto> getSettings(Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        OrganizationSettingsDto settings = settingsService.getOrganizationSettings(orgId);
        return ResponseEntity.ok(settings);
    }

    /**
     * Update organization settings
     */
    @PutMapping
    public ResponseEntity<OrganizationSettingsDto> updateSettings(
            @Valid @RequestBody OrganizationSettingsDto settingsDto,
            Authentication authentication) {

        UUID orgId = getOrgIdFromAuthentication(authentication);
        OrganizationSettingsDto updatedSettings = settingsService.updateOrganizationSettings(orgId, settingsDto);

        log.info("Updated settings for org: {} by user: {}", orgId, authentication.getName());
        return ResponseEntity.ok(updatedSettings);
    }

    /**
     * Get available email providers
     */
    @GetMapping("/email-providers")
    public ResponseEntity<List<EmailProviderInfoDto>> getEmailProviders(Authentication authentication) {
        try {
            UUID orgId = getOrgIdFromAuthentication(authentication);
            log.info("Getting email providers for org: {}", orgId);
            List<EmailProviderInfoDto> providers = settingsService.getAvailableEmailProviders(orgId);
            log.info("Returning {} providers", providers.size());
            return ResponseEntity.ok(providers);
        } catch (Exception e) {
            log.error("Error getting email providers", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Test endpoint to check if providers are available (no auth required for
     * debugging)
     */
    @GetMapping("/email-providers/test")
    public ResponseEntity<Map<String, Object>> testEmailProviders() {
        try {
            Map<EmailProviderType, EmailProvider> providers = emailProviderFactory.getAllProviders();
            Map<String, Object> response = new HashMap<>();
            response.put("providerCount", providers.size());
            response.put("providers", providers.keySet().stream()
                    .map(type -> Map.of(
                            "type", type.name(),
                            "displayName", type.getDisplayName(),
                            "description", type.getDescription()))
                    .collect(Collectors.toList()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in test endpoint", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Switch email provider
     */
    @PostMapping("/email-providers/{providerId}/switch")
    public ResponseEntity<OrganizationSettingsDto> switchEmailProvider(
            @PathVariable String providerId,
            @Valid @RequestBody EmailProviderConfigDto config,
            Authentication authentication) {

        UUID orgId = getOrgIdFromAuthentication(authentication);
        OrganizationSettingsDto updatedSettings = settingsService.switchEmailProvider(orgId, providerId, config);

        log.info("Switched email provider to {} for org: {} by user: {}", providerId, orgId, authentication.getName());
        return ResponseEntity.ok(updatedSettings);
    }

    /**
     * Test email provider configuration
     */
    @PostMapping("/email-providers/{providerId}/test")
    public ResponseEntity<Map<String, Object>> testEmailProvider(
            @PathVariable String providerId,
            @Valid @RequestBody EmailProviderConfigDto config,
            Authentication authentication) {

        boolean isValid = settingsService.testEmailProviderConfiguration(providerId, config);

        Map<String, Object> response = new HashMap<>();
        response.put("providerId", providerId);
        response.put("isValid", isValid);
        response.put("message", isValid ? "Configuration is valid" : "Configuration is invalid");

        return ResponseEntity.ok(response);
    }

    /**
     * Get current email provider status
     */
    @GetMapping("/email-providers/status")
    public ResponseEntity<Map<String, Object>> getEmailProviderStatus(Authentication authentication) {
        UUID orgId = getOrgIdFromAuthentication(authentication);
        OrganizationSettingsDto settings = settingsService.getOrganizationSettings(orgId);
        List<EmailProviderInfoDto> providers = settingsService.getAvailableEmailProviders(orgId);

        Map<String, Object> response = new HashMap<>();
        response.put("currentProvider", settings.getEmailProvider());
        response.put("providers", providers);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Update notification settings
     */
    @PutMapping("/notifications")
    public ResponseEntity<OrganizationSettingsDto> updateNotificationSettings(
            @RequestBody Map<String, Object> notificationSettings,
            Authentication authentication) {

        UUID orgId = getOrgIdFromAuthentication(authentication);

        // Get current settings
        OrganizationSettingsDto currentSettings = settingsService.getOrganizationSettings(orgId);

        // Only update notification settings, preserve other settings
        currentSettings.setNotificationSettings(notificationSettings);

        // Only set emailProviderConfig if it's actually null (don't create empty
        // object)
        if (currentSettings.getEmailProviderConfig() == null) {
            // Don't create empty config - let the service handle it
            log.debug("Email provider config is null, will be handled by service");
        }

        // Ensure emailProvider is not null
        if (currentSettings.getEmailProvider() == null || currentSettings.getEmailProvider().isEmpty()) {
            currentSettings.setEmailProvider("resend");
        }

        OrganizationSettingsDto updatedSettings = settingsService.updateOrganizationSettings(orgId, currentSettings);

        log.info("Updated notification settings for org: {} by user: {}", orgId, authentication.getName());
        return ResponseEntity.ok(updatedSettings);
    }

    /**
     * Update feature flags
     */
    @PutMapping("/feature-flags")
    public ResponseEntity<OrganizationSettingsDto> updateFeatureFlags(
            @RequestBody Map<String, Object> featureFlags,
            Authentication authentication) {

        UUID orgId = getOrgIdFromAuthentication(authentication);
        OrganizationSettingsDto currentSettings = settingsService.getOrganizationSettings(orgId);
        currentSettings.setFeatureFlags(featureFlags);

        OrganizationSettingsDto updatedSettings = settingsService.updateOrganizationSettings(orgId, currentSettings);

        log.info("Updated feature flags for org: {} by user: {}", orgId, authentication.getName());
        return ResponseEntity.ok(updatedSettings);
    }

    // Helper method to extract org ID from authentication
    private UUID getOrgIdFromAuthentication(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) {
            log.error("User not found for authentication: {}",
                    authentication != null ? authentication.getName() : null);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        if (user.getOrgId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Organization required");
        }
        return user.getOrgId();
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userService.findByEmail(authentication.getName());
    }

    /**
     * Get available timezones
     */
    @GetMapping("/timezones")
    public ResponseEntity<List<String>> getAvailableTimezones() {
        List<String> timezones = timeService.getCommonTimezones();
        return ResponseEntity.ok(timezones);
    }

    /**
     * Update user timezone
     */
    @PutMapping("/timezone")
    public ResponseEntity<Map<String, Object>> updateTimezone(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        String timezone = request.get("timezone");
        if (timezone == null || timezone.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Timezone is required"));
        }

        // Validate timezone
        if (!timeService.isValidTimezone(timezone)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid timezone: " + timezone));
        }

        User user = getUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "User not authenticated"));
        }

        // Update user timezone
        user.setTimezone(timezone);
        userService.save(user);

        log.info("Updated timezone for user: {} to: {}", user.getEmail(), timezone);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("timezone", timezone);
        response.put("timezoneOffset", timeService.getTimezoneOffset(timezone));

        return ResponseEntity.ok(response);
    }

    /**
     * Get current user timezone
     */
    @GetMapping("/timezone")
    public ResponseEntity<Map<String, Object>> getCurrentTimezone(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "User not authenticated"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("timezone", user.getTimezone());
        response.put("timezoneOffset", timeService.getTimezoneOffset(user.getTimezone()));

        return ResponseEntity.ok(response);
    }
}
