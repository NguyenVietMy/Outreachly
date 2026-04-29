package com.pulse.pulse.service.integration;

import com.pulse.pulse.entity.IntegrationEvent;
import com.pulse.pulse.entity.UserIntegration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface IntegrationProvider {

    String getProviderName();

    boolean requiresOAuth();

    String buildOAuthUrl(String state, String redirectUri);

    String exchangeCode(String code, String redirectUri);

    List<IntegrationEvent> fetchRecentEvents(UserIntegration integration, LocalDateTime since);

    Map<String, Object> fetchMetadata(String accessToken);
}
