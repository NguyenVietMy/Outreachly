package com.pulse.pulse.identity.application;

import com.pulse.pulse.identity.domain.User;
import com.pulse.pulse.identity.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User createOrUpdateUser(String email, String firstName, String lastName,
            String profilePictureUrl, User.AuthProvider provider, String providerId) {

        // Check if user exists by email
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null) {
            // Update existing user
            existingUser.setFirstName(firstName);
            existingUser.setLastName(lastName);
            existingUser.setProfilePictureUrl(profilePictureUrl);
            existingUser.setProvider(provider);
            existingUser.setProviderId(providerId);

            log.info("Updating existing user: {}", email);
            return userRepository.save(existingUser);
        } else {
            // Create new user
            User newUser = User.builder()
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .profilePictureUrl(profilePictureUrl)
                    .provider(provider)
                    .providerId(providerId)
                    .role(User.Role.USER)
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .build();

            log.info("Creating new user: {}", email);
            return userRepository.save(newUser);
        }
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User assignOrganizationOwnership(Long userId, UUID orgId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setOrgId(orgId);
        user.setRole(User.Role.OWNER);
        return userRepository.save(user);
    }

    public CurrentUserView getCurrentUser(String email) {
        User user = findByEmail(email);
        return user != null ? toView(user) : null;
    }

    public CurrentUserView getUserView(Long id) {
        User user = findById(id);
        return user != null ? toView(user) : null;
    }

    @Transactional
    public CurrentUserView updateTimezone(Long userId, String timezone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setTimezone(timezone);
        return toView(userRepository.save(user));
    }

    private CurrentUserView toView(User user) {
        return new CurrentUserView(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePictureUrl(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getOrgId(),
                user.getTimezone(),
                user.getCreatedAt()
        );
    }
}
