package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.repository.EmailEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryTrackingService {

    private final EmailEventRepository emailEventRepository;

    /**
     * Record a successful email delivery
     */
    public void recordEmailDelivered(String messageId, String recipientEmail, String campaignId, String userId,
            String orgId) {
        try {
            EmailEvent deliveryEvent = EmailEvent.builder()
                    .messageId(messageId)
                    .emailAddress(recipientEmail)
                    .eventType(EmailEvent.EmailEventType.DELIVERY)
                    .timestamp(LocalDateTime.now())
                    .rawMessage("Gmail API success response")
                    .processed(true)
                    .campaignId(campaignId != null ? UUID.fromString(campaignId) : null)
                    .userId(userId)
                    .orgId(orgId != null ? UUID.fromString(orgId) : null)
                    .build();

            emailEventRepository.save(deliveryEvent);
            log.info("Recorded email delivery - MessageId: {}, Recipient: {}, Campaign: {}",
                    messageId, recipientEmail, campaignId);

        } catch (Exception e) {
            log.error("Failed to record email delivery for message: {}", messageId, e);
        }
    }

    /**
     * Record a failed email send
     */
    public void recordEmailRejected(String messageId, String recipientEmail, String campaignId, String userId,
            String orgId, String errorMessage) {
        try {
            EmailEvent rejectEvent = EmailEvent.builder()
                    .messageId(messageId != null ? messageId : "failed_" + System.currentTimeMillis())
                    .emailAddress(recipientEmail)
                    .eventType(EmailEvent.EmailEventType.REJECT)
                    .timestamp(LocalDateTime.now())
                    .rawMessage("Gmail API error: " + errorMessage)
                    .processed(true)
                    .campaignId(campaignId != null ? UUID.fromString(campaignId) : null)
                    .userId(userId)
                    .orgId(orgId != null ? UUID.fromString(orgId) : null)
                    .build();

            emailEventRepository.save(rejectEvent);
            log.info("Recorded email rejection - MessageId: {}, Recipient: {}, Campaign: {}, Error: {}",
                    messageId, recipientEmail, campaignId, errorMessage);

        } catch (Exception e) {
            log.error("Failed to record email rejection for message: {}", messageId, e);
        }
    }

    /**
     * Record a link click in an email
     */
    public void recordLinkClick(String messageId, String recipientEmail, String clickedUrl, String campaignId,
            String userId, String orgId) {
        try {
            // Use a default email if recipient email is null (for link tracking)
            String emailAddress = recipientEmail != null ? recipientEmail : "tracked@outreachly.com";

            EmailEvent clickEvent = EmailEvent.builder()
                    .messageId(messageId)
                    .emailAddress(emailAddress)
                    .eventType(EmailEvent.EmailEventType.CLICK)
                    .timestamp(LocalDateTime.now())
                    .clickedUrl(clickedUrl)
                    .rawMessage("Link clicked: " + clickedUrl)
                    .processed(true)
                    .campaignId(campaignId != null ? UUID.fromString(campaignId) : null)
                    .userId(userId)
                    .orgId(orgId != null ? UUID.fromString(orgId) : null)
                    .build();

            emailEventRepository.save(clickEvent);
            log.info("Recorded link click - MessageId: {}, Recipient: {}, URL: {}, Campaign: {}",
                    messageId, recipientEmail, clickedUrl, campaignId);

        } catch (Exception e) {
            log.error("Failed to record link click for message: {}", messageId, e);
        }
    }

    /**
     * Get delivery statistics for a campaign
     */
    public DeliveryStats getCampaignDeliveryStats(String campaignId) {
        try {
            UUID campaignUuid = UUID.fromString(campaignId);

            long totalDelivered = emailEventRepository.countByEventTypeAndCampaignId(
                    EmailEvent.EmailEventType.DELIVERY, campaignUuid);

            long totalRejected = emailEventRepository.countByEventTypeAndCampaignId(
                    EmailEvent.EmailEventType.REJECT, campaignUuid);

            long totalSent = totalDelivered + totalRejected;
            double deliveryRate = totalSent > 0 ? (double) totalDelivered / totalSent * 100 : 0;

            return DeliveryStats.builder()
                    .totalSent(totalSent)
                    .totalDelivered(totalDelivered)
                    .totalFailed(totalRejected)
                    .deliveryRate(deliveryRate)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get delivery stats for campaign: {}", campaignId, e);
            return DeliveryStats.builder()
                    .totalSent(0)
                    .totalDelivered(0)
                    .totalFailed(0)
                    .deliveryRate(0.0)
                    .build();
        }
    }

    /**
     * Get delivery statistics for a user
     */
    public DeliveryStats getUserDeliveryStats(String userId) {
        try {
            long totalDelivered = emailEventRepository.countByEventTypeAndUserId(
                    EmailEvent.EmailEventType.DELIVERY, userId);

            long totalRejected = emailEventRepository.countByEventTypeAndUserId(
                    EmailEvent.EmailEventType.REJECT, userId);

            long totalSent = totalDelivered + totalRejected;
            double deliveryRate = totalSent > 0 ? (double) totalDelivered / totalSent * 100 : 0;

            return DeliveryStats.builder()
                    .totalSent(totalSent)
                    .totalDelivered(totalDelivered)
                    .totalFailed(totalRejected)
                    .deliveryRate(deliveryRate)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get delivery stats for user: {}", userId, e);
            return DeliveryStats.builder()
                    .totalSent(0)
                    .totalDelivered(0)
                    .totalFailed(0)
                    .deliveryRate(0.0)
                    .build();
        }
    }

    /**
     * Get overall delivery statistics
     */
    public DeliveryStats getOverallDeliveryStats() {
        try {
            long totalDelivered = emailEventRepository.countByEventType(EmailEvent.EmailEventType.DELIVERY);
            long totalRejected = emailEventRepository.countByEventType(EmailEvent.EmailEventType.REJECT);

            long totalSent = totalDelivered + totalRejected;
            double deliveryRate = totalSent > 0 ? (double) totalDelivered / totalSent * 100 : 0;

            return DeliveryStats.builder()
                    .totalSent(totalSent)
                    .totalDelivered(totalDelivered)
                    .totalFailed(totalRejected)
                    .deliveryRate(deliveryRate)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get overall delivery stats", e);
            return DeliveryStats.builder()
                    .totalSent(0)
                    .totalDelivered(0)
                    .totalFailed(0)
                    .deliveryRate(0.0)
                    .build();
        }
    }

    /**
     * Get delivery trend data for a specific period with enhanced time calculations
     */
    public List<TrendData> getDeliveryTrends(int days, String userId, String campaignId) {
        try {
            List<TrendData> trends = new ArrayList<>();
            // Use UTC timezone for consistent date calculations
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            // Calculate the start date based on the requested period
            // For 7 days: today + past 6 days = 7 total days
            // For 30 days: today + past 29 days = 30 total days
            LocalDate startDate = today.minusDays(days - 1);

            log.info("Calculating trends from {} to {} ({} days total) - UTC timezone",
                    startDate, today, days);

            // Generate data for each day in the range
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(today)) {
                // Use UTC timezone for consistent time boundaries
                LocalDateTime startOfDay = currentDate.atStartOfDay().atOffset(ZoneOffset.UTC).toLocalDateTime();
                LocalDateTime endOfDay = currentDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toLocalDateTime();

                long delivered = 0;
                long failed = 0;
                long clicks = 0;

                if (userId != null) {
                    delivered = emailEventRepository.countByEventTypeAndUserIdAndTimestampBetween(
                            EmailEvent.EmailEventType.DELIVERY, userId, startOfDay, endOfDay);
                    failed = emailEventRepository.countByEventTypeAndUserIdAndTimestampBetween(
                            EmailEvent.EmailEventType.REJECT, userId, startOfDay, endOfDay);
                    clicks = emailEventRepository.countByEventTypeAndUserIdAndTimestampBetween(
                            EmailEvent.EmailEventType.CLICK, userId, startOfDay, endOfDay);
                } else if (campaignId != null) {
                    UUID campaignUuid = UUID.fromString(campaignId);
                    delivered = emailEventRepository.countByEventTypeAndCampaignIdAndTimestampBetween(
                            EmailEvent.EmailEventType.DELIVERY, campaignUuid, startOfDay, endOfDay);
                    failed = emailEventRepository.countByEventTypeAndCampaignIdAndTimestampBetween(
                            EmailEvent.EmailEventType.REJECT, campaignUuid, startOfDay, endOfDay);
                    clicks = emailEventRepository.countByEventTypeAndCampaignIdAndTimestampBetween(
                            EmailEvent.EmailEventType.CLICK, campaignUuid, startOfDay, endOfDay);
                } else {
                    delivered = emailEventRepository.countByEventTypeAndTimestampBetween(
                            EmailEvent.EmailEventType.DELIVERY, startOfDay, endOfDay);
                    failed = emailEventRepository.countByEventTypeAndTimestampBetween(
                            EmailEvent.EmailEventType.REJECT, startOfDay, endOfDay);
                    clicks = emailEventRepository.countByEventTypeAndTimestampBetween(
                            EmailEvent.EmailEventType.CLICK, startOfDay, endOfDay);
                }

                long totalSent = delivered + failed;
                double deliveryRate = totalSent > 0 ? (double) delivered / totalSent * 100 : 0;
                double clickRate = delivered > 0 ? (double) clicks / delivered * 100 : 0;

                trends.add(TrendData.builder()
                        .date(currentDate.toString())
                        .delivered(delivered)
                        .failed(failed)
                        .totalSent(totalSent)
                        .deliveryRate(deliveryRate)
                        .clicks(clicks)
                        .clickRate(clickRate)
                        .build());

                // Move to next day
                currentDate = currentDate.plusDays(1);
            }

            return trends;

        } catch (Exception e) {
            log.error("Failed to get delivery trends for {} days", days, e);
            return new ArrayList<>();
        }
    }

    /**
     * Get delivery trends for the current month
     */
    public List<TrendData> getCurrentMonthTrends(String userId, String campaignId) {
        // Use UTC timezone for consistent date calculations
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        long daysInMonth = ChronoUnit.DAYS.between(firstDayOfMonth, today) + 1;

        log.info("Getting current month trends from {} to {} ({} days) - UTC timezone",
                firstDayOfMonth, today, daysInMonth);

        return getDeliveryTrends((int) daysInMonth, userId, campaignId);
    }

    /**
     * Get current date information for debugging
     */
    public String getCurrentDateInfo() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate systemToday = LocalDate.now();

        return String.format("UTC Date: %s, System Date: %s, Timezone: %s",
                today, systemToday, ZoneOffset.UTC);
    }

    /**
     * Create test data for the chart
     */
    public void createTestData() {
        try {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            // Create test data for the last 7 days
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime timestamp = date.atTime(12, 0); // Noon UTC

                // Create some delivered emails
                for (int j = 0; j < 10 + (i * 5); j++) {
                    EmailEvent deliveryEvent = EmailEvent.builder()
                            .messageId("test_delivery_" + date + "_" + j)
                            .emailAddress("test" + j + "@example.com")
                            .eventType(EmailEvent.EmailEventType.DELIVERY)
                            .timestamp(timestamp)
                            .rawMessage("Test delivery event")
                            .processed(true)
                            .userId("test_user_123")
                            .build();
                    emailEventRepository.save(deliveryEvent);
                }

                // Create some failed emails (fewer)
                for (int j = 0; j < 2 + i; j++) {
                    EmailEvent rejectEvent = EmailEvent.builder()
                            .messageId("test_reject_" + date + "_" + j)
                            .emailAddress("invalid" + j + "@example.com")
                            .eventType(EmailEvent.EmailEventType.REJECT)
                            .timestamp(timestamp)
                            .rawMessage("Test reject event")
                            .processed(true)
                            .userId("test_user_123")
                            .build();
                    emailEventRepository.save(rejectEvent);
                }
            }

            log.info("Created test data for the last 7 days");
        } catch (Exception e) {
            log.error("Failed to create test data", e);
            throw e;
        }
    }

    /**
     * Delivery statistics data class
     */
    @lombok.Data
    @lombok.Builder
    public static class DeliveryStats {
        private long totalSent;
        private long totalDelivered;
        private long totalFailed;
        private double deliveryRate;
    }

    /**
     * Trend data for charts
     */
    @lombok.Data
    @lombok.Builder
    public static class TrendData {
        private String date;
        private long delivered;
        private long failed;
        private long totalSent;
        private double deliveryRate;
        private long clicks;
        private double clickRate;
    }
}
