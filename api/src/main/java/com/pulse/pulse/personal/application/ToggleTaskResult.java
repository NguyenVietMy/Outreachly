package com.pulse.pulse.personal.application;

import java.util.Map;

public record ToggleTaskResult(AiTaskView task, Map<String, Object> axisScores) {
}
