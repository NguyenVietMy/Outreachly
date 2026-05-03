package com.pulse.pulse.integrations.infrastructure.persistence;

import com.pulse.pulse.integrations.domain.UserIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIntegrationRepository extends JpaRepository<UserIntegration, UUID> {

    List<UserIntegration> findByUserId(Long userId);

    Optional<UserIntegration> findByUserIdAndProvider(Long userId, String provider);

    List<UserIntegration> findByProviderAndStatus(String provider, String status);

    void deleteByUserIdAndProvider(Long userId, String provider);
}
