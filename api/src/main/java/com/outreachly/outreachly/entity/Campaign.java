package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "TEXT CHECK (status IN ('active','paused','completed','inactive')) DEFAULT 'active'")
    private CampaignStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Many-to-many relationship with Lead through CampaignLead join table
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<CampaignLead> campaignLeads = new ArrayList<>();

    // Helper method to get leads
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<Lead> getLeads() {
        return campaignLeads.stream()
                .filter(cl -> cl.getStatus() != CampaignLead.CampaignLeadStatus.removed)
                .map(CampaignLead::getLead)
                .toList();
    }

    public enum CampaignStatus {
        active, paused, completed, inactive
    }
}
