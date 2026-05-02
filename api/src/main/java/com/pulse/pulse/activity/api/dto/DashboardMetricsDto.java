package com.pulse.pulse.activity.api.dto;

import java.util.Map;

public record DashboardMetricsDto(
        long githubCommits,
        long obsidianNotes,
        long slackMessages,
        long linearTickets,
        String greeting,
        String dateLabel,
        Map<String, Map<String, Long>> eventTypeBreakdown
) {}
