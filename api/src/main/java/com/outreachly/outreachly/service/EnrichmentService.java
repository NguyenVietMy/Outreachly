package com.outreachly.outreachly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outreachly.outreachly.entity.EnrichmentCache;
import com.outreachly.outreachly.entity.EnrichmentJob;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.repository.EnrichmentCacheRepository;
import com.outreachly.outreachly.repository.EnrichmentJobRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final LeadRepository leadRepository;
    private final EnrichmentJobRepository jobRepository;
    private final EnrichmentCacheRepository cacheRepository;
    private final HunterClient hunterClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ENRICH_CONFIDENCE_MIN:0.6}")
    private double confidenceMin;

    @Value("${ENRICH_CONCURRENCY_PER_ORG:5}")
    private int concurrencyPerOrg;

    @Value("${ENRICH_RATE_PER_MIN:30}")
    private int ratePerMin;

    @Value("${ENRICH_CACHE_TTL_DAYS:0}")
    private int cacheTtlDays; // 0 = forever

    @Transactional
    public EnrichmentJob createJob(UUID orgId, UUID leadId) {
        // Check if there's already a pending job for this lead
        List<EnrichmentJob> existingJobs = jobRepository.findByOrgIdAndLeadIdOrderByCreatedAtDesc(orgId, leadId);
        if (!existingJobs.isEmpty() && existingJobs.get(0).getStatus() == EnrichmentJob.Status.pending) {
            log.info("Job already pending for lead {}, skipping creation", leadId);
            return existingJobs.get(0);
        }

        EnrichmentJob job = EnrichmentJob.builder()
                .orgId(orgId)
                .leadId(leadId)
                .provider(EnrichmentJob.Provider.HUNTER)
                .status(EnrichmentJob.Status.pending)
                .attempts(0)
                .build();
        return jobRepository.save(job);
    }

    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    public void processPendingJobs() {
        List<EnrichmentJob> pendingJobs = jobRepository.findByStatus(EnrichmentJob.Status.pending);

        // Only log when there are pending jobs
        if (!pendingJobs.isEmpty()) {
            log.info("Found {} pending enrichment jobs", pendingJobs.size());
        }

        // Rate limiting: only process one job at a time to respect Hunter's rate limits
        if (!pendingJobs.isEmpty()) {
            EnrichmentJob job = pendingJobs.get(0);
            try {
                processJob(job.getId());

                // Add delay between jobs to respect rate limits
                if (ratePerMin > 0) {
                    long delayMs = 60000L / ratePerMin; // Convert per-minute to per-job delay
                    Thread.sleep(Math.max(delayMs, 1000)); // Minimum 1 second between calls
                }
            } catch (Exception e) {
                log.error("Failed to process enrichment job {}: {}", job.getId(), e.getMessage(), e);
            }
        }
    }

    @Async
    @Transactional
    public void processJob(UUID jobId) {
        EnrichmentJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null)
            return;
        job.setStatus(EnrichmentJob.Status.running);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            Lead lead = leadRepository.findById(job.getLeadId()).orElseThrow();
            log.info("Processing enrichment job {} for lead {} (email: {})", jobId, lead.getId(), lead.getEmail());

            // Cache key: provider + email or name+domain
            String keyString = lead.getEmail() != null && !lead.getEmail().isBlank()
                    ? ("HUNTER:" + lead.getEmail().toLowerCase())
                    : ("HUNTER:" + safe(lead.getFirstName()) + ":" + safe(lead.getLastName()) + ":"
                            + safe(lead.getDomain()));
            String keyHash = sha256(keyString);

            Optional<EnrichmentCache> cached = cacheRepository.findByKeyHashAndProvider(keyHash,
                    EnrichmentJob.Provider.HUNTER);
            if (cached.isPresent()) {
                applyResult(lead, objectMapper.readTree(cached.get().getJson()));
                leadRepository.save(lead);
                finalizeJob(job, null);
                return;
            }

            JsonNode finder = null;
            if (lead.getEmail() == null || lead.getEmail().isBlank()) {
                if (lead.getFirstName() != null && lead.getLastName() != null && lead.getDomain() != null) {
                    try {
                        log.info("Calling Hunter email finder for {} {} at {}", lead.getFirstName(), lead.getLastName(),
                                lead.getDomain());
                        finder = hunterClient.emailFinder(lead.getDomain(), lead.getFirstName(), lead.getLastName());
                        log.info("Hunter email finder response: {}", finder);
                    } catch (Exception e) {
                        log.error("Hunter email finder failed: {}", e.getMessage(), e);
                        throw e;
                    }
                }
            }

            String email = lead.getEmail();
            Double confidence = null;
            if (finder != null && finder.has("data") && finder.get("data").has("email")) {
                email = finder.get("data").get("email").asText(null);
                confidence = finder.get("data").has("score") ? finder.get("data").get("score").asDouble() / 100.0
                        : null;
            }

            JsonNode verifier = null;
            if (email != null && !email.isBlank()) {
                try {
                    log.info("Calling Hunter email verifier for {}", email);
                    verifier = hunterClient.emailVerifier(email);
                    log.info("Hunter email verifier response: {}", verifier);
                } catch (Exception e) {
                    log.error("Hunter email verifier failed: {}", e.getMessage(), e);
                    throw e;
                }
            }

            // Build consolidated result json
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("finder", finder);
            resultMap.put("verifier", verifier);
            JsonNode result = objectMapper.valueToTree(resultMap);

            // Cache forever (TTL=0)
            cacheRepository.save(EnrichmentCache.builder()
                    .keyHash(keyHash)
                    .provider(EnrichmentJob.Provider.HUNTER)
                    .json(result.toString())
                    .confidence(confidence)
                    .fetchedAt(LocalDateTime.now())
                    .build());

            try {
                applyResult(lead, result);
                leadRepository.save(lead);
                log.info("Enrichment job {} completed successfully for lead {}", jobId, lead.getId());
                finalizeJob(job, null);
            } catch (Exception e) {
                log.error("Error in applyResult for job {}: {}", jobId, e.getMessage(), e);
                throw e;
            }

        } catch (Exception e) {
            log.error("Enrichment job failed {}: {}", jobId, e.getMessage(), e);
            job.setAttempts(job.getAttempts() + 1);
            job.setError(e.getMessage());
            job.setStatus(EnrichmentJob.Status.failed);
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    private void finalizeJob(EnrichmentJob job, String error) {
        job.setStatus(error == null ? EnrichmentJob.Status.completed : EnrichmentJob.Status.failed);
        job.setError(error);
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private void applyResult(Lead lead, JsonNode result) {
        try {
            log.info("Applying enrichment result to lead {}: {}", lead.getId(), result);

            String newEmail = null;
            Double score = null;

            // Handle finder results (for finding new emails)
            if (result.has("finder") && result.get("finder") != null) {
                JsonNode data = result.get("finder").get("data");
                if (data != null) {
                    if (data.has("email"))
                        newEmail = data.get("email").asText(null);
                    if (data.has("score"))
                        score = data.get("score").asDouble() / 100.0;
                }
            }

            // Handle verifier results (for verifying existing emails)
            if (result.has("verifier") && result.get("verifier") != null) {
                JsonNode verifierData = result.get("verifier").get("data");
                if (verifierData != null) {
                    // Update verification status
                    if (verifierData.has("status")) {
                        String status = verifierData.get("status").asText();
                        if ("deliverable".equals(status)) {
                            lead.setVerifiedStatus(Lead.VerifiedStatus.valid);
                        } else if ("undeliverable".equals(status)) {
                            lead.setVerifiedStatus(Lead.VerifiedStatus.invalid);
                        } else if ("risky".equals(status) || "unknown".equals(status) || "accept_all".equals(status)) {
                            lead.setVerifiedStatus(Lead.VerifiedStatus.risky);
                        }
                    }

                    // Also check the deprecated 'result' field as fallback
                    if (verifierData.has("result")) {
                        String resultStatus = verifierData.get("result").asText();
                        if ("deliverable".equals(resultStatus)) {
                            lead.setVerifiedStatus(Lead.VerifiedStatus.valid);
                        } else if ("undeliverable".equals(resultStatus)) {
                            lead.setVerifiedStatus(Lead.VerifiedStatus.invalid);
                        } else if ("risky".equals(resultStatus) || "unknown".equals(resultStatus)) {
                            lead.setVerifiedStatus(Lead.VerifiedStatus.risky);
                        }
                    }

                    // Update confidence score
                    if (verifierData.has("score")) {
                        Double verifierScore = verifierData.get("score").asDouble() / 100.0;
                        if (score == null || verifierScore > score) {
                            score = verifierScore;
                        }
                    }
                }
            }

            // Apply new email if found and meets confidence threshold
            if (newEmail != null && (score == null || score >= confidenceMin)) {
                if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
                    // keep previous
                    var prev = objectMapper.readTree(lead.getEnrichedJson() == null ? "{}" : lead.getEnrichedJson());
                    com.fasterxml.jackson.databind.node.ObjectNode prevObj = (com.fasterxml.jackson.databind.node.ObjectNode) prev;
                    com.fasterxml.jackson.databind.node.ObjectNode previousNode = (com.fasterxml.jackson.databind.node.ObjectNode) prevObj
                            .get("previous");
                    if (previousNode == null) {
                        previousNode = prevObj.putObject("previous");
                    }
                    previousNode.put("email", lead.getEmail());
                    lead.setEnrichedJson(prevObj.toString());
                }
                lead.setEmail(newEmail);
                log.info("Updated lead {} email to: {} (confidence: {})", lead.getId(), newEmail, score);
            }

            // store raw providers
            var root = objectMapper.readTree(lead.getEnrichedJson() == null ? "{}" : lead.getEnrichedJson());
            com.fasterxml.jackson.databind.node.ObjectNode rootObj = (com.fasterxml.jackson.databind.node.ObjectNode) root;
            com.fasterxml.jackson.databind.node.ObjectNode providersNode = (com.fasterxml.jackson.databind.node.ObjectNode) rootObj
                    .get("providers");
            if (providersNode == null) {
                providersNode = rootObj.putObject("providers");
            }
            providersNode.set("hunter", result);
            lead.setEnrichedJson(rootObj.toString());

            log.info("Lead {} enrichment applied successfully. Email: {}, Verified Status: {}",
                    lead.getId(), lead.getEmail(), lead.getVerifiedStatus());
        } catch (Exception e) {
            log.error("applyResult error for lead {}", lead.getId(), e);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
