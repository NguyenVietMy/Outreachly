package com.outreachly.outreachly.dto;

import java.util.UUID;

public record AiTaskDto(
        UUID id,
        String axis,
        String sectionId,
        String title,
        String description,
        boolean completed,
        String source,
        int priority,
        int orderIndex
) {}
