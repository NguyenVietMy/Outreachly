package com.pulse.pulse.integrations.api.dto;

import java.util.List;

public record IntegrationDto(
        String provider,
        String status,
        boolean supportsSync,
        String accountLabel,
        String accountValue,
        String eventsLabel,
        String eventsValue,
        String lastSyncedAt,
        List<Integer> activitySparkline,
        int consecutiveFailures,
        boolean autoSyncEnabled
) {}
