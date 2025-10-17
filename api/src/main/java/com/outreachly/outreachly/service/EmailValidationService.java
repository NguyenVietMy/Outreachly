package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.repository.LeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class EmailValidationService {

    @Autowired
    private LeadRepository leadRepository;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * Extract all personalization variables from email content
     * 
     * @param content Email subject and body content
     * @return Set of unique variable names
     */
    public Set<String> extractVariables(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new HashSet<>();
        }

        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);

        while (matcher.find()) {
            String variable = matcher.group(1).trim();
            variables.add(variable);
        }

        return variables;
    }

    /**
     * Extract variables from both subject and body
     * 
     * @param subject Email subject
     * @param body    Email body
     * @return Set of unique variable names
     */
    public Set<String> extractVariablesFromEmail(String subject, String body) {
        String combinedContent = (subject != null ? subject : "") + " " + (body != null ? body : "");
        return extractVariables(combinedContent);
    }

    /**
     * Validate if a lead has all required variables
     * 
     * @param lead              Lead entity
     * @param requiredVariables Set of required variable names
     * @return LeadValidationResult
     */
    public LeadValidationResult validateLead(Lead lead, Set<String> requiredVariables) {
        List<String> missingVariables = new ArrayList<>();

        for (String variable : requiredVariables) {
            String value = getLeadVariableValue(lead, variable);
            if (value == null || value.trim().isEmpty()) {
                missingVariables.add(variable);
            }
        }

        return new LeadValidationResult(
                lead.getId().toString(),
                lead.getEmail(),
                missingVariables.isEmpty(),
                missingVariables,
                lead.getFirstName(),
                lead.getLastName(),
                lead.getDomain());
    }

    /**
     * Get the value of a specific variable from a lead
     * 
     * @param lead     Lead entity
     * @param variable Variable name
     * @return Variable value or null
     */
    private String getLeadVariableValue(Lead lead, String variable) {
        if (lead == null || variable == null) {
            return null;
        }

        String lowerVariable = variable.toLowerCase();

        switch (lowerVariable) {
            case "firstname":
                return lead.getFirstName();
            case "lastname":
                return lead.getLastName();
            case "fullname":
                String firstName = lead.getFirstName() != null ? lead.getFirstName() : "";
                String lastName = lead.getLastName() != null ? lead.getLastName() : "";
                String fullName = (firstName + " " + lastName).trim();
                return fullName.isEmpty() ? null : fullName;
            case "companyname":
                return lead.getDomain();
            case "domain":
                return lead.getDomain();
            case "position":
                return lead.getPosition();
            case "title":
                return lead.getPosition();
            case "linkedinurl":
                return lead.getLinkedinUrl();
            case "linkedin":
                return lead.getLinkedinUrl();
            case "email":
                return lead.getEmail();
            case "phone":
                return lead.getPhone();
            default:
                return null;
        }
    }

    /**
     * Validate multiple leads against required variables
     * 
     * @param leadIds           List of lead IDs (as strings)
     * @param orgId             Organization ID
     * @param requiredVariables Set of required variable names
     * @return ValidationResponse with validation results
     */
    public ValidationResponse validateLeads(List<String> leadIds, String orgId, Set<String> requiredVariables) {
        // Convert string IDs to UUIDs and String orgId to UUID
        List<UUID> uuidLeadIds = leadIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
        UUID uuidOrgId = UUID.fromString(orgId);

        List<Lead> leads = leadRepository.findByIdInAndOrgId(uuidLeadIds, uuidOrgId);

        List<LeadValidationResult> results = leads.stream()
                .map(lead -> validateLead(lead, requiredVariables))
                .collect(Collectors.toList());

        List<LeadValidationResult> validLeads = results.stream()
                .filter(LeadValidationResult::isValid)
                .collect(Collectors.toList());

        List<LeadValidationResult> invalidLeads = results.stream()
                .filter(result -> !result.isValid())
                .collect(Collectors.toList());

        return new ValidationResponse(
                validLeads,
                invalidLeads,
                leads.size(),
                validLeads.size(),
                invalidLeads.size());
    }

    /**
     * Lead validation result
     */
    public static class LeadValidationResult {
        private String leadId;
        private String email;
        private boolean isValid;
        private List<String> missingVariables;
        private String firstName;
        private String lastName;
        private String companyName;

        public LeadValidationResult(String leadId, String email, boolean isValid,
                List<String> missingVariables, String firstName,
                String lastName, String companyName) {
            this.leadId = leadId;
            this.email = email;
            this.isValid = isValid;
            this.missingVariables = missingVariables;
            this.firstName = firstName;
            this.lastName = lastName;
            this.companyName = companyName;
        }

        // Getters and setters
        public String getLeadId() {
            return leadId;
        }

        public void setLeadId(String leadId) {
            this.leadId = leadId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public boolean isValid() {
            return isValid;
        }

        public void setValid(boolean valid) {
            isValid = valid;
        }

        public List<String> getMissingVariables() {
            return missingVariables;
        }

        public void setMissingVariables(List<String> missingVariables) {
            this.missingVariables = missingVariables;
        }

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

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }
    }

    /**
     * Validation response
     */
    public static class ValidationResponse {
        private List<LeadValidationResult> validLeads;
        private List<LeadValidationResult> invalidLeads;
        private int totalLeads;
        private int validCount;
        private int invalidCount;

        public ValidationResponse(List<LeadValidationResult> validLeads,
                List<LeadValidationResult> invalidLeads,
                int totalLeads, int validCount, int invalidCount) {
            this.validLeads = validLeads;
            this.invalidLeads = invalidLeads;
            this.totalLeads = totalLeads;
            this.validCount = validCount;
            this.invalidCount = invalidCount;
        }

        // Getters and setters
        public List<LeadValidationResult> getValidLeads() {
            return validLeads;
        }

        public void setValidLeads(List<LeadValidationResult> validLeads) {
            this.validLeads = validLeads;
        }

        public List<LeadValidationResult> getInvalidLeads() {
            return invalidLeads;
        }

        public void setInvalidLeads(List<LeadValidationResult> invalidLeads) {
            this.invalidLeads = invalidLeads;
        }

        public int getTotalLeads() {
            return totalLeads;
        }

        public void setTotalLeads(int totalLeads) {
            this.totalLeads = totalLeads;
        }

        public int getValidCount() {
            return validCount;
        }

        public void setValidCount(int validCount) {
            this.validCount = validCount;
        }

        public int getInvalidCount() {
            return invalidCount;
        }

        public void setInvalidCount(int invalidCount) {
            this.invalidCount = invalidCount;
        }
    }
}
