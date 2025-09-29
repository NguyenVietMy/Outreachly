package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.GenerateTemplateRequest;
import com.outreachly.outreachly.dto.ImproveTemplateRequest;
import com.outreachly.outreachly.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final OpenAiService openAiService;

    @PostMapping("/generate-template")
    public Mono<ResponseEntity<Map<String, Object>>> generateTemplate(
            @RequestBody GenerateTemplateRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> errorResponse = Map.of("error", "Unauthorized");
            return Mono.just(ResponseEntity.status(401).body(errorResponse));
        }

        return openAiService.generateTemplate(
                request.getPrompt(),
                request.getPlatform(),
                request.getCategory(),
                request.getTone())
                .map(response -> {
                    try {
                        // Parse the JSON response from OpenAI
                        Map<String, Object> result = Map.of(
                                "success", true,
                                "data", response);
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        log.error("Error parsing OpenAI response: ", e);
                        Map<String, Object> errorResult = Map.of(
                                "success", false,
                                "error", "Failed to parse AI response");
                        return ResponseEntity.status(500).body(errorResult);
                    }
                })
                .onErrorReturn(ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "error", "Failed to generate template")));
    }

    @PostMapping("/improve-template")
    public Mono<ResponseEntity<Map<String, Object>>> improveTemplate(
            @RequestBody ImproveTemplateRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> errorResponse = Map.of("error", "Unauthorized");
            return Mono.just(ResponseEntity.status(401).body(errorResponse));
        }

        return openAiService.improveTemplate(
                request.getCurrentTemplate(),
                request.getPlatform(),
                request.getImprovementType())
                .map(response -> {
                    try {
                        Map<String, Object> result = Map.of(
                                "success", true,
                                "data", response);
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        log.error("Error parsing OpenAI response: ", e);
                        Map<String, Object> errorResult = Map.of(
                                "success", false,
                                "error", "Failed to parse AI response");
                        return ResponseEntity.status(500).body(errorResult);
                    }
                })
                .onErrorReturn(ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "error", "Failed to improve template")));
    }
}
