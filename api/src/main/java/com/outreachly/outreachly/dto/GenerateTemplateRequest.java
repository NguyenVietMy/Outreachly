package com.outreachly.outreachly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GenerateTemplateRequest {
    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("platform")
    private String platform;

    @JsonProperty("category")
    private String category;

    @JsonProperty("tone")
    private String tone;

    // Default constructor
    public GenerateTemplateRequest() {
    }

    // Getters and setters
    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }
}
