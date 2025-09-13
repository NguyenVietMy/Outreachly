package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.EnrichmentJob;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.EnrichmentJobRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.entity.Lead;
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

    @PostMapping("/{id}/enrich")
    public ResponseEntity<?> enrichLead(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();
        UUID orgId = resolveOrgId(user);

        EnrichmentJob job = enrichmentService.createJob(orgId, id);
        enrichmentService.processJob(job.getId());
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
            enrichmentService.processJob(job.getId());
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

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return null;
        return userService.findByEmail(authentication.getName());
    }

    private UUID resolveOrgId(User user) {
        return user.getOrgId() != null ? user.getOrgId() : csvImportService.getOrCreateDefaultOrganization();
    }
}
