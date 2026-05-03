package com.pulse.pulse.integrations.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record IntegrationView(
        Long userId,
        String provider,
        String status,
        Map<String, Object> metadata,
        LocalDateTime lastSyncedAt,
        LocalDateTime lastWebhookReceivedAt,
        String webhookStatus,
        String lastWebhookError,
        String accountLabel,
        String accountValue,
        String eventsLabel,
        String eventsValue,
        String lastActivityLabel,
        String scopeSummary,
        List<String> selectedResourceIds,
        List<Integer> sparkline) {
}
