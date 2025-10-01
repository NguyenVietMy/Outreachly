package com.outreachly.outreachly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("plan")
    private String plan;

    @JsonProperty("description")
    private String description;

    @JsonProperty("billingEmail")
    private String billingEmail;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}