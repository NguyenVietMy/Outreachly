package com.pulse.pulse.personal.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DailySuggestionView(
        LocalDate suggestionDate,
        LocalDateTime generatedAt,
        List<Map<String, Object>> tasks) {
}
