package com.pulse.pulse.personal.application;

import java.time.LocalDate;

public record GoalCommand(
        String title,
        String category,
        Integer targetValue,
        Integer currentValue,
        String unit,
        LocalDate deadline,
        String status) {
}
