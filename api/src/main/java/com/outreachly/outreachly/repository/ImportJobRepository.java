package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    List<ImportJob> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<ImportJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT ij FROM ImportJob ij WHERE ij.orgId = :orgId AND ij.status IN ('pending', 'processing') ORDER BY ij.createdAt ASC")
    List<ImportJob> findPendingAndProcessingJobsByOrgId(@Param("orgId") UUID orgId);
}
