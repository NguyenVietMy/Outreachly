package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.UserResendConfig;
import com.outreachly.outreachly.repository.UserResendConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing user-specific Resend configurations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserResendConfigService {

    private final UserResendConfigRepository repository;

    // Temporary storage for verification data (in-memory)
    private final Map<Long, VerificationData> verificationStorage = new ConcurrentHashMap<>();

    /**
     * Inner class for storing verification data temporarily
     */
    private static class VerificationData {
        private final String verificationCode;
        private final String apiKey;
        private final String fromEmail;
        private final String fromName;
        private final String domain;
        private final LocalDateTime createdAt;
        private boolean verified;

        public VerificationData(String verificationCode, String apiKey, String fromEmail, String fromName,
                String domain) {
            this.verificationCode = verificationCode;
            this.apiKey = apiKey;
            this.fromEmail = fromEmail;
            this.fromName = fromName;
            this.domain = domain;
            this.createdAt = LocalDateTime.now();
            this.verified = false;
        }

        // Getters
        public String getVerificationCode() {
            return verificationCode;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getFromEmail() {
            return fromEmail;
        }

        public String getFromName() {
            return fromName;
        }

        public String getDomain() {
            return domain;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public boolean isVerified() {
            return verified;
        }

        // Setters
        public void setVerified(boolean verified) {
            this.verified = verified;
        }

        // Check if verification data is expired (10 minutes)
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(createdAt.plusMinutes(10));
        }
    }

    /**
     * Save or update user's Resend configuration
     * Only saves if domain verification is successful
     * Returns detailed result information
     */
    @Transactional
    public SaveResult saveConfig(Long userId, String apiKey, String fromEmail, String fromName, String domain) {
        log.info("Saving Resend configuration for user: {}", userId);

        // Check if domain verification is successful
        VerificationData verificationData = getVerificationData(userId);
        if (verificationData == null) {
            return new SaveResult(false,
                    "Domain verification required before saving configuration. Please verify your domain first.");
        }

        // Use verified data from temporary storage
        String verifiedApiKey = verificationData.getApiKey();
        String verifiedFromEmail = verificationData.getFromEmail();
        String verifiedFromName = verificationData.getFromName();
        String verifiedDomain = verificationData.getDomain();

        try {
            Optional<UserResendConfig> existing = repository.findByUserId(userId);

            if (existing.isPresent()) {
                // Update existing configuration
                UserResendConfig config = existing.get();
                config.setApiKey(verifiedApiKey);
                config.setFromEmail(verifiedFromEmail);
                config.setFromName(verifiedFromName);
                config.setDomain(verifiedDomain);
                config.setIsActive(true);
                config.setIsDomainVerified(true);
                config.setVerificationCode(null); // Clear verification code
                UserResendConfig savedConfig = repository.save(config);

                // Clear temporary verification data
                clearVerificationData(userId);
                return new SaveResult(true, "Configuration updated successfully!", savedConfig);
            } else {
                // Create new configuration
                UserResendConfig config = UserResendConfig.builder()
                        .userId(userId)
                        .apiKey(verifiedApiKey)
                        .fromEmail(verifiedFromEmail)
                        .fromName(verifiedFromName)
                        .domain(verifiedDomain)
                        .isActive(true)
                        .isDomainVerified(true)
                        .build();
                UserResendConfig savedConfig = repository.save(config);

                // Clear temporary verification data
                clearVerificationData(userId);
                return new SaveResult(true, "Configuration saved successfully!", savedConfig);
            }
        } catch (Exception e) {
            log.error("Error saving configuration for user: {}", userId, e);
            return new SaveResult(false, "Failed to save configuration: " + e.getMessage());
        }
    }

    /**
     * Result class for save operations
     */
    public static class SaveResult {
        private final boolean success;
        private final String message;
        private final UserResendConfig config;

        public SaveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.config = null;
        }

        public SaveResult(boolean success, String message, UserResendConfig config) {
            this.success = success;
            this.message = message;
            this.config = config;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public UserResendConfig getConfig() {
            return config;
        }
    }

    /**
     * Get user's active Resend configuration
     */
    public Optional<UserResendConfig> getActiveConfig(Long userId) {
        return repository.findByUserIdAndIsActiveTrue(userId);
    }

    /**
     * Get user's Resend configuration (regardless of active status)
     */
    public Optional<UserResendConfig> getConfig(Long userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Check if user has a Resend configuration
     */
    public boolean hasConfig(Long userId) {
        return repository.existsByUserId(userId);
    }

    /**
     * Delete user's Resend configuration
     */
    @Transactional
    public void deleteConfig(Long userId) {
        log.info("Deleting Resend configuration for user: {}", userId);
        repository.deleteByUserId(userId);
    }

    /**
     * Deactivate user's Resend configuration without deleting
     */
    @Transactional
    public void deactivateConfig(Long userId) {
        log.info("Deactivating Resend configuration for user: {}", userId);
        Optional<UserResendConfig> config = repository.findByUserId(userId);
        config.ifPresent(c -> {
            c.setIsActive(false);
            repository.save(c);
        });
    }

    /**
     * Generate and store verification code for domain validation
     * Uses temporary storage instead of database records
     * Returns detailed result information
     */
    public VerificationResult generateVerificationCode(Long userId, String apiKey, String fromEmail, String fromName,
            String domain) {
        log.info("Generating verification code for user: {}", userId);

        // Generate 6-digit random code
        Random random = new Random();
        String verificationCode = String.format("%06d", random.nextInt(1000000));

        // Store verification data temporarily (no database writes)
        VerificationData verificationData = new VerificationData(
                verificationCode, apiKey, fromEmail, fromName, domain);
        verificationStorage.put(userId, verificationData);

        log.info("Verification code generated and stored temporarily for user: {}", userId);
        return new VerificationResult(true, verificationCode);
    }

    /**
     * Verify the provided verification code
     * Uses temporary storage instead of database records
     * Returns detailed error information
     */
    public VerificationResult verifyCode(Long userId, String providedCode) {
        log.info("Verifying code for user: {}", userId);

        VerificationData verificationData = verificationStorage.get(userId);
        if (verificationData == null) {
            log.warn("No verification data found for user: {}", userId);
            return new VerificationResult(false,
                    "No verification session found. Please request a new verification code.");
        }

        if (verificationData.isExpired()) {
            log.warn("Verification data expired for user: {}", userId);
            verificationStorage.remove(userId);
            return new VerificationResult(false,
                    "Verification code has expired. Please request a new verification code.");
        }

        if (!verificationData.getVerificationCode().equals(providedCode)) {
            log.warn("Invalid verification code for user: {}", userId);
            return new VerificationResult(false, "Invalid verification code. Please check the code and try again.");
        }

        // Mark verification as successful (still no database writes)
        verificationData.setVerified(true);
        log.info("Domain verification successful for user: {}", userId);
        return new VerificationResult(true, "Domain verification successful! You can now save your configuration.");
    }

    /**
     * Result class for verification operations
     */
    public static class VerificationResult {
        private final boolean success;
        private final String message;

        public VerificationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Check if user's domain is verified (from temporary storage)
     */
    public boolean isDomainVerified(Long userId) {
        VerificationData verificationData = verificationStorage.get(userId);
        return verificationData != null && verificationData.isVerified() && !verificationData.isExpired();
    }

    /**
     * Check if user has pending verification data
     */
    public boolean hasPendingVerification(Long userId) {
        VerificationData verificationData = verificationStorage.get(userId);
        return verificationData != null && !verificationData.isExpired();
    }

    /**
     * Get verification data for saving configuration
     */
    public VerificationData getVerificationData(Long userId) {
        VerificationData verificationData = verificationStorage.get(userId);
        if (verificationData != null && verificationData.isVerified() && !verificationData.isExpired()) {
            return verificationData;
        }
        return null;
    }

    /**
     * Clear verification data after successful save
     */
    public void clearVerificationData(Long userId) {
        verificationStorage.remove(userId);
        log.info("Cleared verification data for user: {}", userId);
    }
}
