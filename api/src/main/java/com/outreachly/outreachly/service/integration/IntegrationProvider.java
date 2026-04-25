package com.outreachly.outreachly.service.integration;

import com.outreachly.outreachly.entity.IntegrationEvent;
import com.outreachly.outreachly.entity.UserIntegration;

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
