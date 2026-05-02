package com.pulse.pulse.integrations.infrastructure.provider;

import com.pulse.pulse.integrations.application.IntegrationEventPayload;
import com.pulse.pulse.integrations.domain.UserIntegration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface IntegrationProvider {

    String getProviderName();

    boolean requiresOAuth();

    String buildOAuthUrl(String state, String redirectUri);

    String exchangeCode(String code, String redirectUri);

    List<IntegrationEventPayload> fetchRecentEvents(UserIntegration integration, LocalDateTime since);

    Map<String, Object> fetchMetadata(String accessToken);

    String getAccountLabel();

    String getAccountValue(Map<String, Object> metadata);

    String getEventsLabel();

    default String getRedirectUri() {
        return null;
    }
}
