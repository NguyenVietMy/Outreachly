package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<Campaign> findByIdAndOrgId(UUID id, UUID orgId);

    @Query("SELECT COUNT(c) FROM Campaign c WHERE c.orgId = :orgId")
    Long countByOrgId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Campaign c WHERE c.orgId = :orgId AND c.status = :status")
    Long countByOrgIdAndStatus(@Param("orgId") UUID orgId, @Param("status") Campaign.CampaignStatus status);
}
