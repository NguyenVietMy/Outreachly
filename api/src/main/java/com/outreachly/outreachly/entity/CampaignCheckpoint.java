package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaign_checkpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;

    @Column(name = "email_template_id")
    private UUID emailTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_provider", nullable = false)
    @Builder.Default
    private EmailProvider emailProvider = EmailProvider.GMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CheckpointStatus status = CheckpointStatus.pending;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Many-to-one relationship with Campaign
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Campaign campaign;

    // One-to-many relationship with CampaignCheckpointLead
    @OneToMany(mappedBy = "checkpoint", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<CampaignCheckpointLead> checkpointLeads = new ArrayList<>();

    // Helper method to get leads for this checkpoint
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<Lead> getLeads() {
        return checkpointLeads.stream()
                .map(CampaignCheckpointLead::getLead)
                .toList();
    }

    // Helper method to get pending leads
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<CampaignCheckpointLead> getPendingLeads() {
        return checkpointLeads.stream()
                .filter(cl -> cl.getStatus() == CampaignCheckpointLead.DeliveryStatus.pending)
                .toList();
    }

    // Helper method to get sent leads
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<CampaignCheckpointLead> getSentLeads() {
        return checkpointLeads.stream()
                .filter(cl -> cl.getStatus() == CampaignCheckpointLead.DeliveryStatus.sent ||
                        cl.getStatus() == CampaignCheckpointLead.DeliveryStatus.delivered)
                .toList();
    }

    // Helper method to get failed leads
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<CampaignCheckpointLead> getFailedLeads() {
        return checkpointLeads.stream()
                .filter(cl -> cl.getStatus() == CampaignCheckpointLead.DeliveryStatus.failed)
                .toList();
    }

    public enum CheckpointStatus {
        pending, // Not yet started
        active, // Currently running/scheduled
        paused, // Temporarily stopped
        completed, // All emails sent successfully
        partially_completed // Some emails sent, some failed
    }

    public enum EmailProvider {
        GMAIL, // Gmail API
        RESEND // Resend API
    }
}
