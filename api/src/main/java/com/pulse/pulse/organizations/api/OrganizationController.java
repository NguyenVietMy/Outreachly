package com.pulse.pulse.organizations.api;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.UserService;
import com.pulse.pulse.organizations.api.dto.CreateOrganizationRequest;
import com.pulse.pulse.organizations.api.dto.OrganizationDto;
import com.pulse.pulse.organizations.application.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Slf4j
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> createOrganization(@Valid @RequestBody CreateOrganizationRequest request,
            Authentication authentication) {
        try {
            CurrentUserView user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(UNAUTHORIZED).build();
            if (user.orgId() != null) {
                return ResponseEntity.status(CONFLICT).body(Map.of("error", "User already in an organization"));
            }

            OrganizationDto dto = organizationService.createOrganizationForUser(user, request.getName());
            return ResponseEntity.status(CREATED).body(dto);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create organization", e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to create organization"));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentOrganization(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(UNAUTHORIZED).build();
        if (user.orgId() == null)
            return ResponseEntity.status(FORBIDDEN).body(Map.of("error", "Organization required"));
        try {
            OrganizationDto dto = organizationService.getOrganization(user.orgId());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Failed to fetch current organization", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to fetch current organization");
        }
    }

    private CurrentUserView getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return null;
        return userService.getCurrentUser(authentication.getName());
    }
}
