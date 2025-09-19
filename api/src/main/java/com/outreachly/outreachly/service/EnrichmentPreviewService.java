package com.outreachly.outreachly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentPreviewService {

    private final LeadRepository leadRepository;
    private final HunterClient hunterClient;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ENRICH_CONFIDENCE_MIN:0.6}")
    private double confidenceMin;

    /**
     * Get enrichment preview for a lead without applying changes
     */
    public JsonNode getEnrichmentPreview(UUID leadId) throws Exception {
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new IllegalArgumentException("Lead not found"));

        // Build preview data structure
        ObjectNode preview = objectMapper.createObjectNode();
        preview.put("leadId", leadId.toString());
        preview.put("timestamp", LocalDateTime.now().toString());

        // Current lead data
        ObjectNode currentData = objectMapper.createObjectNode();
        currentData.put("firstName", lead.getFirstName());
        currentData.put("lastName", lead.getLastName());
        currentData.put("email", lead.getEmail());
        currentData.put("domain", lead.getDomain());
        currentData.put("position", lead.getPosition());
        currentData.put("positionRaw", lead.getPositionRaw());
        currentData.put("seniority", lead.getSeniority());
        currentData.put("department", lead.getDepartment());
        currentData.put("phone", lead.getPhone());
        currentData.put("linkedinUrl", lead.getLinkedinUrl());
        currentData.put("twitter", lead.getTwitter());
        currentData.put("confidenceScore", lead.getConfidenceScore());
        currentData.put("emailType", lead.getEmailType() != null ? lead.getEmailType().toString() : null);
        preview.set("currentData", currentData);

        // Hunter enrichment data
        ObjectNode hunterData = objectMapper.createObjectNode();

        // Email finder if no email exists
        if (lead.getEmail() == null || lead.getEmail().isBlank()) {
            if (lead.getFirstName() != null && lead.getLastName() != null && lead.getDomain() != null) {
                try {
                    JsonNode finder = hunterClient.emailFinder(lead.getDomain(), lead.getFirstName(),
                            lead.getLastName());
                    hunterData.set("emailFinder", finder);
                } catch (Exception e) {
                    log.warn("Email finder failed for lead {}: {}", leadId, e.getMessage());
                    hunterData.put("emailFinderError", e.getMessage());
                }
            }
        }

        // Email verifier if email exists
        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            try {
                JsonNode verifier = hunterClient.emailVerifier(lead.getEmail());
                hunterData.set("emailVerifier", verifier);
            } catch (Exception e) {
                log.warn("Email verifier failed for lead {}: {}", leadId, e.getMessage());
                hunterData.put("emailVerifierError", e.getMessage());
            }
        }

        // Domain search for additional contacts
        if (lead.getDomain() != null && !lead.getDomain().isBlank()) {
            try {
                JsonNode domainSearch = hunterClient.domainSearch(lead.getDomain(), 10);
                hunterData.set("domainSearch", domainSearch);
            } catch (Exception e) {
                log.warn("Domain search failed for lead {}: {}", leadId, e.getMessage());
                hunterData.put("domainSearchError", e.getMessage());
            }
        }

        // Company search
        if (lead.getDomain() != null && !lead.getDomain().isBlank()) {
            try {
                JsonNode companySearch = hunterClient.companySearch(lead.getDomain());
                hunterData.set("companySearch", companySearch);
            } catch (Exception e) {
                log.warn("Company search failed for lead {}: {}", leadId, e.getMessage());
                hunterData.put("companySearchError", e.getMessage());
            }
        }

        preview.set("hunterData", hunterData);

        // Generate suggested changes
        ObjectNode suggestedChanges = generateSuggestedChanges(currentData, hunterData);
        preview.set("suggestedChanges", suggestedChanges);
        return preview;
    }

    /**
     * Apply enrichment changes to a lead
     */
    @Transactional
    public JsonNode applyEnrichment(UUID leadId, JsonNode acceptedChanges) throws Exception {
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new IllegalArgumentException("Lead not found"));

        // Store current data in history before applying changes
        addToEnrichmentHistory(lead, "applied", acceptedChanges);

        // Apply changes based on accepted changes
        boolean hasChanges = false;

        if (acceptedChanges.has("email") && !acceptedChanges.get("email").isNull()) {
            String newEmail = acceptedChanges.get("email").asText();
            if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(lead.getEmail())) {
                // Store previous email in enriched_json
                storePreviousData(lead, "email", lead.getEmail());
                lead.setEmail(newEmail);
                hasChanges = true;
            }
        }

        if (acceptedChanges.has("firstName") && !acceptedChanges.get("firstName").isNull()) {
            String newFirstName = acceptedChanges.get("firstName").asText();
            if (newFirstName != null && !newFirstName.equals(lead.getFirstName())) {
                storePreviousData(lead, "firstName", lead.getFirstName());
                lead.setFirstName(newFirstName);
                hasChanges = true;
            }
        }

        if (acceptedChanges.has("lastName") && !acceptedChanges.get("lastName").isNull()) {
            String newLastName = acceptedChanges.get("lastName").asText();
            if (newLastName != null && !newLastName.equals(lead.getLastName())) {
                storePreviousData(lead, "lastName", lead.getLastName());
                lead.setLastName(newLastName);
                hasChanges = true;
            }
        }

        if (acceptedChanges.has("phone") && !acceptedChanges.get("phone").isNull()) {
            String newPhone = acceptedChanges.get("phone").asText();
            if (newPhone != null && !newPhone.equals(lead.getPhone())) {
                storePreviousData(lead, "phone", lead.getPhone());
                lead.setPhone(newPhone);
                hasChanges = true;
            }
        }

        if (acceptedChanges.has("linkedinUrl") && !acceptedChanges.get("linkedinUrl").isNull()) {
            String newLinkedinUrl = acceptedChanges.get("linkedinUrl").asText();
            if (newLinkedinUrl != null && !newLinkedinUrl.equals(lead.getLinkedinUrl())) {
                storePreviousData(lead, "linkedinUrl", lead.getLinkedinUrl());
                lead.setLinkedinUrl(newLinkedinUrl);
                hasChanges = true;
            }
        }

        if (hasChanges) {
            leadRepository.save(lead);

            // Send webhook notification
            webhookService.sendEnrichmentCompleted(leadId, lead.getOrgId(), "applied",
                    Map.of("changesApplied", hasChanges, "acceptedChanges", acceptedChanges));
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", true);
        result.put("leadId", leadId.toString());
        result.put("changesApplied", hasChanges);
        result.put("timestamp", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Revert lead to previous state
     */
    @Transactional
    public JsonNode revertEnrichment(UUID leadId, int historyIndex) throws Exception {
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new IllegalArgumentException("Lead not found"));

        JsonNode history = objectMapper
                .readTree(lead.getEnrichmentHistory() != null ? lead.getEnrichmentHistory() : "[]");
        if (!history.isArray() || historyIndex >= history.size()) {
            throw new IllegalArgumentException("Invalid history index");
        }

        JsonNode previousState = history.get(historyIndex);
        if (!previousState.has("previousData")) {
            throw new IllegalArgumentException("No previous data found at index " + historyIndex);
        }

        // Store current state before reverting
        addToEnrichmentHistory(lead, "reverted", previousState.get("previousData"));

        // Revert to previous state
        JsonNode previousData = previousState.get("previousData");

        if (previousData.has("email")) {
            lead.setEmail(previousData.get("email").asText(null));
        }
        if (previousData.has("firstName")) {
            lead.setFirstName(previousData.get("firstName").asText(null));
        }
        if (previousData.has("lastName")) {
            lead.setLastName(previousData.get("lastName").asText(null));
        }
        if (previousData.has("phone")) {
            lead.setPhone(previousData.get("phone").asText(null));
        }
        if (previousData.has("linkedinUrl")) {
            lead.setLinkedinUrl(previousData.get("linkedinUrl").asText(null));
        }

        leadRepository.save(lead);

        // Send webhook notification
        webhookService.sendEnrichmentCompleted(leadId, lead.getOrgId(), "reverted",
                Map.of("revertedToIndex", historyIndex));

        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", true);
        result.put("leadId", leadId.toString());
        result.put("revertedToIndex", historyIndex);
        result.put("timestamp", LocalDateTime.now().toString());

        return result;
    }

    private ObjectNode generateSuggestedChanges(JsonNode currentData, JsonNode hunterData) {
        ObjectNode suggested = objectMapper.createObjectNode();

        // Email suggestions
        if (hunterData.has("emailFinder") && hunterData.get("emailFinder").has("data")) {
            JsonNode finderData = hunterData.get("emailFinder").get("data");
            if (finderData.has("email")) {
                String suggestedEmail = finderData.get("email").asText();
                double confidence = finderData.has("score") ? finderData.get("score").asDouble() / 100.0 : 0.0;

                if (confidence >= confidenceMin) {
                    suggested.put("email", suggestedEmail);
                    suggested.put("emailConfidence", confidence);
                }
            }
        }

        // Email verification status and risk assessment
        if (hunterData.has("emailVerifier") && hunterData.get("emailVerifier").has("data")) {
            JsonNode verifierData = hunterData.get("emailVerifier").get("data");
            if (verifierData.has("status")) {
                String status = verifierData.get("status").asText();
                suggested.put("emailStatus", status);

                // Mark as risky if status is "accept_all"
                if ("accept_all".equals(status)) {
                    suggested.put("emailRisk", "risky");
                    suggested.put("emailRiskReason", "Email server accepts all emails (catch-all)");
                } else if ("deliverable".equals(status)) {
                    suggested.put("emailRisk", "safe");
                } else if ("undeliverable".equals(status)) {
                    suggested.put("emailRisk", "invalid");
                } else {
                    suggested.put("emailRisk", "unknown");
                }
            }
        }

        // Company information from company search
        if (hunterData.has("companySearch") && hunterData.get("companySearch").has("data")) {
            JsonNode companyData = hunterData.get("companySearch").get("data");

            if (companyData.has("name")
                    && !companyData.get("name").asText().equals(currentData.get("company").asText())) {
                suggested.put("company", companyData.get("name").asText());
            }

            if (companyData.has("industry")
                    && !companyData.get("industry").asText().equals(currentData.get("title").asText())) {
                suggested.put("title", companyData.get("industry").asText());
            }

            if (companyData.has("country")
                    && !companyData.get("country").asText().equals(currentData.get("country").asText())) {
                suggested.put("country", companyData.get("country").asText());
            }
        }

        return suggested;
    }

    private void addToEnrichmentHistory(Lead lead, String action, JsonNode data) {
        try {
            JsonNode history = objectMapper
                    .readTree(lead.getEnrichmentHistory() != null ? lead.getEnrichmentHistory() : "[]");
            ArrayNode historyArray = (ArrayNode) history;

            ObjectNode historyEntry = objectMapper.createObjectNode();
            historyEntry.put("action", action);
            historyEntry.put("timestamp", LocalDateTime.now().toString());
            historyEntry.set("data", data);

            historyArray.add(historyEntry);
            lead.setEnrichmentHistory(historyArray.toString());
        } catch (Exception e) {
            log.warn("Failed to add to enrichment history: {}", e.getMessage());
        }
    }

    private void storePreviousData(Lead lead, String field, String value) {
        try {
            JsonNode enrichedJson = objectMapper
                    .readTree(lead.getEnrichedJson() != null ? lead.getEnrichedJson() : "{}");
            ObjectNode enriched = (ObjectNode) enrichedJson;

            if (!enriched.has("previous")) {
                enriched.set("previous", objectMapper.createObjectNode());
            }

            ObjectNode previous = (ObjectNode) enriched.get("previous");
            previous.put(field, value);

            lead.setEnrichedJson(enriched.toString());
        } catch (Exception e) {
            log.warn("Failed to store previous data for field {}: {}", field, e.getMessage());
        }
    }
}
