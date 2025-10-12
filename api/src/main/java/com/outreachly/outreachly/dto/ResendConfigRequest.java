package com.outreachly.outreachly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Resend configuration requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendConfigRequest {
    private String apiKey;
    private String fromEmail;
    private String fromName;
    private String domain;
}
