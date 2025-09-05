package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }

    public User save(User user) {
        log.info("Saving user: {}", user.getEmail());
        return userRepository.save(user);
    }

    public User createOrUpdateUser(String email, String firstName, String lastName,
            String profilePictureUrl, User.AuthProvider provider,
            String providerId) {
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, providerId);

        if (existingUser.isPresent()) {
            // Update existing user
            User user = existingUser.get();
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setProfilePictureUrl(profilePictureUrl);
            log.info("Updated existing user: {}", email);
            return userRepository.save(user);
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
            log.info("Created new user: {}", email);
            return userRepository.save(newUser);
        }
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByProviderAndProviderId(User.AuthProvider provider, String providerId) {
        return userRepository.existsByProviderAndProviderId(provider, providerId);
    }
}
