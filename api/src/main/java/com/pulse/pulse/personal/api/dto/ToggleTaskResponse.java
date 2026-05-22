package com.pulse.pulse.personal.api.dto;

import java.util.Map;

public record ToggleTaskResponse(AiTaskDto task, Map<String, Object> axisScores) {
}
