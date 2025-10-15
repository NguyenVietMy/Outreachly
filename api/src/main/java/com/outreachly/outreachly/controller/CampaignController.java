package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.Campaign;
import com.outreachly.outreachly.entity.CampaignCheckpoint;
import com.outreachly.outreachly.entity.CampaignCheckpointLead;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.Template;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.CampaignCheckpointLeadRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.TemplateRepository;
import com.outreachly.outreachly.service.CampaignService;
import com.outreachly.outreachly.service.CampaignCheckpointService;
import com.outreachly.outreachly.service.EmailDeliveryService;
import com.outreachly.outreachly.service.TemplateService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Slf4j
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignCheckpointService checkpointService;
    private final TemplateService templateService;
    private final UserService userService;
    private final CampaignCheckpointLeadRepository checkpointLeadRepository;
    private final LeadRepository leadRepository;
    private final TemplateRepository templateRepository;
    private final EmailDeliveryService emailDeliveryService;

    @GetMapping
    public ResponseEntity<?> getAllCampaigns(Authentication authentication) {
        try {
            User user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(401).build();

            UUID orgId = getOrgIdOrForbidden(user);
            List<Campaign> campaigns = campaignService.getAllCampaigns(orgId);

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

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            Campaign savedCampaign = campaignService.createCampaign(orgId, user.getId(), request.getName(),
                    request.getDescription());
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

        UUID orgId = getOrgIdOrForbidden(user);
        return campaignService.getCampaign(id, orgId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCampaign(@PathVariable UUID id, @RequestBody UpdateCampaignRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            Campaign updatedCampaign = campaignService.updateCampaign(id, orgId,
                    request.getName(), request.getDescription(), request.getStatus());
            return ResponseEntity.ok(updatedCampaign);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update campaign"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCampaign(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            campaignService.deleteCampaign(id, orgId);
            return ResponseEntity.ok(Map.of("message", "Campaign deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete campaign"));
        }
    }

    // Lead management endpoints
    @PostMapping("/{id}/leads")
    public ResponseEntity<?> addLeadsToCampaign(@PathVariable UUID id,
            @RequestBody AddLeadsRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            campaignService.addLeadsToCampaign(id, orgId, request.getLeadIds());
            return ResponseEntity.ok(Map.of("message", "Leads added successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error adding leads to campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to add leads to campaign"));
        }
    }

    @DeleteMapping("/{id}/leads")
    public ResponseEntity<?> removeLeadsFromCampaign(@PathVariable UUID id,
            @RequestBody RemoveLeadsRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            campaignService.removeLeadsFromCampaign(id, orgId, request.getLeadIds());
            return ResponseEntity.ok(Map.of("message", "Leads removed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error removing leads from campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to remove leads from campaign"));
        }
    }

    @GetMapping("/{id}/leads")
    public ResponseEntity<?> getCampaignLeads(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            return ResponseEntity.ok(campaignService.getCampaignLeads(id, orgId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching campaign leads: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch campaign leads"));
        }
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getCampaignStats(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            return ResponseEntity.ok(campaignService.getCampaignStats(id, orgId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching campaign stats: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch campaign stats"));
        }
    }

    // Campaign status management endpoints
    @PostMapping("/{id}/pause")
    public ResponseEntity<?> pauseCampaign(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            Campaign pausedCampaign = campaignService.pauseCampaign(id, orgId);
            return ResponseEntity.ok(pausedCampaign);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error pausing campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to pause campaign"));
        }
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resumeCampaign(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            Campaign resumedCampaign = campaignService.resumeCampaign(id, orgId);
            return ResponseEntity.ok(resumedCampaign);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error resuming campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to resume campaign"));
        }
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeCampaign(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            Campaign completedCampaign = campaignService.completeCampaign(id, orgId);
            return ResponseEntity.ok(completedCampaign);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error completing campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to complete campaign"));
        }
    }

    // ========== TEMPLATE ENDPOINTS ==========

    @GetMapping("/templates")
    public ResponseEntity<?> getEmailTemplates(Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            List<Template> templates = templateService.listTemplates(orgId, Template.Platform.EMAIL);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            log.error("Error fetching email templates: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch email templates"));
        }
    }

    // ========== CHECKPOINT ENDPOINTS ==========

    @GetMapping("/{campaignId}/checkpoints")
    public ResponseEntity<?> getCampaignCheckpoints(@PathVariable UUID campaignId, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            return ResponseEntity.ok(checkpointService.getCampaignCheckpoints(campaignId, orgId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching campaign checkpoints: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch campaign checkpoints"));
        }
    }

    @PostMapping("/{campaignId}/checkpoints")
    public ResponseEntity<?> createCheckpoint(@PathVariable UUID campaignId,
            @RequestBody CreateCheckpointRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            CampaignCheckpoint checkpoint = checkpointService.createCheckpoint(
                    campaignId, orgId, request.getName(),
                    request.getScheduledDate(), request.getTimeOfDay(),
                    request.getEmailTemplateId(), request.getLeadIds());
            return ResponseEntity.ok(checkpoint);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error creating checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create checkpoint"));
        }
    }

    @GetMapping("/{campaignId}/checkpoints/{checkpointId}")
    public ResponseEntity<?> getCheckpoint(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            return checkpointService.getCheckpoint(checkpointId, orgId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch checkpoint"));
        }
    }

    @PutMapping("/{campaignId}/checkpoints/{checkpointId}")
    public ResponseEntity<?> updateCheckpoint(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            @RequestBody UpdateCheckpointRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            CampaignCheckpoint updatedCheckpoint = checkpointService.updateCheckpoint(
                    checkpointId, orgId, request.getName(),
                    request.getScheduledDate(), request.getTimeOfDay(),
                    request.getEmailTemplateId(), request.getStatus());
            return ResponseEntity.ok(updatedCheckpoint);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update checkpoint"));
        }
    }

    @DeleteMapping("/{campaignId}/checkpoints/{checkpointId}")
    public ResponseEntity<?> deleteCheckpoint(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            checkpointService.deleteCheckpoint(checkpointId, orgId);
            return ResponseEntity.ok(Map.of("message", "Checkpoint deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete checkpoint"));
        }
    }

    // Checkpoint lead management
    @PostMapping("/{campaignId}/checkpoints/{checkpointId}/leads")
    public ResponseEntity<?> addLeadsToCheckpoint(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            @RequestBody AddLeadsToCheckpointRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            checkpointService.addLeadsToCheckpoint(checkpointId, orgId, request.getLeadIds());
            return ResponseEntity.ok(Map.of("message", "Leads added to checkpoint successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error adding leads to checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to add leads to checkpoint"));
        }
    }

    @DeleteMapping("/{campaignId}/checkpoints/{checkpointId}/leads")
    public ResponseEntity<?> removeLeadsFromCheckpoint(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            @RequestBody RemoveLeadsFromCheckpointRequest request, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            checkpointService.removeLeadsFromCheckpoint(checkpointId, orgId, request.getLeadIds());
            return ResponseEntity.ok(Map.of("message", "Leads removed from checkpoint successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error removing leads from checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to remove leads from checkpoint"));
        }
    }

    @GetMapping("/{campaignId}/checkpoints/{checkpointId}/leads")
    public ResponseEntity<?> getCheckpointLeads(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            return ResponseEntity.ok(checkpointService.getCheckpointLeads(checkpointId, orgId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching checkpoint leads: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch checkpoint leads"));
        }
    }

    // Checkpoint statistics
    @GetMapping("/{campaignId}/checkpoints/{checkpointId}/stats")
    public ResponseEntity<?> getCheckpointStats(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            return ResponseEntity.ok(checkpointService.getCheckpointStats(checkpointId, orgId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching checkpoint stats: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch checkpoint stats"));
        }
    }

    // Checkpoint status management
    @PostMapping("/{campaignId}/checkpoints/{checkpointId}/activate")
    public ResponseEntity<?> activateCheckpoint(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            CampaignCheckpoint checkpoint = checkpointService.activateCheckpoint(checkpointId, orgId);
            return ResponseEntity.ok(checkpoint);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error activating checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to activate checkpoint"));
        }
    }

    @PostMapping("/{campaignId}/checkpoints/{checkpointId}/pause")
    public ResponseEntity<?> pauseCheckpoint(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            CampaignCheckpoint checkpoint = checkpointService.pauseCheckpoint(checkpointId, orgId);
            return ResponseEntity.ok(checkpoint);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error pausing checkpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to pause checkpoint"));
        }
    }

    @PostMapping("/{campaignId}/checkpoints/{checkpointId}/retry")
    public ResponseEntity<?> retryFailedLeads(@PathVariable UUID campaignId, @PathVariable UUID checkpointId,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = getOrgIdOrForbidden(user);

        try {
            // Verify checkpoint belongs to campaign and organization
            CampaignCheckpoint checkpoint = checkpointService.getCheckpoint(checkpointId, orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

            if (!checkpoint.getCampaignId().equals(campaignId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Checkpoint does not belong to campaign"));
            }

            // Get failed leads for this checkpoint
            List<CampaignCheckpointLead> failedLeads = checkpoint.getFailedLeads();

            if (failedLeads.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "No failed leads to retry",
                        "retriedCount", 0,
                        "successCount", 0,
                        "failureCount", 0));
            }

            log.info("Retrying {} failed leads for checkpoint: {}", failedLeads.size(), checkpoint.getName());

            // Retry sending emails to failed leads
            int successCount = 0;
            int failureCount = 0;

            for (CampaignCheckpointLead checkpointLead : failedLeads) {
                try {
                    // Reset status to pending for retry
                    checkpointLead.setStatus(CampaignCheckpointLead.DeliveryStatus.pending);
                    checkpointLead.setErrorMessage(null);
                    checkpointLead.setUpdatedAt(LocalDateTime.now());
                    checkpointLeadRepository.save(checkpointLead);

                    // Get lead details
                    Lead lead = leadRepository.findById(checkpointLead.getLeadId()).orElse(null);
                    if (lead == null) {
                        log.warn("Lead not found for retry: {}", checkpointLead.getLeadId());
                        checkpointLead.setStatus(CampaignCheckpointLead.DeliveryStatus.failed);
                        checkpointLead.setErrorMessage("Lead not found");
                        checkpointLeadRepository.save(checkpointLead);
                        failureCount++;
                        continue;
                    }

                    // Get template
                    Template emailTemplate = null;
                    if (checkpoint.getEmailTemplateId() != null) {
                        emailTemplate = templateRepository.findById(checkpoint.getEmailTemplateId()).orElse(null);
                    }

                    // Send email using EmailDeliveryService
                    emailDeliveryService.sendEmailToLead(lead, emailTemplate, checkpoint);

                    // Mark as sent
                    checkpointLead.setStatus(CampaignCheckpointLead.DeliveryStatus.sent);
                    checkpointLead.setSentAt(LocalDateTime.now());
                    checkpointLead.setUpdatedAt(LocalDateTime.now());
                    checkpointLeadRepository.save(checkpointLead);

                    successCount++;
                    log.info("Successfully retried email to: {}", lead.getEmail());

                } catch (Exception e) {
                    log.error("Failed to retry email for lead: {}", checkpointLead.getLeadId(), e);
                    checkpointLead.setStatus(CampaignCheckpointLead.DeliveryStatus.failed);
                    checkpointLead.setErrorMessage(e.getMessage());
                    checkpointLead.setUpdatedAt(LocalDateTime.now());
                    checkpointLeadRepository.save(checkpointLead);
                    failureCount++;
                }
            }

            // Update checkpoint status based on results
            updateCheckpointStatusAfterRetry(checkpoint);

            return ResponseEntity.ok(Map.of(
                    "message", "Retry completed",
                    "retriedCount", failedLeads.size(),
                    "successCount", successCount,
                    "failureCount", failureCount,
                    "checkpointStatus", checkpoint.getStatus()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrying failed leads: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to retry leads: " + e.getMessage()));
        }
    }

    /**
     * Update checkpoint status after retry operation
     */
    private void updateCheckpointStatusAfterRetry(CampaignCheckpoint checkpoint) {
        // Get updated delivery statistics
        long sentLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpoint.getId(), CampaignCheckpointLead.DeliveryStatus.sent);
        long failedLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpoint.getId(), CampaignCheckpointLead.DeliveryStatus.failed);

        CampaignCheckpoint.CheckpointStatus newStatus;
        if (failedLeads == 0) {
            newStatus = CampaignCheckpoint.CheckpointStatus.completed;
        } else if (sentLeads > 0) {
            newStatus = CampaignCheckpoint.CheckpointStatus.partially_completed;
        } else {
            newStatus = CampaignCheckpoint.CheckpointStatus.paused; // All failed
        }

        checkpointService.updateCheckpoint(checkpoint.getId(), checkpoint.getOrgId(), null, null, null, null,
                newStatus);

        log.info("Updated checkpoint {} status to {} after retry ({} sent, {} failed)",
                checkpoint.getName(), newStatus, sentLeads, failedLeads);
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return null;
        return userService.findByEmail(authentication.getName());
    }

    private UUID getOrgIdOrForbidden(User user) {
        if (user.getOrgId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Organization required");
        }
        return user.getOrgId();
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

    public static class AddLeadsRequest {
        private List<UUID> leadIds;

        public List<UUID> getLeadIds() {
            return leadIds;
        }

        public void setLeadIds(List<UUID> leadIds) {
            this.leadIds = leadIds;
        }
    }

    public static class RemoveLeadsRequest {
        private List<UUID> leadIds;

        public List<UUID> getLeadIds() {
            return leadIds;
        }

        public void setLeadIds(List<UUID> leadIds) {
            this.leadIds = leadIds;
        }
    }

    // ========== CHECKPOINT REQUEST DTOS ==========

    public static class CreateCheckpointRequest {
        private String name;
        private java.time.LocalDate scheduledDate;
        private java.time.LocalTime timeOfDay;
        private UUID emailTemplateId;
        private List<UUID> leadIds;

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public java.time.LocalDate getScheduledDate() {
            return scheduledDate;
        }

        public void setScheduledDate(java.time.LocalDate scheduledDate) {
            this.scheduledDate = scheduledDate;
        }

        public java.time.LocalTime getTimeOfDay() {
            return timeOfDay;
        }

        public void setTimeOfDay(java.time.LocalTime timeOfDay) {
            this.timeOfDay = timeOfDay;
        }

        public UUID getEmailTemplateId() {
            return emailTemplateId;
        }

        public void setEmailTemplateId(UUID emailTemplateId) {
            this.emailTemplateId = emailTemplateId;
        }

        public List<UUID> getLeadIds() {
            return leadIds;
        }

        public void setLeadIds(List<UUID> leadIds) {
            this.leadIds = leadIds;
        }
    }

    public static class UpdateCheckpointRequest {
        private String name;
        private java.time.LocalDate scheduledDate;
        private java.time.LocalTime timeOfDay;
        private UUID emailTemplateId;
        private CampaignCheckpoint.CheckpointStatus status;

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public java.time.LocalDate getScheduledDate() {
            return scheduledDate;
        }

        public void setScheduledDate(java.time.LocalDate scheduledDate) {
            this.scheduledDate = scheduledDate;
        }

        public java.time.LocalTime getTimeOfDay() {
            return timeOfDay;
        }

        public void setTimeOfDay(java.time.LocalTime timeOfDay) {
            this.timeOfDay = timeOfDay;
        }

        public UUID getEmailTemplateId() {
            return emailTemplateId;
        }

        public void setEmailTemplateId(UUID emailTemplateId) {
            this.emailTemplateId = emailTemplateId;
        }

        public CampaignCheckpoint.CheckpointStatus getStatus() {
            return status;
        }

        public void setStatus(CampaignCheckpoint.CheckpointStatus status) {
            this.status = status;
        }
    }

    public static class AddLeadsToCheckpointRequest {
        private List<UUID> leadIds;

        public List<UUID> getLeadIds() {
            return leadIds;
        }

        public void setLeadIds(List<UUID> leadIds) {
            this.leadIds = leadIds;
        }
    }

    public static class RemoveLeadsFromCheckpointRequest {
        private List<UUID> leadIds;

        public List<UUID> getLeadIds() {
            return leadIds;
        }

        public void setLeadIds(List<UUID> leadIds) {
            this.leadIds = leadIds;
        }
    }
}
