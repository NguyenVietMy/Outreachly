package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.service.DeliveryTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/track")
@RequiredArgsConstructor
@Slf4j
public class LinkTrackingController {

    private final DeliveryTrackingService deliveryTrackingService;

    /**
     * Track link clicks and redirect to original URL
     * URL format:
     * /track/click?url={encodedOriginalUrl}&msg={messageId}&user={userId}&campaign={campaignId}&org={orgId}
     * 
     * Example:
     * http://localhost:3000/track/click?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DMIvvsqJAYh0&msg=gmail_1760235053679_efkq4jsgv&user=1&org=ee7eaf9b-134c-491c-b487-95be8d0acda1
     * 
     * Double masking flow:
     * 1. User clicks TinyURL: https://tiny.ly/abc123
     * 2. TinyURL redirects to your tracking URL with original URL encoded
     * 3. We decode the URL parameter to get original URL
     * 4. Record the click with original URL
     * 5. Redirect to original URL
     */
    @GetMapping("/click")
    public RedirectView trackClick(
            @RequestParam("url") String encodedOriginalUrl,
            @RequestParam("msg") String messageId,
            @RequestParam(value = "user", required = false) String userId,
            @RequestParam(value = "campaign", required = false) String campaignId,
            @RequestParam(value = "org", required = false) String orgId,
            @RequestParam(value = "email", required = false) String recipientEmail) {

        try {
            // Decode the original URL
            String originalUrl = URLDecoder.decode(encodedOriginalUrl, StandardCharsets.UTF_8);

            // Record the click with the original URL
            deliveryTrackingService.recordLinkClick(
                    messageId,
                    recipientEmail,
                    originalUrl,
                    campaignId,
                    userId,
                    orgId);

            log.info("Tracked click for original URL: {} from message: {} (user: {}, org: {})",
                    originalUrl, messageId, userId, orgId);

            // Redirect to the original URL
            return new RedirectView(originalUrl);

        } catch (Exception e) {
            log.error("Error tracking click for URL: {}, message: {}", encodedOriginalUrl, messageId, e);
            // Still redirect even if tracking fails
            try {
                String originalUrl = URLDecoder.decode(encodedOriginalUrl, StandardCharsets.UTF_8);
                return new RedirectView(originalUrl);
            } catch (Exception decodeError) {
                log.error("Failed to decode URL: {}", encodedOriginalUrl, decodeError);
                // Fallback URL - use appropriate domain for environment
                String fallbackUrl = isProductionEnvironment() ? "https://outreach-ly.com" : "http://localhost:3000";
                return new RedirectView(fallbackUrl);
            }
        }
    }

    /**
     * Check if we're running in production environment
     */
    private boolean isProductionEnvironment() {
        String env = System.getProperty("spring.profiles.active");
        return "prod".equals(env) || "production".equals(env);
    }

    /**
     * Test endpoint to verify link tracking is working
     * GET /track/test
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Link tracking controller is working");
        response.put("environment", isProductionEnvironment() ? "production" : "development");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Get click statistics for debugging
     */
    @GetMapping("/click/stats")
    public ResponseEntity<Map<String, Object>> getClickStats(
            @RequestParam(value = "messageId", required = false) String messageId,
            @RequestParam(value = "userId", required = false) String userId) {

        try {
            Map<String, Object> response = new HashMap<>();

            // This would need to be implemented in DeliveryTrackingService
            // For now, just return a placeholder
            response.put("message", "Click stats endpoint - to be implemented");
            response.put("messageId", messageId);
            response.put("userId", userId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting click stats", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
