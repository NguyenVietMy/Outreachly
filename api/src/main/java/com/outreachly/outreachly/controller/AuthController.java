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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            User user = userService.findByEmail(email);

            if (user != null) {
                response.put("authenticated", true);
                response.put("user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "profilePictureUrl", user.getProfilePictureUrl(),
                        "role", user.getRole().name()));
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
            responseBody.put("redirectUrl", "http://localhost:3000/");

            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", "Logout completed");
            responseBody.put("redirectUrl", "http://localhost:3000/");
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
