package com.pulse.pulse.dto;

import java.time.LocalDate;

public record UpdateGoalRequest(
        String title,
        String category,
        Integer targetValue,
        Integer currentValue,
        String unit,
        LocalDate deadline,
        String status
) {}
