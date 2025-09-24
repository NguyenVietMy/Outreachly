package com.outreachly.outreachly.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.service.EmailEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/email/webhook")
@RequiredArgsConstructor
@Slf4j
public class EmailWebhookController {

    private final EmailEventService emailEventService;
    private final ObjectMapper objectMapper;

    @PostMapping("/ses")
    public ResponseEntity<String> handleSesWebhook(@RequestBody String payload) {
        try {
            log.info("Received SES webhook: {}", payload);

            JsonNode root = objectMapper.readTree(payload);
            String notificationType = root.get("Type").asText();

            if ("Notification".equals(notificationType)) {
                String message = root.get("Message").asText();
                JsonNode messageNode = objectMapper.readTree(message);

                String eventType = messageNode.get("eventType").asText();
                String messageId = messageNode.get("mail").get("messageId").asText();

                // Extract recipient email
                JsonNode destination = messageNode.get("mail").get("destination");
                String emailAddress = destination.isArray() && destination.size() > 0
                        ? destination.get(0).asText()
                        : destination.asText();

                EmailEvent.EmailEventType emailEventType = mapSesEventType(eventType);

                EmailEvent emailEvent = EmailEvent.builder()
                        .messageId(messageId)
                        .emailAddress(emailAddress)
                        .eventType(emailEventType)
                        .timestamp(LocalDateTime.now())
                        .rawMessage(payload)
                        .processed(false)
                        .build();

                // Add bounce/complaint specific data
                if ("bounce".equals(eventType)) {
                    JsonNode bounce = messageNode.get("bounce");
                    if (bounce != null) {
                        emailEvent.setBounceType(bounce.get("bounceType").asText());
                        emailEvent.setBounceSubtype(bounce.get("bounceSubType").asText());
                    }
                } else if ("complaint".equals(eventType)) {
                    JsonNode complaint = messageNode.get("complaint");
                    if (complaint != null) {
                        emailEvent.setComplaintFeedbackType(complaint.get("complaintFeedbackType").asText());
                    }
                }

                emailEventService.saveEmailEvent(emailEvent);
                log.info("Processed SES event: {} for email: {}", eventType, emailAddress);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing SES webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error processing webhook");
        }
    }

    private EmailEvent.EmailEventType mapSesEventType(String sesEventType) {
        return switch (sesEventType.toLowerCase()) {
            case "bounce" -> EmailEvent.EmailEventType.BOUNCE;
            case "complaint" -> EmailEvent.EmailEventType.COMPLAINT;
            case "delivery" -> EmailEvent.EmailEventType.DELIVERY;
            case "open" -> EmailEvent.EmailEventType.OPEN;
            case "click" -> EmailEvent.EmailEventType.CLICK;
            case "reject" -> EmailEvent.EmailEventType.REJECT;
            default -> EmailEvent.EmailEventType.DELIVERY; // Default fallback
        };
    }
}
