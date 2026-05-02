package com.pulse.pulse.identity.api;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            CurrentUserView user = userService.getCurrentUser(authentication.getName());

            if (user != null) {
                response.put("authenticated", true);
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.id());
                userMap.put("email", user.email());
                userMap.put("firstName", user.firstName());
                userMap.put("lastName", user.lastName());
                userMap.put("profilePictureUrl", user.profilePictureUrl());
                userMap.put("role", user.role());
                userMap.put("orgId", user.orgId());
                userMap.put("timezone", user.timezone());
                userMap.put("createdAt", user.createdAt());
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
            SecurityContextHolder.clearContext();

            if (request.getSession(false) != null) {
                request.getSession().invalidate();
            }

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

        Map<String, Object> googleProvider = new HashMap<>();
        googleProvider.put("name", "Google");
        googleProvider.put("url", "/oauth2/authorization/google");
        googleProvider.put("enabled", true);
        providers.put("google", googleProvider);

        return ResponseEntity.ok(providers);
    }
}
