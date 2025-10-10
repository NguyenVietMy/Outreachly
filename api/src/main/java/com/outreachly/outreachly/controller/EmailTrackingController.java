package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.service.EmailTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Slf4j
public class EmailTrackingController {

    private final EmailTrackingService emailTrackingService;

    /**
     * Handle email open tracking pixel requests
     */
    @GetMapping("/open")
    public ResponseEntity<byte[]> trackEmailOpen(
            @RequestParam String msg,
            @RequestParam String to,
            @RequestParam(required = false) String campaign,
            @RequestParam(required = false) String user,
            @RequestHeader Map<String, String> headers) {

        try {
            log.info("Email open tracked - Message: {}, To: {}, Campaign: {}, User: {}",
                    msg, to, campaign, user);

            // Record the email open event
            emailTrackingService.recordEmailOpen(msg, to, campaign, user, headers);

            // Return a 1x1 transparent pixel
            byte[] pixel = createTransparentPixel();

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.IMAGE_PNG);
            responseHeaders.setContentLength(pixel.length);
            responseHeaders.setCacheControl("no-cache, no-store, must-revalidate");
            responseHeaders.setPragma("no-cache");
            responseHeaders.setExpires(0);

            return new ResponseEntity<>(pixel, responseHeaders, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error tracking email open for message: {}", msg, e);

            // Still return pixel even if tracking fails
            byte[] pixel = createTransparentPixel();
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.IMAGE_PNG);
            responseHeaders.setContentLength(pixel.length);

            return new ResponseEntity<>(pixel, responseHeaders, HttpStatus.OK);
        }
    }

    /**
     * Handle email click tracking
     */
    @GetMapping("/click")
    public ResponseEntity<Void> trackEmailClick(
            @RequestParam String url,
            @RequestParam String msg,
            @RequestParam String to,
            @RequestParam(required = false) String campaign,
            @RequestParam(required = false) String user,
            @RequestHeader Map<String, String> headers) {

        try {
            log.info("Email click tracked - URL: {}, Message: {}, To: {}, Campaign: {}, User: {}",
                    url, msg, to, campaign, user);

            // Record the email click event
            emailTrackingService.recordEmailClick(url, msg, to, campaign, user, headers);

            // Redirect to the actual URL
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", url)
                    .build();

        } catch (Exception e) {
            log.error("Error tracking email click for message: {}, URL: {}", msg, url, e);

            // Still redirect even if tracking fails
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", url)
                    .build();
        }
    }

    /**
     * Get email tracking statistics for a campaign
     */
    @GetMapping("/stats/campaign/{campaignId}")
    public ResponseEntity<Map<String, Object>> getCampaignStats(@PathVariable String campaignId) {
        try {
            Map<String, Object> stats = emailTrackingService.getCampaignStats(campaignId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting campaign stats for campaign: {}", campaignId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get campaign statistics"));
        }
    }

    /**
     * Get email tracking statistics for a user
     */
    @GetMapping("/stats/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserStats(@PathVariable String userId) {
        try {
            Map<String, Object> stats = emailTrackingService.getUserStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting user stats for user: {}", userId, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get user statistics"));
        }
    }

    /**
     * Create a 1x1 transparent PNG pixel
     */
    private byte[] createTransparentPixel() {
        // 1x1 transparent PNG
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
                0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89, 0x00, 0x00,
                0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00,
                0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00,
                0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
