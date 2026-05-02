package com.pulse.pulse.integrations.api.dto;

import java.util.Map;

public record ConnectApiKeyRequest(
        String apiKey,
        Map<String, Object> metadata
) {}
