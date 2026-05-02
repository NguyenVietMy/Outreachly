package com.pulse.pulse.activity.application;

import java.time.LocalDateTime;
import java.util.Map;

public record ActivityIngestItem(
        String eventType,
        String title,
        String externalId,
        LocalDateTime eventTimestamp,
        Map<String, Object> rawPayload) {
}
