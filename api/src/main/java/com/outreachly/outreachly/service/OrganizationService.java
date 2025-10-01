package com.outreachly.outreachly.service;

import com.outreachly.outreachly.dto.OrganizationDto;
import com.outreachly.outreachly.entity.Organization;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.OrganizationRepository;
import com.outreachly.outreachly.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Transactional
    public OrganizationDto createOrganizationForUser(User user, String name) {
        if (user.getOrgId() != null) {
            throw new IllegalStateException("User already belongs to an organization");
        }

        Organization org = Organization.builder()
                .name(name)
                .plan("free")
                .description(null)
                .billingEmail(null)
                .build();
        Organization saved = organizationRepository.save(org);

        user.setOrgId(saved.getId());
        user.setRole(User.Role.OWNER);
        userRepository.save(user);

        log.info("Created organization {} for user {} and assigned OWNER role", saved.getId(), user.getEmail());
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