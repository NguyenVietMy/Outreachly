package com.outreachly.outreachly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationSettingsDto {

    @NotBlank(message = "Email provider is required")
    @JsonProperty("emailProvider")
    private String emailProvider;

    @Valid
    @NotNull(message = "Email provider configuration is required")
    @JsonProperty("emailProviderConfig")
    private EmailProviderConfigDto emailProviderConfig;

    @JsonProperty("notificationSettings")
    private Map<String, Object> notificationSettings;

    @JsonProperty("featureFlags")
    private Map<String, Object> featureFlags;
}
