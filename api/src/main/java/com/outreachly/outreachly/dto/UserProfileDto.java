package com.outreachly.outreachly.dto;

import java.util.List;
import java.util.Map;

public record UserProfileDto(
        String profileMarkdown,
        List<Map<String, Object>> knowledgeAreas,
        boolean onboardingCompleted
) {}
