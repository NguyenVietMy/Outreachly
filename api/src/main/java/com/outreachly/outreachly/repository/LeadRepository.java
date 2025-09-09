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

    List<Lead> findByOrgId(UUID orgId);

    List<Lead> findByOrgIdAndListId(UUID orgId, UUID listId);

    Optional<Lead> findByEmailAndOrgId(String email, UUID orgId);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.orgId = :orgId")
    Long countByOrgId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.orgId = :orgId AND l.listId = :listId")
    Long countByOrgIdAndListId(@Param("orgId") UUID orgId, @Param("listId") UUID listId);
}
