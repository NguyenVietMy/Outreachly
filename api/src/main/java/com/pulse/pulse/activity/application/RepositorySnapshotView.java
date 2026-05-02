package com.pulse.pulse.activity.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record RepositorySnapshotView(
        Long userId,
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
        LocalDateTime lastSyncedAt,
        List<Map<String, Object>> recentCommits,
        List<Map<String, Object>> openPrs,
        List<Map<String, Object>> assignedIssues,
        String readmeContent) {
}
