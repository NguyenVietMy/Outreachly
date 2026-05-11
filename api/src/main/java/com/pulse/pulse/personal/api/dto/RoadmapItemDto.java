package com.pulse.pulse.personal.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RoadmapItemDto(
        UUID id,
        String title,
        String description,
        String phase,
        LocalDate deadline,
        int focusRank,
        String aiRationale,
        String status
) {}
