package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "enrichment_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichmentCache {

    @Id
    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrichmentJob.Provider provider;

    @Column(name = "json", columnDefinition = "jsonb NOT NULL")
    @JdbcTypeCode(SqlTypes.JSON)
    private String json;

    @Column
    private Double confidence;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
