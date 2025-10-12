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
     * /track/click?url={encodedUrl}&msg={messageId}&user={userId}&campaign={campaignId}&org={orgId}
     */
    @GetMapping("/click")
    public RedirectView trackClick(
            @RequestParam("url") String encodedUrl,
            @RequestParam("msg") String messageId,
            @RequestParam(value = "user", required = false) String userId,
            @RequestParam(value = "campaign", required = false) String campaignId,
            @RequestParam(value = "org", required = false) String orgId,
            @RequestParam(value = "email", required = false) String recipientEmail) {

        try {
            // Decode the original URL
            String originalUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);

            // Record the click
            deliveryTrackingService.recordLinkClick(
                    messageId,
                    recipientEmail,
                    originalUrl,
                    campaignId,
                    userId,
                    orgId);

            log.info("Tracked click for URL: {} from message: {}", originalUrl, messageId);

            // Redirect to the original URL
            return new RedirectView(originalUrl);

        } catch (Exception e) {
            log.error("Error tracking click for URL: {}, message: {}", encodedUrl, messageId, e);
            // Still redirect even if tracking fails
            try {
                String originalUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);
                return new RedirectView(originalUrl);
            } catch (Exception decodeError) {
                log.error("Failed to decode URL: {}", encodedUrl, decodeError);
                return new RedirectView("https://outreachly.com"); // Fallback
            }
        }
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


