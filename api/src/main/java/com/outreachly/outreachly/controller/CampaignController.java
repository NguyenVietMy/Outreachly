package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.Campaign;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.CampaignRepository;
import com.outreachly.outreachly.service.CsvImportService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Slf4j
public class CampaignController {

    private final CampaignRepository campaignRepository;
    private final UserService userService;
    private final CsvImportService csvImportService;

    @GetMapping
    public ResponseEntity<?> getAllCampaigns(Authentication authentication) {
        try {
            User user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(401).build();

            UUID orgId = resolveOrgId(user);
            List<Campaign> campaigns = campaignRepository.findByOrgIdOrderByCreatedAtDescSimple(orgId);

            return ResponseEntity.ok(campaigns);
        } catch (Exception e) {
            log.error("Error fetching campaigns: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch campaigns: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createCampaign(@RequestBody CreateCampaignRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = resolveOrgId(user);

        try {
            Campaign campaign = Campaign.builder()
                    .orgId(orgId)
                    .name(request.getName())
                    .description(request.getDescription())
                    .status(Campaign.CampaignStatus.active)
                    .build();

            Campaign savedCampaign = campaignRepository.save(campaign);
            return ResponseEntity.ok(savedCampaign);
        } catch (Exception e) {
            log.error("Error creating campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create campaign"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCampaign(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = resolveOrgId(user);
        return campaignRepository.findByIdAndOrgId(id, orgId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCampaign(@PathVariable UUID id, @RequestBody UpdateCampaignRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = resolveOrgId(user);

        return campaignRepository.findByIdAndOrgId(id, orgId)
                .map(campaign -> {
                    if (request.getName() != null)
                        campaign.setName(request.getName());
                    if (request.getDescription() != null)
                        campaign.setDescription(request.getDescription());
                    if (request.getStatus() != null)
                        campaign.setStatus(request.getStatus());

                    Campaign updatedCampaign = campaignRepository.save(campaign);
                    return ResponseEntity.ok(updatedCampaign);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCampaign(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = resolveOrgId(user);

        return campaignRepository.findByIdAndOrgId(id, orgId)
                .map(campaign -> {
                    campaignRepository.delete(campaign);
                    return ResponseEntity.ok(Map.of("message", "Campaign deleted successfully"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return null;
        return userService.findByEmail(authentication.getName());
    }

    private UUID resolveOrgId(User user) {
        return user.getOrgId() != null ? user.getOrgId() : csvImportService.getOrCreateDefaultOrganization();
    }

    // Request DTOs
    public static class CreateCampaignRequest {
        private String name;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class UpdateCampaignRequest {
        private String name;
        private String description;
        private Campaign.CampaignStatus status;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Campaign.CampaignStatus getStatus() {
            return status;
        }

        public void setStatus(Campaign.CampaignStatus status) {
            this.status = status;
        }
    }
}
