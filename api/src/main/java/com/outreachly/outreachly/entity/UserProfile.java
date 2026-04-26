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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "profile_markdown", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String profileMarkdown = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "knowledge_areas", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> knowledgeAreas = List.of();

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private boolean onboardingCompleted = false;

    @Column(name = "leetcode_username", length = 50)
    private String leetcodeUsername;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leetcode_stats", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> leetcodeStats = Map.of();

    @Column(name = "leetcode_fetched_at")
    private OffsetDateTime leetcodeFetchedAt;

    @Column(name = "resume_text", columnDefinition = "TEXT")
    @Builder.Default
    private String resumeText = "";

    @Column(name = "resume_filename", length = 255)
    private String resumeFilename;

    @Column(name = "resume_uploaded_at")
    private OffsetDateTime resumeUploadedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "system_design_answers", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> systemDesignAnswers = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "core_cs_answers", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> coreCsAnswers = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "axis_scores", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> axisScores = Map.of(
            "dsa", 0, "projects", 0, "systemDesign", 0, "coreCs", 0, "resume", 0);

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resume_score_breakdown", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> resumeScoreBreakdown = Map.of();

    @Column(name = "target_role", length = 20)
    @Builder.Default
    private String targetRole = "swe_intern";

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
