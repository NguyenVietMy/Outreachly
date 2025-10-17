package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.service.EmailValidationService;
import com.outreachly.outreachly.service.EmailValidationService.ValidationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/emails")
@CrossOrigin(origins = "*")
public class EmailValidationController {

    @Autowired
    private EmailValidationService emailValidationService;

    /**
     * Extract variables from email content
     * POST /api/emails/extract-variables
     */
    @PostMapping("/extract-variables")
    public ResponseEntity<VariableExtractionResponse> extractVariables(
            @RequestBody VariableExtractionRequest request) {
        
        Set<String> variables = emailValidationService.extractVariablesFromEmail(
            request.getSubject(), 
            request.getBody()
        );
        
        VariableExtractionResponse response = new VariableExtractionResponse(
            variables.stream().toList(),
            variables.size(),
            (request.getSubject() != null ? request.getSubject() : "") + " " + (request.getBody() != null ? request.getBody() : "")
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Validate leads against required variables
     * POST /api/emails/validate-leads
     */
    @PostMapping("/validate-leads")
    public ResponseEntity<ValidationResponse> validateLeads(
            @RequestBody LeadValidationRequest request) {
        
        ValidationResponse response = emailValidationService.validateLeads(
            request.getLeadIds(),
            request.getOrgId(),
            request.getRequiredVariables()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Request DTO for variable extraction
     */
    public static class VariableExtractionRequest {
        private String subject;
        private String body;

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    /**
     * Response DTO for variable extraction
     */
    public static class VariableExtractionResponse {
        private List<String> requiredVariables;
        private int totalVariables;
        private String emailContent;

        public VariableExtractionResponse(List<String> requiredVariables, int totalVariables, String emailContent) {
            this.requiredVariables = requiredVariables;
            this.totalVariables = totalVariables;
            this.emailContent = emailContent;
        }

        public List<String> getRequiredVariables() { return requiredVariables; }
        public void setRequiredVariables(List<String> requiredVariables) { this.requiredVariables = requiredVariables; }
        
        public int getTotalVariables() { return totalVariables; }
        public void setTotalVariables(int totalVariables) { this.totalVariables = totalVariables; }
        
        public String getEmailContent() { return emailContent; }
        public void setEmailContent(String emailContent) { this.emailContent = emailContent; }
    }

    /**
     * Request DTO for lead validation
     */
    public static class LeadValidationRequest {
        private List<String> leadIds;
        private String orgId;
        private Set<String> requiredVariables;

        public List<String> getLeadIds() { return leadIds; }
        public void setLeadIds(List<String> leadIds) { this.leadIds = leadIds; }
        
        public String getOrgId() { return orgId; }
        public void setOrgId(String orgId) { this.orgId = orgId; }
        
        public Set<String> getRequiredVariables() { return requiredVariables; }
        public void setRequiredVariables(Set<String> requiredVariables) { this.requiredVariables = requiredVariables; }
    }
}
