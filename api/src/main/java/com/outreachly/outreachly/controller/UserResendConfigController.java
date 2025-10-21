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
                // Return default empty response instead of 404
                ResendConfigResponse defaultResponse = ResendConfigResponse.builder()
                        .isActive(false)
                        .isDomainVerified(false)
                        .build();
                return ResponseEntity.ok(defaultResponse);
            }

            UserResendConfig userConfig = config.get();
            ResendConfigResponse response = ResendConfigResponse.builder()
                    .fromEmail(userConfig.getFromEmail())
                    .fromName(userConfig.getFromName())
                    .domain(userConfig.getDomain())
                    .isActive(userConfig.getIsActive())
                    .isDomainVerified(userConfig.getIsDomainVerified())
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

            // Check if domain verification is required
            if (!userResendConfigService.isDomainVerified(userId)) {
                return ResponseEntity.status(400).body(
                        ResendConfigResponse.builder()
                                .isActive(false)
                                .isDomainVerified(false)
                                .build());
            }

            // Save configuration (will use verified data from temporary storage)
            UserResendConfigService.SaveResult saveResult = userResendConfigService.saveConfig(
                    userId,
                    request.getApiKey(),
                    request.getFromEmail(),
                    request.getFromName(),
                    request.getDomain());

            if (!saveResult.isSuccess()) {
                return ResponseEntity.status(400).body(
                        ResendConfigResponse.builder()
                                .isActive(false)
                                .isDomainVerified(false)
                                .build());
            }

            UserResendConfig config = saveResult.getConfig();

            ResendConfigResponse response = ResendConfigResponse.builder()
                    .fromEmail(config.getFromEmail())
                    .fromName(config.getFromName())
                    .domain(config.getDomain())
                    .isActive(config.getIsActive())
                    .isDomainVerified(config.getIsDomainVerified())
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
     * Send domain verification email
     */
    @PostMapping("/verify-domain")
    public ResponseEntity<Map<String, Object>> sendVerificationEmail(
            @RequestBody ResendConfigRequest request,
            Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            log.info("Sending verification email for user: {}", userId);

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

            // Get user's email for sending verification
            Optional<User> user = userRepository.findByEmail(authentication.getName());
            if (user.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "User not found"));
            }

            String userEmail = user.get().getEmail();

            // Generate verification code
            UserResendConfigService.VerificationResult verificationResult = userResendConfigService
                    .generateVerificationCode(
                            userId,
                            request.getApiKey(),
                            request.getFromEmail(),
                            request.getFromName(),
                            request.getDomain());

            if (!verificationResult.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", verificationResult.getMessage()));
            }

            String verificationCode = verificationResult.getMessage();

            // Send verification email using Resend API
            try {
                Map<String, Object> emailRequest = new HashMap<>();
                emailRequest.put("from", request.getFromEmail());
                emailRequest.put("to", new String[] { userEmail });
                emailRequest.put("subject", "Verify Your Domain - Outreachly");
                emailRequest.put("html", String.format(
                        "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                                "<h2 style='color: #2563eb;'>Verify Your Domain</h2>" +
                                "<p>Hello,</p>" +
                                "<p>You're setting up your Resend email configuration for Outreachly. To complete the setup, please use the verification code below:</p>"
                                +
                                "<div style='background-color: #f3f4f6; padding: 20px; text-align: center; margin: 20px 0; border-radius: 8px;'>"
                                +
                                "<h1 style='color: #1f2937; font-size: 32px; margin: 0; letter-spacing: 4px;'>%s</h1>" +
                                "</div>" +
                                "<p>If you didn't request this verification, please ignore this email.</p>" +
                                "<p>Best regards,<br>The Outreachly Team</p>" +
                                "</div>",
                        verificationCode));

                @SuppressWarnings("unchecked")
                Map<String, Object> response = resendWebClient.post()
                        .uri("https://api.resend.com/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getApiKey())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .bodyValue(emailRequest)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response != null && response.containsKey("id")) {
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message",
                            "Verification email sent successfully! Please check your email and spam folder."));
                } else {
                    log.error("Unexpected response from Resend API: {}", response);
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message",
                            "Failed to send verification email. Please check your API key and email configuration."));
                }

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                log.error("Resend API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

                if (e.getStatusCode().value() == 401) {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "Invalid API key. Please check your Resend API key and try again."));
                } else if (e.getStatusCode().value() == 403) {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message",
                            "API key does not have permission to send emails. Please check your Resend account settings."));
                } else if (e.getStatusCode().value() == 422) {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message",
                            "Invalid email configuration. Please check your email address and domain settings."));
                } else {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message",
                            "Resend API error: " + e.getStatusCode() + ". Please check your configuration."));
                }
            } catch (Exception e) {
                log.error("Failed to send verification email", e);
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Failed to send verification email: " + e.getMessage()));
            }

        } catch (Exception e) {
            log.error("Error sending verification email", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Verify the provided verification code
     */
    @PostMapping("/verify-code")
    public ResponseEntity<Map<String, Object>> verifyCode(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            String providedCode = request.get("verificationCode");

            if (providedCode == null || providedCode.trim().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Verification code is required"));
            }

            UserResendConfigService.VerificationResult result = userResendConfigService.verifyCode(userId,
                    providedCode.trim());

            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage()));

        } catch (Exception e) {
            log.error("Error verifying code", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Internal server error: " + e.getMessage()));
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
