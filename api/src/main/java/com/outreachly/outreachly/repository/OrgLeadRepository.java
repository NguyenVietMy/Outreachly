package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.OrgLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrgLeadRepository extends JpaRepository<OrgLead, UUID> {

    @Query("SELECT ol FROM OrgLead ol WHERE ol.orgId = :orgId AND LOWER(ol.email) = LOWER(:email)")
    Optional<OrgLead> findByOrgIdAndEmailIgnoreCase(@Param("orgId") UUID orgId, @Param("email") String email);

    @Query("SELECT ol FROM OrgLead ol WHERE ol.orgId = :orgId AND ol.lead.id = :leadId")
    Optional<OrgLead> findByOrgIdAndLeadId(@Param("orgId") UUID orgId, @Param("leadId") UUID leadId);

    @Query("SELECT DISTINCT ol FROM OrgLead ol JOIN FETCH ol.lead l LEFT JOIN FETCH l.campaignLeads cl LEFT JOIN FETCH cl.campaign WHERE ol.orgId = :orgId")
    java.util.List<OrgLead> findByOrgIdWithLeadAndCampaigns(@Param("orgId") UUID orgId);
}
