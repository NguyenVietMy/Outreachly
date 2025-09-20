package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.dto.LeadWithCampaignsDto;
import com.outreachly.outreachly.entity.CampaignLead;
import com.outreachly.outreachly.entity.EnrichmentJob;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.EnrichmentJobRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.CampaignRepository;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.Campaign;
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
    private final CampaignRepository campaignRepository;

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
                log.info("Fetching leads for campaign: {} for organization: {}", campaignId, orgId);
                // Get leads through campaign-lead relationship
                List<Lead> campaignLeads = campaignLeadService.getActiveLeadsForCampaign(campaignId);
                log.debug("Retrieved {} leads from campaign service for campaign {}", campaignLeads.size(), campaignId);

                leads = campaignLeads.stream()
                        .filter(lead -> lead.getOrgId().equals(orgId))
                        .toList();

                log.info("Filtered to {} leads belonging to organization {} for campaign {}",
                        leads.size(), orgId, campaignId);
            } else {
                log.info("Fetching all leads for organization: {}", orgId);
                // Get all leads for the organization
                leads = leadRepository.findByOrgId(orgId);
                log.debug("Retrieved {} total leads for organization {}", leads.size(), orgId);
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
        if (user == null) {
            log.warn("Unauthorized attempt to assign leads to campaign - no user found");
            return ResponseEntity.status(401).build();
        }

        UUID orgId = resolveOrgId(user);
        log.info("Starting bulk campaign assignment - User: {}, Org: {}, Campaign: {}, Lead IDs: {}",
                user.getId(), orgId, request.getCampaignId(), request.getLeadIds());

        try {
            List<Lead> leads = leadRepository.findAllById(request.getLeadIds());
            log.debug("Found {} leads from database for IDs: {}", leads.size(), request.getLeadIds());

            // Filter leads that belong to the user's organization
            List<Lead> userLeads = leads.stream()
                    .filter(lead -> lead.getOrgId().equals(orgId))
                    .toList();

            log.info("Filtered to {} leads belonging to organization {} out of {} total leads",
                    userLeads.size(), orgId, leads.size());

            // Create campaign-lead relationships using service
            List<UUID> leadIds = userLeads.stream().map(Lead::getId).toList();
            int assignedCount = campaignLeadService.addLeadsToCampaign(request.getCampaignId(), leadIds, user.getId());

            log.info("Successfully assigned {} leads to campaign {} for user {}",
                    assignedCount, request.getCampaignId(), user.getId());

            // Verify the assignments by checking the database
            long actualCount = campaignLeadService.getActiveLeadCountForCampaign(request.getCampaignId());
            log.info("Verification: Campaign {} now has {} active leads in database",
                    request.getCampaignId(), actualCount);

            return ResponseEntity.ok(Map.of(
                    "message", "Leads assigned to campaign successfully",
                    "assignedCount", assignedCount));
        } catch (Exception e) {
            log.error("Error assigning leads to campaign - User: {}, Campaign: {}, Lead IDs: {}, Error: {}",
                    user.getId(), request.getCampaignId(), request.getLeadIds(), e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to assign leads to campaign"));
        }
    }

    @PutMapping("/{id}/campaign")
    public ResponseEntity<?> assignLeadToCampaign(
            @PathVariable UUID id,
            @RequestBody CampaignAssignmentRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) {
            log.warn("Unauthorized attempt to assign lead to campaign - no user found for lead ID: {}", id);
            return ResponseEntity.status(401).build();
        }

        UUID orgId = resolveOrgId(user);
        log.info("Starting single lead campaign assignment - User: {}, Org: {}, Lead ID: {}, Campaign: {}",
                user.getId(), orgId, id, request.getCampaignId());

        try {
            Lead lead = leadRepository.findById(id)
                    .filter(l -> l.getOrgId().equals(orgId))
                    .orElseThrow(() -> {
                        log.warn("Lead not found or not accessible - Lead ID: {}, User Org: {}", id, orgId);
                        return new IllegalArgumentException("Lead not found");
                    });

            log.debug("Found lead: {} (Email: {}) for campaign assignment", lead.getId(), lead.getEmail());

            // Create campaign-lead relationship using service
            campaignLeadService.addLeadToCampaign(request.getCampaignId(), lead.getId(), user.getId());

            log.info("Successfully assigned lead {} to campaign {} for user {}",
                    lead.getId(), request.getCampaignId(), user.getId());

            return ResponseEntity.ok(Map.of("message", "Lead assigned to campaign successfully"));
        } catch (Exception e) {
            log.error("Error assigning lead to campaign - User: {}, Lead ID: {}, Campaign: {}, Error: {}",
                    user.getId(), id, request.getCampaignId(), e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to assign lead to campaign"));
        }
    }

    @DeleteMapping("/bulk-campaign-remove")
    public ResponseEntity<?> removeLeadsFromCampaign(
            @RequestBody BulkCampaignRemovalRequest request,
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

            // Remove campaign-lead relationships using service
            List<UUID> leadIds = userLeads.stream().map(Lead::getId).toList();
            int removedCount = campaignLeadService.removeLeadsFromCampaign(request.getCampaignId(), leadIds);

            return ResponseEntity.ok(Map.of(
                    "message", "Leads removed from campaign successfully",
                    "removedCount", removedCount));
        } catch (Exception e) {
            log.error("Error removing leads from campaign: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to remove leads from campaign"));
        }
    }

    @PostMapping("/bulk-create")
    public ResponseEntity<?> bulkCreateLeads(
            @RequestBody BulkCreateLeadsRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null) {
            log.warn("Unauthorized attempt to create leads - no user found");
            return ResponseEntity.status(401).build();
        }

        UUID orgId = resolveOrgId(user);
        log.info("Starting bulk lead creation - User: {}, Org: {}, Lead count: {}",
                user.getId(), orgId, request.getLeads().size());

        try {
            List<Lead> leadsToCreate = new ArrayList<>();
            List<String> createdLeadIds = new ArrayList<>();

            log.info("Processing {} leads for bulk creation", request.getLeads().size());
            log.info("Campaign ID: {}", request.getCampaignId());

            for (LeadData leadData : request.getLeads()) {
                log.debug("Creating lead: firstName={}, lastName={}, email={}, domain={}",
                        leadData.getFirstName(), leadData.getLastName(), leadData.getEmail(), leadData.getDomain());

                Lead lead = Lead.builder()
                        .orgId(orgId)
                        .firstName(leadData.getFirstName())
                        .lastName(leadData.getLastName())
                        .email(leadData.getEmail())
                        .domain(leadData.getDomain())
                        .position(leadData.getPosition())
                        .positionRaw(leadData.getPositionRaw())
                        .seniority(leadData.getSeniority())
                        .department(leadData.getDepartment())
                        .linkedinUrl(leadData.getLinkedinUrl())
                        .confidenceScore(leadData.getConfidenceScore())
                        .emailType(leadData.getEmailType())
                        .verifiedStatus(leadData.getVerifiedStatus())
                        .source("hunter_api")
                        .build();

                leadsToCreate.add(lead);
            }

            // Save all leads
            List<Lead> savedLeads = leadRepository.saveAll(leadsToCreate);

            // Extract IDs for response
            for (Lead savedLead : savedLeads) {
                createdLeadIds.add(savedLead.getId().toString());
            }

            log.info("Successfully created {} leads for user {} in organization {}",
                    savedLeads.size(), user.getId(), orgId);

            // If campaign ID is provided, add leads to campaign
            if (request.getCampaignId() != null) {
                List<UUID> leadIds = savedLeads.stream().map(Lead::getId).toList();
                log.info("Adding {} leads to campaign {} for user {}",
                        leadIds.size(), request.getCampaignId(), user.getId());

                try {
                    // Validate campaign exists and belongs to user's organization
                    Campaign campaign = campaignRepository.findByIdAndOrgId(request.getCampaignId(), orgId)
                            .orElseThrow(() -> new IllegalArgumentException("Campaign not found or access denied"));

                    log.info("Campaign validation successful: {} (ID: {})", campaign.getName(), campaign.getId());

                    int assignedCount = campaignLeadService.addLeadsToCampaign(request.getCampaignId(), leadIds,
                            user.getId());
                    log.info("Successfully assigned {} leads to campaign {} for user {}",
                            assignedCount, request.getCampaignId(), user.getId());
                } catch (Exception e) {
                    log.error("Error adding leads to campaign {}: {}", request.getCampaignId(), e.getMessage(), e);
                    // Don't fail the entire operation if campaign assignment fails
                }
            } else {
                log.info("No campaign ID provided, leads created without campaign assignment");
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Leads created successfully",
                    "leadIds", createdLeadIds,
                    "count", savedLeads.size()));
        } catch (Exception e) {
            log.error("Error creating leads: {}", e.getMessage(), e);
            e.printStackTrace(); // Add stack trace for debugging
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create leads: " + e.getMessage()));
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

    public static class BulkCampaignRemovalRequest {
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

    @GetMapping("/debug/campaign/{campaignId}")
    public ResponseEntity<?> debugCampaignLeads(
            @PathVariable UUID campaignId,
            Authentication authentication) {
        try {
            User user = getUser(authentication);
            if (user == null)
                return ResponseEntity.status(401).build();

            UUID orgId = resolveOrgId(user);

            // Get campaign-lead relationships directly
            List<CampaignLead> campaignLeads = campaignLeadService.getActiveCampaignsForLead(campaignId);
            long count = campaignLeadService.getActiveLeadCountForCampaign(campaignId);

            // Get leads through the service
            List<Lead> leads = campaignLeadService.getActiveLeadsForCampaign(campaignId);

            Map<String, Object> debug = Map.of(
                    "campaignId", campaignId,
                    "orgId", orgId,
                    "campaignLeadRelationships", campaignLeads.size(),
                    "activeLeadCount", count,
                    "leadsRetrieved", leads.size(),
                    "leadIds", leads.stream().map(Lead::getId).toList());

            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            log.error("Error debugging campaign leads: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
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
                    "ID,First Name,Last Name,Position,Department,Email,Phone,Domain,LinkedIn URL,Twitter,Confidence Score,Email Type,Source,Verified Status,Created At\n");

            for (Lead lead : leads) {
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        lead.getId(),
                        escapeCsv(lead.getFirstName()),
                        escapeCsv(lead.getLastName()),
                        escapeCsv(lead.getPosition()),
                        escapeCsv(lead.getDepartment()),
                        escapeCsv(lead.getEmail()),
                        escapeCsv(lead.getPhone()),
                        escapeCsv(lead.getDomain()),
                        escapeCsv(lead.getLinkedinUrl()),
                        escapeCsv(lead.getTwitter()),
                        lead.getConfidenceScore(),
                        lead.getEmailType() != null ? lead.getEmailType().toString() : "",
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

    // Request DTOs
    public static class BulkCreateLeadsRequest {
        private List<LeadData> leads;
        private UUID campaignId;

        public List<LeadData> getLeads() {
            return leads;
        }

        public void setLeads(List<LeadData> leads) {
            this.leads = leads;
        }

        public UUID getCampaignId() {
            return campaignId;
        }

        public void setCampaignId(UUID campaignId) {
            this.campaignId = campaignId;
        }
    }

    public static class LeadData {
        private String firstName;
        private String lastName;
        private String email;
        private String domain;
        private String position;
        private String positionRaw;
        private String seniority;
        private String department;
        private String linkedinUrl;
        private Integer confidenceScore;
        private Lead.EmailType emailType;
        private Lead.VerifiedStatus verifiedStatus;

        // Getters and setters
        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public String getPositionRaw() {
            return positionRaw;
        }

        public void setPositionRaw(String positionRaw) {
            this.positionRaw = positionRaw;
        }

        public String getSeniority() {
            return seniority;
        }

        public void setSeniority(String seniority) {
            this.seniority = seniority;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getLinkedinUrl() {
            return linkedinUrl;
        }

        public void setLinkedinUrl(String linkedinUrl) {
            this.linkedinUrl = linkedinUrl;
        }

        public Integer getConfidenceScore() {
            return confidenceScore;
        }

        public void setConfidenceScore(Integer confidenceScore) {
            this.confidenceScore = confidenceScore;
        }

        public Lead.EmailType getEmailType() {
            return emailType;
        }

        public void setEmailType(Lead.EmailType emailType) {
            this.emailType = emailType;
        }

        public Lead.VerifiedStatus getVerifiedStatus() {
            return verifiedStatus;
        }

        public void setVerifiedStatus(Lead.VerifiedStatus verifiedStatus) {
            this.verifiedStatus = verifiedStatus;
        }
    }
}
