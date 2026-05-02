package com.pulse.pulse.organizations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.organizations.domain.Organization;
import com.pulse.pulse.organizations.infrastructure.persistence.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.UUID;

class OrganizationServiceTest {

    private OrganizationRepository organizationRepository;
    private UserService userService;
    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationRepository = Mockito.mock(OrganizationRepository.class);
        userService = Mockito.mock(UserService.class);
        organizationService = new OrganizationService(organizationRepository, userService);
    }

    @Test
    void createOrganizationAssignsOwnershipThroughIdentityService() {
        CurrentUserView user = new CurrentUserView(
                7L,
                "test@example.com",
                "Test",
                "User",
                null,
                "USER",
                null,
                null,
                LocalDateTime.now()
        );

        UUID orgId = UUID.randomUUID();
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            organization.setId(orgId);
            return organization;
        });

        var result = organizationService.createOrganizationForUser(user, "Pulse");

        assertEquals(orgId, result.getId());
        assertEquals("Pulse", result.getName());
        verify(userService).assignOrganizationOwnership(7L, orgId);
    }
}
