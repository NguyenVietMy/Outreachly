package com.outreachly.outreachly.dto;

import java.util.List;

public record IntegrationDto(
        String provider,
        String status,
        String accountLabel,
        String accountValue,
        String eventsLabel,
        String eventsValue,
        String lastSyncedAt,
        List<Integer> activitySparkline
) {}
