package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.ResendConfigRequest;
import com.outreachly.outreachly.dto.ResendConfigResponse;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.entity.UserResendConfig;
import com.outreachly.outreachly.repository.UserRepository;
import com.outreachly.outreachly.service.UserResendConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for managing user-specific Resend configurations
 */
@RestController
@RequestMapping("/api/user/resend")
@RequiredArgsConstructor
@Slf4j
public class UserResendConfigController {

    private final UserResendConfigService userResendConfigService;
    private final UserRepository userRepository;
    private final WebClient resendWebClient;

    /**
     * Get user's Resend configuration
     */
    @GetMapping("/config")
    public ResponseEntity<ResendConfigResponse> getConfig(Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            log.info("Getting Resend config for user: {}", userId);

            Optional<UserResendConfig> config = userResendConfigService.getConfig(userId);

            if (config.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            UserResendConfig userConfig = config.get();
            ResendConfigResponse response = ResendConfigResponse.builder()
                    .fromEmail(userConfig.getFromEmail())
                    .fromName(userConfig.getFromName())
                    .domain(userConfig.getDomain())
                    .isActive(userConfig.getIsActive())
                    .apiKeyMasked(maskApiKey(userConfig.getApiKey()))
                    .createdAt(userConfig.getCreatedAt())
                    .updatedAt(userConfig.getUpdatedAt())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting Resend config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Save user's Resend configuration
     */
    @PostMapping("/config")
    public ResponseEntity<ResendConfigResponse> saveConfig(
            @RequestBody ResendConfigRequest request,
            Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            log.info("Saving Resend config for user: {}", userId);

            // Validate request
            if (request.getApiKey() == null || request.getApiKey().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            if (request.getFromEmail() == null || request.getFromEmail().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Save configuration
            UserResendConfig config = userResendConfigService.saveConfig(
                    userId,
                    request.getApiKey(),
                    request.getFromEmail(),
                    request.getFromName(),
                    request.getDomain());

            ResendConfigResponse response = ResendConfigResponse.builder()
                    .fromEmail(config.getFromEmail())
                    .fromName(config.getFromName())
                    .domain(config.getDomain())
                    .isActive(config.getIsActive())
                    .apiKeyMasked(maskApiKey(config.getApiKey()))
                    .createdAt(config.getCreatedAt())
                    .updatedAt(config.getUpdatedAt())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error saving Resend config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete user's Resend configuration
     */
    @DeleteMapping("/config")
    public ResponseEntity<Void> deleteConfig(Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            log.info("Deleting Resend config for user: {}", userId);

            userResendConfigService.deleteConfig(userId);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error deleting Resend config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Test user's Resend configuration
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConfig(
            @RequestBody ResendConfigRequest request) {
        try {
            log.info("Testing Resend config for email: {}", request.getFromEmail());

            // Validate API key format
            if (request.getApiKey() == null || !request.getApiKey().startsWith("re_")) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Invalid API key format. Should start with 're_'"));
            }

            // Validate email format
            if (request.getFromEmail() == null || !request.getFromEmail().contains("@")) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Invalid email format"));
            }

            // Test API key by making a request to Resend API
            try {
                Map<String, Object> testRequest = new HashMap<>();
                testRequest.put("from", request.getFromEmail());
                testRequest.put("to", new String[] { "test@resend.dev" }); // Resend test email
                testRequest.put("subject", "Test Email from Outreachly");
                testRequest.put("text", "This is a test email to verify your Resend configuration.");

                @SuppressWarnings("unchecked")
                Map<String, Object> response = resendWebClient.post()
                        .uri("https://api.resend.com/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getApiKey())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .bodyValue(testRequest)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response != null && response.containsKey("id")) {
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Configuration is valid! Test email sent successfully."));
                } else {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "Unexpected response from Resend API"));
                }

            } catch (Exception e) {
                log.error("Resend API test failed", e);
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "API test failed: " + e.getMessage()));
            }

        } catch (Exception e) {
            log.error("Error testing Resend config", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Get user ID from authentication
     */
    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }

        Optional<User> user = userRepository.findByEmail(authentication.getName());
        if (user.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        return user.get().getId();
    }

    /**
     * Mask API key for security (show only last 4 characters)
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 4) {
            return "****";
        }
        return "re_****" + apiKey.substring(apiKey.length() - 4);
    }
}
