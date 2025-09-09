package com.outreachly.outreachly.entity;

import jakarta.persistence.*;
import jakarta.persistence.AttributeConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String filename;

    @Convert(converter = ImportStatusConverter.class)
    @Column(columnDefinition = "TEXT CHECK (status IN ('pending','processing','completed','failed')) DEFAULT 'pending'")
    private ImportStatus status;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "processed_rows")
    private Integer processedRows;

    @Column(name = "error_rows")
    private Integer errorRows;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ImportStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Converter
    public static class ImportStatusConverter implements AttributeConverter<ImportStatus, String> {
        @Override
        public String convertToDatabaseColumn(ImportStatus status) {
            if (status == null) {
                return null;
            }
            return status.name().toLowerCase();
        }

        @Override
        public ImportStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return ImportStatus.valueOf(dbData.toUpperCase());
        }
    }
}
