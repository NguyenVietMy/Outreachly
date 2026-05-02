package com.pulse.pulse.repository;

import com.pulse.pulse.entity.GitHubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, UUID> {

    List<GitHubRepository> findByUserIdOrderByPushedAtDesc(Long userId);

    Optional<GitHubRepository> findByUserIdAndRepoFullName(Long userId, String repoFullName);

    @Query("SELECT r FROM GitHubRepository r WHERE r.userId = :userId AND r.pushedAt >= :since ORDER BY r.pushedAt DESC")
    List<GitHubRepository> findActiveByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM GitHubRepository r WHERE r.userId = :userId AND r.repoFullName NOT IN :activeRepos")
    void deleteStaleRepos(@Param("userId") Long userId, @Param("activeRepos") List<String> activeRepos);
}
