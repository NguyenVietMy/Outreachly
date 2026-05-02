package com.pulse.pulse.personal.infrastructure.persistence;

import com.pulse.pulse.personal.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
