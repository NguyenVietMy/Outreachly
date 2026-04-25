package com.outreachly.outreachly.dto;

import java.util.List;
import java.util.Map;

public record OnboardingRequest(
        List<Map<String, Object>> knowledgeAreas,
        List<GoalInput> goals,
        String bio
) {
    public record GoalInput(
            String title,
            String category,
            Integer targetValue,
            String unit
    ) {}
}
