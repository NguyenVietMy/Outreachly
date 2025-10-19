package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.repository.EmailEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final EmailEventRepository emailEventRepository;

    // Daily rate limit: 100 emails per day
    private static final int DAILY_RATE_LIMIT = 100;

    /**
     * Check if user has remaining email quota for today
     * 
     * @param userId User ID to check
     * @param orgId  Organization ID to check
     * @return RateLimitInfo containing remaining emails and reset time
     */
    public RateLimitInfo checkRateLimit(String userId, String orgId) {
        try {
            // Use local timezone instead of UTC to match how timestamps are stored
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            // Count emails sent today by this user/org
            long emailsSentToday = emailEventRepository.countByUserIdAndOrgIdAndTimestampBetweenAndEventType(
                    userId,
                    orgId != null ? java.util.UUID.fromString(orgId) : null,
                    startOfDay,
                    endOfDay,
                    EmailEvent.EmailEventType.DELIVERY);

            int remaining = Math.max(0, DAILY_RATE_LIMIT - (int) emailsSentToday);

            // Calculate reset time (next local midnight)
            LocalDateTime resetTime = endOfDay;
            long resetTimeSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), resetTime);

            return RateLimitInfo.builder()
                    .remaining(remaining)
                    .limit(DAILY_RATE_LIMIT)
                    .resetTime(resetTime)
                    .resetTimeSeconds(resetTimeSeconds)
                    .build();

        } catch (Exception e) {
            log.error("Error checking rate limit for user: {} org: {}", userId, orgId, e);
            return RateLimitInfo.builder()
                    .remaining(0)
                    .limit(DAILY_RATE_LIMIT)
                    .resetTime(LocalDate.now().plusDays(1).atStartOfDay())
                    .resetTimeSeconds(86400)
                    .build();
        }
    }

    /**
     * Check if user can send the specified number of emails
     * 
     * @param userId     User ID to check
     * @param orgId      Organization ID to check
     * @param emailCount Number of emails to send
     * @return true if user can send the emails, false otherwise
     */
    public boolean canSendEmails(String userId, String orgId, int emailCount) {
        RateLimitInfo rateLimitInfo = checkRateLimit(userId, orgId);
        boolean canSend = rateLimitInfo.getRemaining() >= emailCount;
        log.info("🚦 CAN SEND RESULT - Remaining: {} >= Requested: {} = {}",
                rateLimitInfo.getRemaining(), emailCount, canSend);
        return canSend;
    }

    /**
     * Get rate limit info for a specific user/organization
     * 
     * @param userId User ID
     * @param orgId  Organization ID
     * @return RateLimitInfo
     */
    public RateLimitInfo getRateLimitInfo(String userId, String orgId) {
        return checkRateLimit(userId, orgId);
    }

    /**
     * Rate limit information data class
     */
    @lombok.Data
    @lombok.Builder
    public static class RateLimitInfo {
        private int remaining;
        private int limit;
        private LocalDateTime resetTime;
        private long resetTimeSeconds;
    }
}
