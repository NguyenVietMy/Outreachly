package com.outreachly.outreachly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ImproveTemplateRequest {
    @JsonProperty("currentTemplate")
    private String currentTemplate;

    @JsonProperty("platform")
    private String platform;

    @JsonProperty("improvementType")
    private String improvementType;

    // Default constructor
    public ImproveTemplateRequest() {
    }

    // Getters and setters
    public String getCurrentTemplate() {
        return currentTemplate;
    }

    public void setCurrentTemplate(String currentTemplate) {
        this.currentTemplate = currentTemplate;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getImprovementType() {
        return improvementType;
    }

    public void setImprovementType(String improvementType) {
        this.improvementType = improvementType;
    }
}
