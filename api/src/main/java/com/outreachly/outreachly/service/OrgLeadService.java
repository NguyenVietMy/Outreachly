package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.OrgLead;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.OrgLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrgLeadService {

    private final OrgLeadRepository orgLeadRepository;
    private final LeadRepository leadRepository;

    public Optional<OrgLead> findOrgLeadByEmail(UUID orgId, String email) {
        return orgLeadRepository.findByOrgIdAndEmailIgnoreCase(orgId, email);
    }

    public java.util.List<Lead> getLeadsForOrg(UUID orgId) {
        java.util.List<OrgLead> mappings = orgLeadRepository.findByOrgIdWithLeadAndCampaigns(orgId);
        return mappings.stream().map(OrgLead::getLead).toList();
    }

    public boolean hasMapping(UUID orgId, UUID leadId) {
        return orgLeadRepository.findByOrgIdAndLeadId(orgId, leadId).isPresent();
    }

    public OrgLead ensureOrgLeadForEmail(UUID orgId, String email, String sourceIfCreate) {
        return orgLeadRepository.findByOrgIdAndEmailIgnoreCase(orgId, email)
                .orElseGet(() -> {
                    // Ensure global lead exists
                    Lead lead = leadRepository.findByEmailIgnoreCase(email)
                            .orElseGet(() -> leadRepository.save(Lead.builder()
                                    .orgId(UUID.fromString("b8470f71-e5c8-4974-b6af-3d7af17aa55c"))
                                    .email(email.toLowerCase())
                                    .source(sourceIfCreate)
                                    .build()));

                    // Create org mapping (store email in lowercase for lookup)
                    OrgLead orgLead = OrgLead.builder()
                            .orgId(orgId)
                            .lead(lead)
                            .email(email.toLowerCase())
                            .build();
                    return orgLeadRepository.save(orgLead);
                });
    }
}
