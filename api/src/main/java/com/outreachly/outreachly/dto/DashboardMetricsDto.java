package com.outreachly.outreachly.dto;

public record DashboardMetricsDto(
        long githubCommits,
        long obsidianNotes,
        long slackMessages,
        long linearTickets,
        String greeting,
        String dateLabel
) {}
