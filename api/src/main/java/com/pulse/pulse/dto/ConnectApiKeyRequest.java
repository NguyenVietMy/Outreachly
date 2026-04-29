package com.pulse.pulse.dto;

import java.util.Map;

public record ConnectApiKeyRequest(
        String apiKey,
        Map<String, Object> metadata
) {}
