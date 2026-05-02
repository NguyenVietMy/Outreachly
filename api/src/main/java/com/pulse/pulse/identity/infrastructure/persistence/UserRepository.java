package com.pulse.pulse.identity.infrastructure.persistence;

import com.pulse.pulse.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);

    boolean existsByEmail(String email);
}
