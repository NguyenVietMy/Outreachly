package com.outreachly.outreachly.dto;

import java.util.Map;

public record ConnectApiKeyRequest(
        String apiKey,
        Map<String, Object> metadata
) {}
