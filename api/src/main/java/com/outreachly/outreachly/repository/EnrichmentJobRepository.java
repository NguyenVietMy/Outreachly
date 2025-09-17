package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.EnrichmentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnrichmentJobRepository extends JpaRepository<EnrichmentJob, UUID> {
    List<EnrichmentJob> findByOrgIdAndLeadIdOrderByCreatedAtDesc(UUID orgId, UUID leadId);

    long countByOrgIdAndStatus(UUID orgId, EnrichmentJob.Status status);

    List<EnrichmentJob> findByStatus(EnrichmentJob.Status status);
}
