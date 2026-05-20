package com.pulse.pulse.personal.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "knowledge_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_key", nullable = false, length = 255)
    private String sourceKey;

    @Column(name = "chunk_index", nullable = false)
    @Builder.Default
    private int chunkIndex = 0;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "vector(1536)", insertable = false, updatable = false)
    private String embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Column(name = "embedded_at")
    private OffsetDateTime embeddedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
