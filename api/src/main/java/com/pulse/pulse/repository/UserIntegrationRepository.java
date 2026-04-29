package com.pulse.pulse.repository;

import com.pulse.pulse.entity.UserIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIntegrationRepository extends JpaRepository<UserIntegration, UUID> {

    List<UserIntegration> findByUserId(Long userId);

    Optional<UserIntegration> findByUserIdAndProvider(Long userId, String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);

    @Query("SELECT ui FROM UserIntegration ui WHERE ui.status = 'connected' " +
           "AND ui.autoSyncEnabled = true " +
           "AND (ui.lastSyncedAt IS NULL OR ui.lastSyncedAt < :staleCutoff) " +
           "AND (ui.nextSyncAfter IS NULL OR ui.nextSyncAfter < :now) " +
           "ORDER BY ui.lastSyncedAt ASC NULLS FIRST")
    List<UserIntegration> findSyncEligible(@Param("staleCutoff") LocalDateTime staleCutoff,
                                           @Param("now") LocalDateTime now);

    long countByStatusAndAutoSyncEnabled(String status, Boolean autoSyncEnabled);
}
