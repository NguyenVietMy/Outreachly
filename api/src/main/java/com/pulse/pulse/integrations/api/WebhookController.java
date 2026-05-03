package com.pulse.pulse.integrations.api;

import com.pulse.pulse.integrations.application.IntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class WebhookController {

    private final IntegrationService integrationService;

    public WebhookController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(@RequestHeader Map<String, String> headers,
                                                    @RequestBody byte[] body) {
        try {
            integrationService.handleGitHubWebhook(normalizeHeaders(headers), body);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Rejected GitHub webhook: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping(value = "/slack", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleSlackWebhook(@RequestHeader Map<String, String> headers,
                                                @RequestBody byte[] body) {
        try {
            IntegrationService.SlackWebhookResult result =
                    integrationService.handleSlackWebhook(normalizeHeaders(headers), body);
            if (result.challenge()) {
                return ResponseEntity.ok(Map.of("challenge", result.challengeValue()));
            }
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Rejected Slack webhook: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/linear")
    public ResponseEntity<Void> handleLinearWebhook(@RequestHeader Map<String, String> headers,
                                                    @RequestBody byte[] body) {
        try {
            integrationService.handleLinearWebhook(normalizeHeaders(headers), body);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Rejected Linear webhook: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        return new LinkedHashMap<>(headers);
    }
}
