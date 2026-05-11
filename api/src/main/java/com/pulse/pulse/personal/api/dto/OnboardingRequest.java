package com.pulse.pulse.personal.api.dto;

public record OnboardingRequest(
        String targetRole,
        Integer graduationYear
) {}
