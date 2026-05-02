package com.pulse.pulse.activity.application;

import java.util.Map;

public record GitHubProjectSyncRequest(
        Long userId,
        String accessToken,
        Map<String, Object> metadata) {
}
