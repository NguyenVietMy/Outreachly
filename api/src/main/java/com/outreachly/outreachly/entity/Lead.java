package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "list_id")
    private UUID listId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String company;
    private String domain;
    private String title;
    private String email;
    private String phone;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    private String country;
    private String state;
    private String city;

    @Column(name = "custom_text_field")
    private String customTextField;

    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "verified_status", columnDefinition = "TEXT CHECK (verified_status IN ('unknown','valid','risky','invalid')) DEFAULT 'unknown'")
    private VerifiedStatus verifiedStatus;

    @Column(name = "enriched_json", columnDefinition = "jsonb DEFAULT '{}'::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String enrichedJson;

    @Column(name = "enrichment_history", columnDefinition = "jsonb DEFAULT '[]'::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String enrichmentHistory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum VerifiedStatus {
        unknown, valid, risky, invalid
    }
}
