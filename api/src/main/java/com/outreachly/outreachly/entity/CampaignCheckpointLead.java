package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_checkpoint_leads", uniqueConstraints = @UniqueConstraint(columnNames = { "checkpoint_id",
        "lead_id" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignCheckpointLead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "checkpoint_id", nullable = false)
    private UUID checkpointId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.pending;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Many-to-one relationship with CampaignCheckpoint
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private CampaignCheckpoint checkpoint;

    // Many-to-one relationship with Lead
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Lead lead;

    // Helper method to mark as sent
    public void markAsSent() {
        this.status = DeliveryStatus.sent;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    // Helper method to mark as delivered
    public void markAsDelivered() {
        this.status = DeliveryStatus.delivered;
        if (this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
        this.errorMessage = null;
    }

    // Helper method to mark as failed
    public void markAsFailed(String errorMessage) {
        this.status = DeliveryStatus.failed;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public enum DeliveryStatus {
        pending, // Not yet sent
        sent, // API call succeeded
        delivered, // Confirmed delivered (same as sent for now)
        failed // API call failed
    }
}
