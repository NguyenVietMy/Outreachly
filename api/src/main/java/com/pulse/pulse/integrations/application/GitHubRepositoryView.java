package com.pulse.pulse.integrations.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record GitHubRepositoryView(
        String repoFullName,
        String repoName,
        String description,
        String primaryLanguage,
        List<String> topics,
        Map<String, Object> languages,
        LocalDateTime pushedAt,
        Integer openIssuesCount,
        boolean fork,
        String defaultBranch,
        LocalDateTime lastSyncedAt) {
}
