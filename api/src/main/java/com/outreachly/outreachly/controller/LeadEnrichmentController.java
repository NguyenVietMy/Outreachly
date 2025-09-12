package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.EnrichmentJob;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.EnrichmentJobRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.service.CsvImportService;
import com.outreachly.outreachly.service.EnrichmentService;
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

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return null;
        return userService.findByEmail(authentication.getName());
    }

    private UUID resolveOrgId(User user) {
        return user.getOrgId() != null ? user.getOrgId() : csvImportService.getOrCreateDefaultOrganization();
    }
}
