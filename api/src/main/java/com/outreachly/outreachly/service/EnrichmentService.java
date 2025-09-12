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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        EnrichmentJob job = EnrichmentJob.builder()
                .orgId(orgId)
                .leadId(leadId)
                .provider(EnrichmentJob.Provider.HUNTER)
                .status(EnrichmentJob.Status.pending)
                .attempts(0)
                .build();
        return jobRepository.save(job);
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
                    finder = hunterClient.emailFinder(lead.getDomain(), lead.getFirstName(), lead.getLastName());
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
                verifier = hunterClient.emailVerifier(email);
            }

            // Build consolidated result json
            JsonNode result = objectMapper.valueToTree(Map.of(
                    "finder", finder,
                    "verifier", verifier));

            // Cache forever (TTL=0)
            cacheRepository.save(EnrichmentCache.builder()
                    .keyHash(keyHash)
                    .provider(EnrichmentJob.Provider.HUNTER)
                    .json(result.toString())
                    .confidence(confidence)
                    .fetchedAt(LocalDateTime.now())
                    .build());

            applyResult(lead, result);
            leadRepository.save(lead);
            finalizeJob(job, null);

        } catch (Exception e) {
            log.error("Enrichment job failed {}", jobId, e);
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
            String newEmail = null;
            Double score = null;
            if (result.has("finder") && result.get("finder") != null) {
                JsonNode data = result.get("finder").get("data");
                if (data != null) {
                    if (data.has("email"))
                        newEmail = data.get("email").asText(null);
                    if (data.has("score"))
                        score = data.get("score").asDouble() / 100.0;
                }
            }

            if (newEmail != null && (score == null || score >= confidenceMin)) {
                if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
                    // keep previous
                    var prev = objectMapper.readTree(lead.getEnrichedJson() == null ? "{}" : lead.getEnrichedJson());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) prev.with("previous")).put("email",
                            lead.getEmail());
                    lead.setEnrichedJson(prev.toString());
                }
                lead.setEmail(newEmail);
            }

            // store raw providers
            var root = objectMapper.readTree(lead.getEnrichedJson() == null ? "{}" : lead.getEnrichedJson());
            ((com.fasterxml.jackson.databind.node.ObjectNode) root.with("providers")).set("hunter", result);
            lead.setEnrichedJson(root.toString());
        } catch (Exception e) {
            log.warn("applyResult error", e);
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
