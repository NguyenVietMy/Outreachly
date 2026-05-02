package com.pulse.pulse.personal.application;

import java.time.LocalDate;
import java.util.UUID;

public record UserGoalView(
        UUID id,
        String title,
        String category,
        Integer targetValue,
        Integer currentValue,
        String unit,
        LocalDate deadline,
        String status) {
}
