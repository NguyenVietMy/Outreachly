package com.outreachly.outreachly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${WEBHOOK_ENABLED:false}")
    private boolean webhookEnabled;

    @Value("${WEBHOOK_URL:}")
    private String webhookUrl;

    @Value("${WEBHOOK_SECRET:}")
    private String webhookSecret;

    public void sendEnrichmentCompleted(UUID leadId, UUID userId, String action, Map<String, Object> data) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "enrichment.completed");
            payload.put("leadId", leadId != null ? leadId.toString() : null);
            payload.put("userId", userId != null ? userId.toString() : null);
            payload.put("action", action);
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (webhookSecret != null && !webhookSecret.isBlank()) {
                headers.set("X-Webhook-Secret", webhookSecret);
            }

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload),
                    headers);

            restTemplate.postForObject(webhookUrl, request, String.class);
            log.info("Webhook sent for enrichment completion: leadId={}, action={}", leadId, action);

        } catch (Exception e) {
            log.warn("Failed to send webhook for enrichment completion: leadId={}, error={}", leadId, e.getMessage());
        }
    }

    public void sendEnrichmentFailed(UUID leadId, UUID userId, String error) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "enrichment.failed");
            payload.put("leadId", leadId != null ? leadId.toString() : null);
            payload.put("userId", userId != null ? userId.toString() : null);
            payload.put("error", error);
            payload.put("timestamp", System.currentTimeMillis());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (webhookSecret != null && !webhookSecret.isBlank()) {
                headers.set("X-Webhook-Secret", webhookSecret);
            }

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload),
                    headers);

            restTemplate.postForObject(webhookUrl, request, String.class);
            log.info("Webhook sent for enrichment failure: leadId={}", leadId);

        } catch (Exception e) {
            log.warn("Failed to send webhook for enrichment failure: leadId={}, error={}", leadId, e.getMessage());
        }
    }
}
