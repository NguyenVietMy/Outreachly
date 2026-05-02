package com.pulse.pulse.personal.application;

import java.util.UUID;

public record AiTaskView(
        UUID id,
        String axis,
        String sectionId,
        String title,
        String description,
        boolean completed,
        String source,
        int priority,
        int orderIndex) {
}
