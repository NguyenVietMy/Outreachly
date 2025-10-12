package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.UserResendConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserResendConfig entity
 */
@Repository
public interface UserResendConfigRepository extends JpaRepository<UserResendConfig, UUID> {

    /**
     * Find active configuration for a user
     */
    Optional<UserResendConfig> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * Find configuration by user ID (regardless of active status)
     */
    Optional<UserResendConfig> findByUserId(Long userId);

    /**
     * Check if user has a configuration
     */
    boolean existsByUserId(Long userId);

    /**
     * Delete configuration by user ID
     */
    void deleteByUserId(Long userId);
}
