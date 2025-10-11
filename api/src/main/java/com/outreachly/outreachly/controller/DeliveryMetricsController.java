package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.service.DeliveryTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Slf4j
public class DeliveryMetricsController {

    private final DeliveryTrackingService deliveryTrackingService;

    /**
     * Get delivery rate statistics for a specific campaign
     */
    @GetMapping("/delivery-rate/campaign/{campaignId}")
    public ResponseEntity<DeliveryTrackingService.DeliveryStats> getCampaignDeliveryRate(
            @PathVariable String campaignId) {
        try {
            log.info("Getting delivery rate stats for campaign: {}", campaignId);
            DeliveryTrackingService.DeliveryStats stats = deliveryTrackingService.getCampaignDeliveryStats(campaignId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting campaign delivery rate for campaign: {}", campaignId, e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get delivery rate statistics for a specific user
     */
    @GetMapping("/delivery-rate/user")
    public ResponseEntity<DeliveryTrackingService.DeliveryStats> getUserDeliveryRate(Authentication authentication) {
        try {
            String userId = getUserIdFromAuth(authentication);
            if (userId == null) {
                // For now, return overall stats instead of 401 to get data showing
                log.info("No user authentication, returning overall delivery stats");
                DeliveryTrackingService.DeliveryStats stats = deliveryTrackingService.getOverallDeliveryStats();
                return ResponseEntity.ok(stats);
            }

            log.info("Getting delivery rate stats for user: {}", userId);
            DeliveryTrackingService.DeliveryStats stats = deliveryTrackingService.getUserDeliveryStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting user delivery rate", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get delivery trend data for a specific period
     */
    @GetMapping("/delivery-rate/trends")
    public ResponseEntity<List<DeliveryTrackingService.TrendData>> getDeliveryTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String campaignId,
            Authentication authentication) {
        try {
            // Extract user ID from authentication
            String userId = getUserIdFromAuth(authentication);

            log.info("Getting delivery trend data for {} days, user: {}, campaign: {}",
                    days, userId, campaignId);

            List<DeliveryTrackingService.TrendData> trends = deliveryTrackingService.getDeliveryTrends(days, userId,
                    campaignId);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("Error getting delivery trends", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get delivery trend data for the current month
     */
    @GetMapping("/delivery-rate/trends/monthly")
    public ResponseEntity<List<DeliveryTrackingService.TrendData>> getMonthlyDeliveryTrends(
            @RequestParam(required = false) String campaignId,
            Authentication authentication) {
        try {
            // Extract user ID from authentication
            String userId = getUserIdFromAuth(authentication);

            log.info("Getting monthly delivery trend data, user: {}, campaign: {}",
                    userId, campaignId);

            List<DeliveryTrackingService.TrendData> trends = deliveryTrackingService.getCurrentMonthTrends(userId,
                    campaignId);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("Error getting monthly delivery trends", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get current date information for debugging
     */
    @GetMapping("/delivery-rate/date-info")
    public ResponseEntity<String> getCurrentDateInfo() {
        try {
            String dateInfo = deliveryTrackingService.getCurrentDateInfo();
            return ResponseEntity.ok(dateInfo);
        } catch (Exception e) {
            log.error("Error getting current date info", e);
            return ResponseEntity.status(500).body("Error getting date info");
        }
    }

    /**
     * Simple test endpoint to check if we have any email events data
     */
    @GetMapping("/delivery-rate/test-data")
    public ResponseEntity<Map<String, Object>> getTestData() {
        try {
            Map<String, Object> response = new HashMap<>();

            // Get overall stats
            DeliveryTrackingService.DeliveryStats stats = deliveryTrackingService.getOverallDeliveryStats();
            response.put("overallStats", stats);

            // Get 7-day trends
            List<DeliveryTrackingService.TrendData> trends = deliveryTrackingService.getDeliveryTrends(7, null, null);
            response.put("trends", trends);

            // Get date info
            String dateInfo = deliveryTrackingService.getCurrentDateInfo();
            response.put("dateInfo", dateInfo);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting test data", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Create some test data for the chart
     */
    @PostMapping("/delivery-rate/create-test-data")
    public ResponseEntity<Map<String, String>> createTestData() {
        try {
            deliveryTrackingService.createTestData();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Test data created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating test data", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Extract user ID from authentication
     */
    private String getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        try {
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                org.springframework.security.oauth2.core.user.OAuth2User oauth2User = (org.springframework.security.oauth2.core.user.OAuth2User) authentication
                        .getPrincipal();
                return oauth2User.getAttribute("sub"); // Google user ID
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from authentication", e);
        }

        return null;
    }
}
