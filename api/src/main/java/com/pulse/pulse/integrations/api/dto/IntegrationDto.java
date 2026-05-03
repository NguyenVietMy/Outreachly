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
        String lastActivityLabel,
        String scopeSummary,
        List<String> selectedResourceIds,
        List<Integer> activitySparkline,
        String webhookStatus,
        String webhookError
) {}
