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

    @Query("SELECT DISTINCT c FROM Campaign c LEFT JOIN FETCH c.campaignLeads cl LEFT JOIN FETCH cl.lead WHERE c.orgId = :orgId ORDER BY c.createdAt DESC")
    List<Campaign> findByOrgIdOrderByCreatedAtDesc(@Param("orgId") UUID orgId);

    @Query("SELECT DISTINCT c FROM Campaign c LEFT JOIN FETCH c.campaignLeads cl LEFT JOIN FETCH cl.lead WHERE c.id = :id AND c.orgId = :orgId")
    Optional<Campaign> findByIdAndOrgId(@Param("id") UUID id, @Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Campaign c WHERE c.orgId = :orgId")
    Long countByOrgId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Campaign c WHERE c.orgId = :orgId AND c.status = :status")
    Long countByOrgIdAndStatus(@Param("orgId") UUID orgId, @Param("status") Campaign.CampaignStatus status);

    // Simple query without eager fetching for cases where we don't need lead
    // relationships
    @Query("SELECT c FROM Campaign c WHERE c.orgId = :orgId ORDER BY c.createdAt DESC")
    List<Campaign> findByOrgIdOrderByCreatedAtDescSimple(@Param("orgId") UUID orgId);
}
