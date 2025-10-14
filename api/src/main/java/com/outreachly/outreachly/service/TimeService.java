package com.outreachly.outreachly.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Service for handling timezone conversions and time calculations.
 * All times are stored in UTC in the database and converted for display.
 */
@Service
@Slf4j
public class TimeService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Get current time in UTC
     */
    public LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Get current time in user's timezone
     */
    public LocalDateTime nowInTimezone(String timezone) {
        try {
            ZoneOffset offset = parseUtcOffset(timezone);
            return LocalDateTime.now(offset);
        } catch (Exception e) {
            log.warn("Invalid timezone: {}, falling back to UTC", timezone);
            return nowUtc();
        }
    }

    /**
     * Convert UTC time to user's timezone for display
     */
    public LocalDateTime utcToUserTimezone(LocalDateTime utcTime, String userTimezone) {
        try {
            ZoneOffset offset = parseUtcOffset(userTimezone);
            return utcTime.atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(offset)
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("Invalid timezone: {}, returning UTC time", userTimezone);
            return utcTime;
        }
    }

    /**
     * Convert user's local time to UTC for storage
     */
    public LocalDateTime userTimezoneToUtc(LocalDateTime userTime, String userTimezone) {
        try {
            ZoneOffset offset = parseUtcOffset(userTimezone);
            return userTime.atZone(offset)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("Invalid timezone: {}, treating as UTC", userTimezone);
            return userTime;
        }
    }

    /**
     * Check if a checkpoint should be executed now (within 5-minute window)
     */
    public boolean isTimeToExecute(LocalDateTime scheduledTimeUtc, DayOfWeek scheduledDay) {
        LocalDateTime now = nowUtc();

        // Check if it's the right day
        if (now.getDayOfWeek() != scheduledDay) {
            return false;
        }

        // Check if we're within 5 minutes of scheduled time
        LocalTime scheduledTime = scheduledTimeUtc.toLocalTime();
        LocalTime nowTime = now.toLocalTime();

        // Create a 5-minute window around scheduled time
        LocalTime windowStart = scheduledTime.minusMinutes(5);
        LocalTime windowEnd = scheduledTime.plusMinutes(5);

        return !nowTime.isBefore(windowStart) && !nowTime.isAfter(windowEnd);
    }

    /**
     * Calculate next occurrence of a scheduled time
     */
    public LocalDateTime calculateNextScheduledTime(DayOfWeek dayOfWeek, LocalTime timeOfDay, String userTimezone) {
        try {
            ZoneOffset offset = parseUtcOffset(userTimezone);
            LocalDateTime nowInUserTz = LocalDateTime.now(offset);

            // Find next occurrence of the day
            LocalDateTime nextOccurrence = nowInUserTz.with(TemporalAdjusters.nextOrSame(dayOfWeek));

            // Set the time
            nextOccurrence = nextOccurrence.with(timeOfDay);

            // If the time has already passed today, move to next week
            if (nextOccurrence.isBefore(nowInUserTz)) {
                nextOccurrence = nextOccurrence.with(TemporalAdjusters.next(dayOfWeek));
            }

            // Convert to UTC for storage
            return nextOccurrence.atZone(offset)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();

        } catch (Exception e) {
            log.error("Error calculating next scheduled time for timezone: {}", userTimezone, e);
            // Fallback to UTC
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime nextOccurrence = now.with(TemporalAdjusters.nextOrSame(dayOfWeek));
            nextOccurrence = nextOccurrence.with(timeOfDay);

            if (nextOccurrence.isBefore(now)) {
                nextOccurrence = nextOccurrence.with(TemporalAdjusters.next(dayOfWeek));
            }

            return nextOccurrence;
        }
    }

    /**
     * Format time for display in user's timezone
     */
    public String formatTimeForDisplay(LocalTime time, String userTimezone) {
        try {
            ZoneOffset offset = parseUtcOffset(userTimezone);
            LocalDateTime today = LocalDate.now(offset).atTime(time);
            LocalDateTime utcTime = today.atZone(offset)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();

            LocalDateTime displayTime = utcTime.atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(offset)
                    .toLocalDateTime();

            return displayTime.toLocalTime().format(TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Error formatting time for timezone: {}", userTimezone);
            return time.format(TIME_FORMATTER);
        }
    }

    /**
     * Get list of common timezones for user selection
     */
    public List<String> getCommonTimezones() {
        return List.of(
                "UTC−12", "UTC−11", "UTC−10", "UTC−9", "UTC−8", "UTC−7", "UTC−6", "UTC−5", "UTC−4", "UTC−3", "UTC−2",
                "UTC−1",
                "UTC±0", "UTC+1", "UTC+2", "UTC+3", "UTC+4", "UTC+5", "UTC+6", "UTC+7", "UTC+8", "UTC+9", "UTC+10",
                "UTC+11", "UTC+12", "UTC+13", "UTC+14");
    }

    /**
     * Validate if timezone string is valid
     */
    public boolean isValidTimezone(String timezone) {
        try {
            parseUtcOffset(timezone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get timezone offset from UTC (e.g., "+05:00", "-08:00")
     */
    public String getTimezoneOffset(String timezone) {
        try {
            ZoneOffset offset = parseUtcOffset(timezone);
            return offset.toString();
        } catch (Exception e) {
            log.warn("Error getting offset for timezone: {}", timezone);
            return "+00:00";
        }
    }

    /**
     * Parse UTC offset string (e.g., "UTC+5", "UTC−8", "UTC±0") to ZoneOffset
     */
    private ZoneOffset parseUtcOffset(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            throw new IllegalArgumentException("Timezone cannot be null or empty");
        }

        String trimmed = timezone.trim();

        // Handle UTC±0 case
        if ("UTC±0".equals(trimmed)) {
            return ZoneOffset.UTC;
        }

        // Handle UTC+0 case
        if ("UTC+0".equals(trimmed)) {
            return ZoneOffset.UTC;
        }

        // Parse UTC±X format
        if (trimmed.startsWith("UTC")) {
            String offsetPart = trimmed.substring(3);

            if (offsetPart.startsWith("+")) {
                int hours = Integer.parseInt(offsetPart.substring(1));
                return ZoneOffset.ofHours(hours);
            } else if (offsetPart.startsWith("−") || offsetPart.startsWith("-") || offsetPart.startsWith("?")) {
                // Handle Unicode minus (U+2212), regular minus, and corrupted minus
                String hoursStr = offsetPart.substring(1);
                int hours = Integer.parseInt(hoursStr);
                return ZoneOffset.ofHours(-hours);
            }
        }

        throw new IllegalArgumentException("Invalid UTC offset format: " + timezone);
    }
}
