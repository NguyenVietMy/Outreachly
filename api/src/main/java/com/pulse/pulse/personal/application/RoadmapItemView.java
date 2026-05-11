package com.pulse.pulse.personal.application;

import java.time.LocalDate;
import java.util.UUID;

public record RoadmapItemView(
        UUID id,
        String title,
        String description,
        String phase,
        LocalDate deadline,
        int focusRank,
        String aiRationale,
        String status
) {}
