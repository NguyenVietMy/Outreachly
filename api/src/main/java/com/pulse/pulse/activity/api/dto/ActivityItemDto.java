package com.pulse.pulse.activity.api.dto;

public record ActivityItemDto(
        String id,
        String title,
        String provider,
        String eventType,
        String timeAgo,
        String timestamp
) {}
