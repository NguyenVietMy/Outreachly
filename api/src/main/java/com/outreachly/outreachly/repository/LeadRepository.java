package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    @Query("SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.campaignLeads cl LEFT JOIN FETCH cl.campaign WHERE l.orgId = :orgId")
    List<Lead> findByOrgId(@Param("orgId") UUID orgId);

    @Query("SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.campaignLeads cl LEFT JOIN FETCH cl.campaign WHERE l.orgId = :orgId AND l.listId = :listId")
    List<Lead> findByOrgIdAndListId(@Param("orgId") UUID orgId, @Param("listId") UUID listId);

    @Query("SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.campaignLeads cl LEFT JOIN FETCH cl.campaign WHERE l.email = :email AND l.orgId = :orgId")
    Optional<Lead> findByEmailAndOrgId(@Param("email") String email, @Param("orgId") UUID orgId);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.orgId = :orgId")
    Long countByOrgId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.orgId = :orgId AND l.listId = :listId")
    Long countByOrgIdAndListId(@Param("orgId") UUID orgId, @Param("listId") UUID listId);

    @Query("SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.campaignLeads cl LEFT JOIN FETCH cl.campaign WHERE l.id = :id")
    Optional<Lead> findByIdWithCampaigns(@Param("id") UUID id);

    @Query("SELECT l FROM Lead l WHERE LOWER(l.email) = LOWER(:email)")
    Optional<Lead> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT l FROM Lead l WHERE l.id IN :ids AND l.orgId = :orgId")
    List<Lead> findByIdInAndOrgId(@Param("ids") List<UUID> ids, @Param("orgId") UUID orgId);
}
