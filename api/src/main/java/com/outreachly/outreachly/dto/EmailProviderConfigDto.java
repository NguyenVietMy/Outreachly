package com.outreachly.outreachly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailProviderConfigDto {

    @NotBlank(message = "API key is required")
    @JsonProperty("apiKey")
    private String apiKey;

    @Email(message = "From email must be valid")
    @NotBlank(message = "From email is required")
    @JsonProperty("fromEmail")
    private String fromEmail;

    @NotBlank(message = "From name is required")
    @JsonProperty("fromName")
    private String fromName;

    // Optional fields for different providers
    @JsonProperty("region")
    private String region;

    @JsonProperty("host")
    private String host;

    @JsonProperty("port")
    private Integer port;

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;
}

