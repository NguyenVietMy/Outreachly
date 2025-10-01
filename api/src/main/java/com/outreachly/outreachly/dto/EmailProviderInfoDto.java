package com.outreachly.outreachly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailProviderInfoDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("isActive")
    private boolean isActive;

    @JsonProperty("isHealthy")
    private boolean isHealthy;

    @JsonProperty("config")
    private EmailProviderConfigDto config;

    @JsonProperty("description")
    private String description;
}

