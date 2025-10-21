package com.outreachly.outreachly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Resend configuration responses
 * Note: API key is masked for security
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendConfigResponse {
    private String fromEmail;
    private String fromName;
    private String domain;
    private Boolean isActive;
    private Boolean isDomainVerified;
    private String apiKeyMasked; // Only show last 4 characters
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
