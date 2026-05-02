package com.pulse.pulse.activity.application;

import java.util.List;

public record ActivityIngestCommand(
        Long userId,
        String provider,
        List<ActivityIngestItem> items) {
}
