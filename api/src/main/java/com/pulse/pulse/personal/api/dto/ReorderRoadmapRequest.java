package com.pulse.pulse.personal.api.dto;

import java.util.List;
import java.util.UUID;

public record ReorderRoadmapRequest(List<UUID> orderedIds) {}
