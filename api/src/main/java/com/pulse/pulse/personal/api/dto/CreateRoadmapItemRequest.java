package com.pulse.pulse.personal.api.dto;

import java.time.LocalDate;

public record CreateRoadmapItemRequest(String title, String description, LocalDate deadline) {}
