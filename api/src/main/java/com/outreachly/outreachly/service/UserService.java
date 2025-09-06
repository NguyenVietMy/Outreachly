package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
