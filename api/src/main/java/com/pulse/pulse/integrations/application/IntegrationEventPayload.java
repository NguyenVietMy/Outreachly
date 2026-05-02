package com.pulse.pulse.integrations.application;

import java.time.LocalDateTime;
import java.util.Map;

public record IntegrationEventPayload(
        String eventType,
        String title,
        String externalId,
        LocalDateTime eventTimestamp,
        Map<String, Object> rawPayload) {
}
