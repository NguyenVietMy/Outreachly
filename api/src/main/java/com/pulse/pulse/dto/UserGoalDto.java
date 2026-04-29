package com.pulse.pulse.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UserGoalDto(
        UUID id,
        String title,
        String category,
        Integer targetValue,
        Integer currentValue,
        String unit,
        LocalDate deadline,
        String status
) {}
