package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.CreateOrganizationRequest;
import com.outreachly.outreachly.dto.OrganizationDto;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.OrganizationService;
import com.outreachly.outreachly.service.UserService;
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
            User user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(UNAUTHORIZED).build();
            if (user.getOrgId() != null) {
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
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(UNAUTHORIZED).build();
        if (user.getOrgId() == null)
            return ResponseEntity.status(FORBIDDEN).body(Map.of("error", "Organization required"));
        try {
            OrganizationDto dto = organizationService.getOrganization(user.getOrgId());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Failed to fetch current organization", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to fetch current organization");
        }
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return null;
        return userService.findByEmail(authentication.getName());
    }
}