package com.outreachly.outreachly.dto;

import java.util.List;
import java.util.Map;

public record UserProfileDto(
        String profileMarkdown,
        List<Map<String, Object>> knowledgeAreas,
        boolean onboardingCompleted,
        String leetcodeUsername,
        Map<String, Object> leetcodeStats,
        String resumeFilename,
        boolean hasResume,
        Map<String, Object> systemDesignAnswers,
        Map<String, Object> coreCsAnswers,
        Map<String, Object> axisScores,
        Map<String, Object> resumeScoreBreakdown,
        String targetRole,
        Integer graduationYear
) {}
