package com.outreachly.outreachly.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EmailRequest {
    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Recipients are required")
    private List<@Email(message = "Invalid email format") String> recipients;

    private String templateId;
    private Map<String, Object> templateData;
    private boolean html = true;
    private String replyTo;
    private String campaignId;
}
