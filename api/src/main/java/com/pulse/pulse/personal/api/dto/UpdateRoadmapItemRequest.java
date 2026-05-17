package com.pulse.pulse.personal.api.dto;

import java.time.LocalDate;

public record UpdateRoadmapItemRequest(String title, String description, LocalDate deadline) {}
