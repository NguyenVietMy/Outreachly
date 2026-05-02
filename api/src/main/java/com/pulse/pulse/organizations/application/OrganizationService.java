package com.pulse.pulse.organizations.application;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.organizations.api.dto.OrganizationDto;
import com.pulse.pulse.organizations.domain.Organization;
import com.pulse.pulse.organizations.infrastructure.persistence.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserService userService;

    @Transactional
    public OrganizationDto createOrganizationForUser(CurrentUserView user, String name) {
        if (user.orgId() != null) {
            throw new IllegalStateException("User already belongs to an organization");
        }

        Organization org = Organization.builder()
                .name(name)
                .plan("free")
                .description(null)
                .billingEmail(null)
                .build();
        Organization saved = organizationRepository.save(org);

        userService.assignOrganizationOwnership(user.id(), saved.getId());

        log.info("Created organization {} for user {} and assigned OWNER role", saved.getId(), user.email());
        return toDto(saved);
    }

    public OrganizationDto getOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        return toDto(org);
    }

    private OrganizationDto toDto(Organization org) {
        return OrganizationDto.builder()
                .id(org.getId())
                .name(org.getName())
                .plan(org.getPlan())
                .description(org.getDescription())
                .billingEmail(org.getBillingEmail())
                .createdAt(org.getCreatedAt())
                .build();
    }
}
