package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {

    /**
     * Find organization settings by org ID
     * RLS will automatically filter by user's organization
     */
    @Query("SELECT os FROM OrganizationSettings os WHERE os.orgId = :orgId")
    Optional<OrganizationSettings> findByOrgId(@Param("orgId") UUID orgId);

    /**
     * Check if organization settings exist for the given org ID
     */
    @Query("SELECT COUNT(os) > 0 FROM OrganizationSettings os WHERE os.orgId = :orgId")
    boolean existsByOrgId(@Param("orgId") UUID orgId);
}

