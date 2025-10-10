package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.repository.EmailEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTrackingService {

    private final EmailEventRepository emailEventRepository;

    /**
     * Record an email open event
     */
    @Transactional
    public void recordEmailOpen(String messageId, String recipientEmail, String campaignId,
            String userId, Map<String, String> headers) {
        try {
            // Extract useful information from headers
            String userAgent = headers.getOrDefault("user-agent", "unknown");
            String ipAddress = headers.getOrDefault("x-forwarded-for",
                    headers.getOrDefault("x-real-ip", "unknown"));
            String referer = headers.getOrDefault("referer", "");

            log.info("Recording email open - MessageId: {}, Recipient: {}, Campaign: {}, " +
                    "User: {}, IP: {}, UserAgent: {}",
                    messageId, recipientEmail, campaignId, userId, ipAddress, userAgent);

            // Create tracking data string with additional info
            String trackingData = String.format(
                    "campaign=%s,user=%s,ip=%s,userAgent=%s,referer=%s",
                    campaignId != null ? campaignId : "unknown",
                    userId != null ? userId : "unknown",
                    ipAddress,
                    userAgent,
                    referer);

            // Store in database
            EmailEvent emailEvent = EmailEvent.builder()
                    .messageId(messageId)
                    .emailAddress(recipientEmail)
                    .eventType(EmailEvent.EmailEventType.OPEN)
                    .timestamp(LocalDateTime.now())
                    .rawMessage(trackingData)
                    .processed(true)
                    .build();

            emailEventRepository.save(emailEvent);

            log.info("Email open event saved successfully for message: {}", messageId);

        } catch (Exception e) {
            log.error("Failed to record email open for message: {}", messageId, e);
        }
    }

    /**
     * Record an email click event
     */
    @Transactional
    public void recordEmailClick(String url, String messageId, String recipientEmail,
            String campaignId, String userId, Map<String, String> headers) {
        try {
            // Extract useful information from headers
            String userAgent = headers.getOrDefault("user-agent", "unknown");
            String ipAddress = headers.getOrDefault("x-forwarded-for",
                    headers.getOrDefault("x-real-ip", "unknown"));

            log.info("Recording email click - URL: {}, MessageId: {}, Recipient: {}, " +
                    "Campaign: {}, User: {}, IP: {}, UserAgent: {}",
                    url, messageId, recipientEmail, campaignId, userId, ipAddress, userAgent);

            // Create tracking data string with additional info
            String trackingData = String.format(
                    "url=%s,campaign=%s,user=%s,ip=%s,userAgent=%s",
                    url,
                    campaignId != null ? campaignId : "unknown",
                    userId != null ? userId : "unknown",
                    ipAddress,
                    userAgent);

            // Store in database
            EmailEvent emailEvent = EmailEvent.builder()
                    .messageId(messageId)
                    .emailAddress(recipientEmail)
                    .eventType(EmailEvent.EmailEventType.CLICK)
                    .timestamp(LocalDateTime.now())
                    .rawMessage(trackingData)
                    .processed(true)
                    .build();

            emailEventRepository.save(emailEvent);

            log.info("Email click event saved successfully for message: {}, URL: {}", messageId, url);

        } catch (Exception e) {
            log.error("Failed to record email click for message: {}, URL: {}", messageId, url, e);
        }
    }

    /**
     * Get email open statistics for a campaign
     */
    public Map<String, Object> getCampaignStats(String campaignId) {
        try {
            // Count total opens for this campaign
            long totalOpens = emailEventRepository.findAll().stream()
                    .filter(event -> event.getEventType() == EmailEvent.EmailEventType.OPEN)
                    .filter(event -> event.getRawMessage() != null
                            && event.getRawMessage().contains("campaign=" + campaignId))
                    .count();

            // Count total clicks for this campaign
            long totalClicks = emailEventRepository.findAll().stream()
                    .filter(event -> event.getEventType() == EmailEvent.EmailEventType.CLICK)
                    .filter(event -> event.getRawMessage() != null
                            && event.getRawMessage().contains("campaign=" + campaignId))
                    .count();

            // For now, we don't have a direct way to count total sent emails
            // This would need to be tracked separately or inferred from other data
            long totalSent = Math.max(totalOpens, totalClicks); // Rough estimate

            double openRate = totalSent > 0 ? (double) totalOpens / totalSent : 0.0;
            double clickRate = totalSent > 0 ? (double) totalClicks / totalSent : 0.0;

            return Map.of(
                    "campaignId", campaignId,
                    "totalSent", totalSent,
                    "totalOpened", totalOpens,
                    "totalClicked", totalClicks,
                    "openRate", Math.round(openRate * 100.0) / 100.0,
                    "clickRate", Math.round(clickRate * 100.0) / 100.0);
        } catch (Exception e) {
            log.error("Error getting campaign stats for campaign: {}", campaignId, e);
            return Map.of(
                    "campaignId", campaignId,
                    "totalSent", 0,
                    "totalOpened", 0,
                    "totalClicked", 0,
                    "openRate", 0.0,
                    "clickRate", 0.0);
        }
    }

    /**
     * Get email open statistics for a user
     */
    public Map<String, Object> getUserStats(String userId) {
        try {
            // Count total opens for this user
            long totalOpens = emailEventRepository.findAll().stream()
                    .filter(event -> event.getEventType() == EmailEvent.EmailEventType.OPEN)
                    .filter(event -> event.getRawMessage() != null && event.getRawMessage().contains("user=" + userId))
                    .count();

            // Count total clicks for this user
            long totalClicks = emailEventRepository.findAll().stream()
                    .filter(event -> event.getEventType() == EmailEvent.EmailEventType.CLICK)
                    .filter(event -> event.getRawMessage() != null && event.getRawMessage().contains("user=" + userId))
                    .count();

            // For now, we don't have a direct way to count total sent emails
            // This would need to be tracked separately or inferred from other data
            long totalSent = Math.max(totalOpens, totalClicks); // Rough estimate

            double openRate = totalSent > 0 ? (double) totalOpens / totalSent : 0.0;
            double clickRate = totalSent > 0 ? (double) totalClicks / totalSent : 0.0;

            return Map.of(
                    "userId", userId,
                    "totalSent", totalSent,
                    "totalOpened", totalOpens,
                    "totalClicked", totalClicks,
                    "openRate", Math.round(openRate * 100.0) / 100.0,
                    "clickRate", Math.round(clickRate * 100.0) / 100.0);
        } catch (Exception e) {
            log.error("Error getting user stats for user: {}", userId, e);
            return Map.of(
                    "userId", userId,
                    "totalSent", 0,
                    "totalOpened", 0,
                    "totalClicked", 0,
                    "openRate", 0.0,
                    "clickRate", 0.0);
        }
    }
}
