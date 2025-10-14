package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            User user = userService.findByEmail(email);

            if (user != null) {
                response.put("authenticated", true);
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("email", user.getEmail());
                userMap.put("firstName", user.getFirstName());
                userMap.put("lastName", user.getLastName());
                userMap.put("profilePictureUrl", user.getProfilePictureUrl());
                userMap.put("role", user.getRole() != null ? user.getRole().name() : null);
                userMap.put("orgId", user.getOrgId());
                userMap.put("timezone", user.getTimezone());
                userMap.put("createdAt", user.getCreatedAt());
                response.put("user", userMap);
            } else {
                response.put("authenticated", false);
                response.put("message", "User not found");
            }
        } else {
            response.put("authenticated", false);
            response.put("message", "Not authenticated");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Clear the security context
            SecurityContextHolder.clearContext();

            // Invalidate the session
            if (request.getSession(false) != null) {
                request.getSession().invalidate();
            }

            // Clear any cookies
            response.setHeader("Set-Cookie", "JSESSIONID=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");

            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", "Logged out successfully");
            responseBody.put("redirectUrl", frontendUrl + "/");

            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", "Logout completed");
            responseBody.put("redirectUrl", frontendUrl + "/");
            return ResponseEntity.ok(responseBody);
        }
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Please login via OAuth2");
        response.put("loginUrl", "/oauth2/authorization/google");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        Map<String, Object> providers = new HashMap<>();

        // Google provider
        Map<String, Object> googleProvider = new HashMap<>();
        googleProvider.put("name", "Google");
        googleProvider.put("url", "/oauth2/authorization/google");
        googleProvider.put("enabled", true);
        providers.put("google", googleProvider);

        return ResponseEntity.ok(providers);
    }
}
