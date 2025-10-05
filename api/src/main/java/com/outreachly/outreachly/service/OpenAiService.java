package com.outreachly.outreachly.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class OpenAiService {

    private final WebClient webClient;

    public OpenAiService(@Value("${OPENAI_API_KEY}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<String> generateTemplate(String prompt, String platform, String category, String tone) {
        String systemPrompt = buildSystemPrompt(platform, category, tone);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-3.5-turbo");
        requestBody.put("messages", new Object[] {
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
        });
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.7);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    JsonNode choices = response.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode message = choices.get(0).get("message");
                        if (message != null) {
                            return message.get("content").asText();
                        }
                    }
                    throw new RuntimeException("No response from OpenAI");
                })
                .doOnError(error -> log.error("OpenAI API error: ", error));
    }

    private String buildSystemPrompt(String platform, String category, String tone) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert email and LinkedIn message writer for business outreach. ");
        prompt.append("Generate professional, engaging templates that convert prospects into customers.\n\n");

        prompt.append("Platform: ").append(platform).append("\n");
        if (category != null && !category.isEmpty()) {
            prompt.append("Category: ").append(category).append("\n");
        }
        if (tone != null && !tone.isEmpty()) {
            prompt.append("Tone: ").append(tone).append("\n");
        }

        prompt.append("\nRequirements:\n");
        prompt.append(
                "- ONLY use these exact variables for personalization: {{first_name}}, {{last_name}}, {{company}}, {{title}}\n");
        prompt.append("- DO NOT use any other variables like {{industry}}, {{position}}, {{department}}, etc.\n");
        prompt.append("- Keep emails under 150 words for better engagement\n");
        prompt.append("- Make subject lines compelling and under 50 characters\n");
        prompt.append("- Include a clear call-to-action\n");
        prompt.append("- Be professional but conversational\n");
        prompt.append("- Avoid spam trigger words\n\n");

        if ("EMAIL".equals(platform)) {
            prompt.append("Return your response as JSON with this exact format:\n");
            prompt.append("{\n");
            prompt.append("  \"subject\": \"Your subject line here\",\n");
            prompt.append("  \"body\": \"Your email body here\"\n");
            prompt.append("}\n");
        } else {
            prompt.append("Return your response as JSON with this exact format:\n");
            prompt.append("{\n");
            prompt.append("  \"body\": \"Your LinkedIn message here\"\n");
            prompt.append("}\n");
        }

        return prompt.toString();
    }

    public Mono<String> improveTemplate(String currentTemplate, String platform, String improvementType) {
        String systemPrompt = buildImprovementPrompt(platform, improvementType);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-3.5-turbo");
        requestBody.put("messages", new Object[] {
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", "Current template:\n" + currentTemplate)
        });
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.7);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    JsonNode choices = response.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode message = choices.get(0).get("message");
                        if (message != null) {
                            return message.get("content").asText();
                        }
                    }
                    throw new RuntimeException("No response from OpenAI");
                })
                .doOnError(error -> log.error("OpenAI API error: ", error));
    }

    private String buildImprovementPrompt(String platform, String improvementType) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert email and LinkedIn message optimizer. ");
        prompt.append("Improve the provided template based on the improvement type.\n\n");

        prompt.append("Platform: ").append(platform).append("\n");
        prompt.append("Improvement Type: ").append(improvementType).append("\n\n");

        switch (improvementType.toLowerCase()) {
            case "shorter":
                prompt.append("Make the template more concise while keeping the key message and call-to-action.\n");
                break;
            case "longer":
                prompt.append(
                        "Expand the template with more value proposition and details while maintaining engagement.\n");
                break;
            case "more professional":
                prompt.append("Make the tone more formal and business-appropriate.\n");
                break;
            case "more casual":
                prompt.append("Make the tone more friendly and conversational.\n");
                break;
            case "higher conversion":
                prompt.append("Optimize for higher conversion rates with better CTAs and value propositions.\n");
                break;
            default:
                prompt.append("Improve the overall quality and effectiveness of the template.\n");
        }

        prompt.append("\nReturn your response as JSON with the same format as the original template.\n");

        return prompt.toString();
    }
}
