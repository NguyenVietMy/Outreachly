package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        try {
            User user = (User) authentication.getPrincipal();
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("email", user.getEmail());
            userInfo.put("firstName", user.getFirstName());
            userInfo.put("lastName", user.getLastName());
            userInfo.put("profilePictureUrl", user.getProfilePictureUrl());
            userInfo.put("provider", user.getProvider());
            userInfo.put("role", user.getRole());
            userInfo.put("createdAt", user.getCreatedAt());

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            log.error("Error getting current user: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getAvailableProviders() {
        Map<String, Object> providers = new HashMap<>();
        providers.put("google", Map.of(
                "name", "Google",
                "url", "/oauth2/authorization/google",
                "enabled", true));
        providers.put("github", Map.of(
                "name", "GitHub",
                "url", "/oauth2/authorization/github",
                "enabled", true));
        providers.put("microsoft", Map.of(
                "name", "Microsoft",
                "url", "/oauth2/authorization/microsoft",
                "enabled", true));

        return ResponseEntity.ok(providers);
    }
}
