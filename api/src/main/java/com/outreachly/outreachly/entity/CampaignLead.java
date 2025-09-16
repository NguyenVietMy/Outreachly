package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_lead", uniqueConstraints = @UniqueConstraint(columnNames = { "campaign_id", "lead_id" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignLead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "added_by")
    private Long addedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "TEXT CHECK (status IN ('active','paused','completed','removed')) DEFAULT 'active'")
    private CampaignLeadStatus status;

    @Column(name = "notes")
    private String notes;

    // Many-to-one relationship with Campaign
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campaign_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Campaign campaign;

    // Many-to-one relationship with Lead
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Lead lead;

    public enum CampaignLeadStatus {
        active, paused, completed, removed
    }
}
