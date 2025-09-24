package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EmailEventType eventType;

    @Column(name = "bounce_type")
    private String bounceType;

    @Column(name = "bounce_subtype")
    private String bounceSubtype;

    @Column(name = "complaint_feedback_type")
    private String complaintFeedbackType;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "raw_message", columnDefinition = "TEXT")
    private String rawMessage;

    @Builder.Default
    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum EmailEventType {
        BOUNCE, COMPLAINT, DELIVERY, OPEN, CLICK, REJECT
    }
}
