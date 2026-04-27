package com.outreachly.outreachly.dto;

import com.outreachly.outreachly.entity.Lead;
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
public class LeadDto {
    private UUID id;
    private UUID orgId;
    private UUID listId;
    private String firstName;
    private String lastName;
    private String domain;
    private String email;
    private String phone;
    private String position;
    private String positionRaw;
    private String seniority;
    private String department;
    private String linkedinUrl;
    private String twitter;
    private Integer confidenceScore;
    private Lead.EmailType emailType;
    private String customTextField;
    private String source;
    private Lead.VerifiedStatus verifiedStatus;
    private String enrichedJson;
    private String enrichmentHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LeadDto fromLead(Lead lead) {
        return LeadDto.builder()
                .id(lead.getId())
                .orgId(lead.getOrgId())
                .listId(lead.getListId())
                .firstName(lead.getFirstName())
                .lastName(lead.getLastName())
                .domain(lead.getDomain())
                .email(lead.getEmail())
                .phone(lead.getPhone())
                .position(lead.getPosition())
                .positionRaw(lead.getPositionRaw())
                .seniority(lead.getSeniority())
                .department(lead.getDepartment())
                .linkedinUrl(lead.getLinkedinUrl())
                .twitter(lead.getTwitter())
                .confidenceScore(lead.getConfidenceScore())
                .emailType(lead.getEmailType())
                .customTextField(lead.getCustomTextField())
                .source(lead.getSource())
                .verifiedStatus(lead.getVerifiedStatus())
                .enrichedJson(lead.getEnrichedJson())
                .enrichmentHistory(lead.getEnrichmentHistory())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }
}
