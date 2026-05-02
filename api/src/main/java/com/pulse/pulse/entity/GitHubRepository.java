package com.pulse.pulse.entity;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "github_repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "repo_full_name", nullable = false, length = 255)
    private String repoFullName;

    @Column(name = "repo_name", nullable = false, length = 100)
    private String repoName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> topics = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> languages = Map.of();

    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;

    @Column(name = "open_issues_count", nullable = false)
    @Builder.Default
    private int openIssuesCount = 0;

    @Column(name = "is_fork", nullable = false)
    @Builder.Default
    private boolean fork = false;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(name = "readme_content", columnDefinition = "TEXT")
    private String readmeContent;

    @Column(name = "readme_fetched_at")
    private LocalDateTime readmeFetchedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recent_commits", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> recentCommits = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "open_prs", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> openPrs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assigned_issues", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> assignedIssues = List.of();

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
