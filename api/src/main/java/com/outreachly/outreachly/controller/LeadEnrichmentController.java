package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.LeadWithCampaignsDto;
import com.outreachly.outreachly.entity.EnrichmentJob;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.EnrichmentJobRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.service.CampaignLeadService;
import com.outreachly.outreachly.service.CsvImportService;
import com.outreachly.outreachly.service.EnrichmentService;
import com.outreachly.outreachly.service.EnrichmentPreviewService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@Slf4j
public class LeadEnrichmentController {

    private final EnrichmentService enrichmentService;
    private final EnrichmentPreviewService enrichmentPreviewService;
    private final EnrichmentJobRepository jobRepository;
    private final UserService userService;
    private final CsvImportService csvImportService;
    private final LeadRepository leadRepository;
    private final CampaignLeadService campaignLeadService;

    @PostMapping("/{id}/enrich")
    public ResponseEntity<?> enrichLead(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();
        UUID orgId = resolveOrgId(user);

        EnrichmentJob job = enrichmentService.createJob(orgId, id);
        // Let the scheduled processor handle the job to avoid race conditions
        return ResponseEntity.ok(Map.of("jobId", job.getId(), "status", job.getStatus()));
    }

    @PostMapping("/enrich")
    public ResponseEntity<?> enrichList(@RequestParam("list") UUID listId, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();
        UUID orgId = resolveOrgId(user);

        // Create one job per lead in the list
        var leads = leadRepository.findByOrgIdAndListId(orgId, listId);
        if (leads == null || leads.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "empty", "count", 0));
        }
        var jobIds = new java.util.ArrayList<java.util.UUID>();
        for (Lead l : leads) {
            EnrichmentJob job = enrichmentService.createJob(orgId, l.getId());
            jobIds.add(job.getId());
            // Don't call processJob immediately - let the scheduler handle it
            // This prevents duplicate API calls for the same lead
        }
        return ResponseEntity.ok(Map.of("status", "queued", "count", leads.size(), "jobIds", jobIds));
    }

    @GetMapping("/{id}/enrich-jobs")
    public ResponseEntity<?> listJobs(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();
        UUID orgId = resolveOrgId(user);
        List<EnrichmentJob> jobs = jobRepository.findByOrgIdAndLeadIdOrderByCreatedAtDesc(orgId, id);
        return ResponseEntity.ok(jobs);
    }

    @PostMapping("/{id}/enrich/preview")
    public ResponseEntity<?> previewEnrichment(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        try {
            // TODO: Add per-user rate limiting (commented out for MVP)
            // if (isUserRateLimited(user.getId())) {
            // return ResponseEntity.status(429).body(Map.of("error", "Rate limit
            // exceeded"));
            // }

            var preview = enrichmentPreviewService.getEnrichmentPreview(id);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.error("Enrichment preview failed for lead {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/enrich/apply")
    public ResponseEntity<?> applyEnrichment(@PathVariable UUID id, @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        try {
            // TODO: Add per-user rate limiting (commented out for MVP)
            // if (isUserRateLimited(user.getId())) {
            // return ResponseEntity.status(429).body(Map.of("error", "Rate limit
            // exceeded"));
            // }

            var acceptedChanges = request.get("acceptedChanges");
            if (acceptedChanges == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "acceptedChanges is required"));
            }

            var result = enrichmentPreviewService.applyEnrichment(id,
                    new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(acceptedChanges));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Enrichment application failed for lead {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/enrich/revert")
    public ResponseEntity<?> revertEnrichment(@PathVariable UUID id, @RequestParam int historyIndex,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        try {
            var result = enrichmentPreviewService.revertEnrichment(id, historyIndex);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Enrichment revert failed for lead {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/enrichment-history")
    public ResponseEntity<?> getEnrichmentHistory(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        try {
            Lead lead = leadRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Lead not found"));

            var history = lead.getEnrichmentHistory();
            if (history == null || history.isBlank()) {
                return ResponseEntity.ok(Map.of("history", "[]"));
            }

            return ResponseEntity
                    .ok(Map.of("history", new com.fasterxml.jackson.databind.ObjectMapper().readTree(history)));
        } catch (Exception e) {
            log.error("Failed to get enrichment history for lead {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllLeads(
            @RequestParam(required = false) UUID campaignId,
            Authentication authentication) {
        try {
            User user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(401).build();

            UUID orgId = resolveOrgId(user);
            List<Lead> leads;

            if (campaignId != null) {
                // Get leads through campaign-lead relationship
                leads = campaignLeadService.getActiveLeadsForCampaign(campaignId)
                        .stream()
                        .filter(lead -> lead.getOrgId().equals(orgId))
                        .toList();
            } else {
                // Get all leads for the organization
                leads = leadRepository.findByOrgId(orgId);
            }

            // Convert to DTO with campaign information
            List<LeadWithCampaignsDto> leadDtos = leads.stream()
                    .map(LeadWithCampaignsDto::fromLead)
                    .toList();

            return ResponseEntity.ok(leadDtos);
        } catch (Exception e) {
            log.error("Error fetching leads: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch leads: " + e.getMessage()));
        }
    }

    @PutMapping("/bulk-campaign")
    public ResponseEntity<?> assignLeadsToCampaign(
            @RequestBody BulkCampaignAssignmentRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = resolveOrgId(user);

        try {
            List<Lead> leads = leadRepository.findAllById(request.getLeadIds());

            // Filter leads that belong to the user's organization
            List<Lead> userLeads = leads.stream()
                    .filter(lead -> lead.getOrgId().equals(orgId))
                    .toList();

            // Create campaign-lead relationships using service
            List<UUID> leadIds = userLeads.stream().map(Lead::getId).toList();
            int assignedCount = campaignLeadService.addLeadsToCampaign(request.getCampaignId(), leadIds, user.getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Leads assigned to campaign successfully",
                    "assignedCount", assignedCount));
        } catch (Exception e) {
            log.error("Error assigning leads to campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to assign leads to campaign"));
        }
    }

    @PutMapping("/{id}/campaign")
    public ResponseEntity<?> assignLeadToCampaign(
            @PathVariable UUID id,
            @RequestBody CampaignAssignmentRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        UUID orgId = resolveOrgId(user);

        try {
            Lead lead = leadRepository.findById(id)
                    .filter(l -> l.getOrgId().equals(orgId))
                    .orElseThrow(() -> new IllegalArgumentException("Lead not found"));

            // Create campaign-lead relationship using service
            campaignLeadService.addLeadToCampaign(request.getCampaignId(), lead.getId(), user.getId());

            return ResponseEntity.ok(Map.of("message", "Lead assigned to campaign successfully"));
        } catch (Exception e) {
            log.error("Error assigning lead to campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to assign lead to campaign"));
        }
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
    public static class BulkCampaignAssignmentRequest {
        private List<UUID> leadIds;
        private UUID campaignId;

        public List<UUID> getLeadIds() {
            return leadIds;
        }

        public void setLeadIds(List<UUID> leadIds) {
            this.leadIds = leadIds;
        }

        public UUID getCampaignId() {
            return campaignId;
        }

        public void setCampaignId(UUID campaignId) {
            this.campaignId = campaignId;
        }
    }

    public static class CampaignAssignmentRequest {
        private UUID campaignId;

        public UUID getCampaignId() {
            return campaignId;
        }

        public void setCampaignId(UUID campaignId) {
            this.campaignId = campaignId;
        }
    }

    public static class ExportRequest {
        private List<UUID> leadIds;

        public List<UUID> getLeadIds() {
            return leadIds;
        }

        public void setLeadIds(List<UUID> leadIds) {
            this.leadIds = leadIds;
        }
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyLead(@PathVariable UUID id, Authentication authentication) {
        try {
            User user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(401).build();

            UUID orgId = resolveOrgId(user);
            leadRepository.findById(id)
                    .filter(l -> l.getOrgId().equals(orgId))
                    .orElseThrow(() -> new IllegalArgumentException("Lead not found"));

            // For now, just return success - verification logic can be implemented later
            return ResponseEntity.ok(Map.of("message", "Lead verification initiated", "leadId", id));
        } catch (Exception e) {
            log.error("Error verifying lead: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to verify lead: " + e.getMessage()));
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportLeads(@RequestBody ExportRequest request, Authentication authentication) {
        try {
            User user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(401).build();

            UUID orgId = resolveOrgId(user);
            List<Lead> leads;

            if (request.getLeadIds() != null && !request.getLeadIds().isEmpty()) {
                // Export specific leads
                leads = leadRepository.findAllById(request.getLeadIds())
                        .stream()
                        .filter(lead -> lead.getOrgId().equals(orgId))
                        .toList();
            } else {
                // Export all leads for organization
                leads = leadRepository.findByOrgId(orgId);
            }

            // Convert to CSV
            StringBuilder csv = new StringBuilder();
            csv.append(
                    "ID,First Name,Last Name,Company,Title,Email,Phone,Domain,LinkedIn URL,Country,State,City,Source,Verified Status,Created At\n");

            for (Lead lead : leads) {
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        lead.getId(),
                        escapeCsv(lead.getFirstName()),
                        escapeCsv(lead.getLastName()),
                        escapeCsv(lead.getCompany()),
                        escapeCsv(lead.getTitle()),
                        escapeCsv(lead.getEmail()),
                        escapeCsv(lead.getPhone()),
                        escapeCsv(lead.getDomain()),
                        escapeCsv(lead.getLinkedinUrl()),
                        escapeCsv(lead.getCountry()),
                        escapeCsv(lead.getState()),
                        escapeCsv(lead.getCity()),
                        escapeCsv(lead.getSource()),
                        lead.getVerifiedStatus(),
                        lead.getCreatedAt()));
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=leads-export.csv")
                    .body(csv.toString());
        } catch (Exception e) {
            log.error("Error exporting leads: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to export leads: " + e.getMessage()));
        }
    }

    private String escapeCsv(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
