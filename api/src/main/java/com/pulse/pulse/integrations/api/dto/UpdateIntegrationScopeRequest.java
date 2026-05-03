package com.pulse.pulse.integrations.api.dto;

import java.util.List;

public record UpdateIntegrationScopeRequest(
        List<String> selectedResourceIds
) {
}
