package com.pulse.pulse.activity.application;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ActivityEventView(
        UUID id,
        String provider,
        String eventType,
        String title,
        String externalId,
        LocalDateTime eventTimestamp,
        Map<String, Object> rawPayload) {
}
