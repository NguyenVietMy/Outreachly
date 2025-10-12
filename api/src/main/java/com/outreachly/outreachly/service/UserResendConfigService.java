package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.UserResendConfig;
import com.outreachly.outreachly.repository.UserResendConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing user-specific Resend configurations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserResendConfigService {

    private final UserResendConfigRepository repository;

    /**
     * Save or update user's Resend configuration
     */
    @Transactional
    public UserResendConfig saveConfig(Long userId, String apiKey, String fromEmail, String fromName, String domain) {
        log.info("Saving Resend configuration for user: {}", userId);

        Optional<UserResendConfig> existing = repository.findByUserId(userId);

        if (existing.isPresent()) {
            // Update existing configuration
            UserResendConfig config = existing.get();
            config.setApiKey(apiKey);
            config.setFromEmail(fromEmail);
            config.setFromName(fromName);
            config.setDomain(domain);
            config.setIsActive(true);
            return repository.save(config);
        } else {
            // Create new configuration
            UserResendConfig config = UserResendConfig.builder()
                    .userId(userId)
                    .apiKey(apiKey)
                    .fromEmail(fromEmail)
                    .fromName(fromName)
                    .domain(domain)
                    .isActive(true)
                    .build();
            return repository.save(config);
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
}
