package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.OrgLead;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.OrgLeadRepository;
import com.outreachly.outreachly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadDataService {

    private final LeadRepository leadRepository;
    private final OrgLeadRepository orgLeadRepository;
    private final UserRepository userRepository;

    /**
     * Get lead data for email personalization by email address
     */
    @Transactional(readOnly = true)
    public Map<String, String> getLeadDataForEmail(String email, UUID orgId) {
        Map<String, String> leadData = new HashMap<>();

        try {
            // First try to find lead in org-specific mapping
            Optional<OrgLead> orgLead = orgLeadRepository.findByOrgIdAndEmailIgnoreCase(orgId, email);

            if (orgLead.isPresent()) {
                Lead lead = orgLead.get().getLead();
                // Access all properties within transaction to avoid lazy loading issues
                populateLeadData(leadData, lead);
                log.debug("Found lead data for email: {} in org: {}", email, orgId);
            } else {
                // Fallback to global lead lookup
                Optional<Lead> globalLead = leadRepository.findByEmailIgnoreCase(email);
                if (globalLead.isPresent()) {
                    Lead lead = globalLead.get();
                    populateLeadData(leadData, lead);
                    log.debug("Found global lead data for email: {}", email);
                } else {
                    log.debug("No lead data found for email: {}", email);
                }
            }

            // Always include the email itself
            leadData.put("email", email);

            // Extract first name from email if not available
            if (!leadData.containsKey("firstName") || leadData.get("firstName") == null) {
                String firstName = extractFirstNameFromEmail(email);
                leadData.put("firstName", firstName);
            }

        } catch (Exception e) {
            log.error("Error fetching lead data for email: {}", email, e);
            // Return basic data even if there's an error
            leadData.put("email", email);
            leadData.put("firstName", extractFirstNameFromEmail(email));
        }

        return leadData;
    }

    /**
     * Get lead data for email personalization by user authentication
     */
    @Transactional(readOnly = true)
    public Map<String, String> getLeadDataForEmail(String email, String userEmail) {
        try {
            // Find user by email to get orgId
            Optional<User> user = userRepository.findByEmail(userEmail);
            if (user.isPresent()) {
                return getLeadDataForEmail(email, user.get().getOrgId());
            } else {
                log.warn("User not found for email: {}", userEmail);
                return getBasicLeadData(email);
            }
        } catch (Exception e) {
            log.error("Error fetching lead data for email: {} with user: {}", email, userEmail, e);
            return getBasicLeadData(email);
        }
    }

    /**
     * Populate lead data map from Lead entity
     */
    private void populateLeadData(Map<String, String> leadData, Lead lead) {
        try {
            if (lead.getFirstName() != null) {
                leadData.put("firstName", lead.getFirstName());
            }
            if (lead.getLastName() != null) {
                leadData.put("lastName", lead.getLastName());
            }
            if (lead.getEmail() != null) {
                leadData.put("email", lead.getEmail());
            }
            if (lead.getDomain() != null) {
                leadData.put("domain", lead.getDomain());
            }
            if (lead.getPosition() != null) {
                leadData.put("position", lead.getPosition());
            }
            if (lead.getPositionRaw() != null) {
                leadData.put("positionRaw", lead.getPositionRaw());
            }
            if (lead.getSeniority() != null) {
                leadData.put("seniority", lead.getSeniority());
            }
            if (lead.getDepartment() != null) {
                leadData.put("department", lead.getDepartment());
            }
            if (lead.getLinkedinUrl() != null) {
                leadData.put("linkedinUrl", lead.getLinkedinUrl());
            }
            if (lead.getTwitter() != null) {
                leadData.put("twitter", lead.getTwitter());
            }
            if (lead.getPhone() != null) {
                leadData.put("phone", lead.getPhone());
            }
            if (lead.getCustomTextField() != null) {
                leadData.put("customTextField", lead.getCustomTextField());
            }
            if (lead.getSource() != null) {
                leadData.put("source", lead.getSource());
            }

            // Add computed fields
            if (lead.getFirstName() != null && lead.getLastName() != null) {
                leadData.put("fullName", lead.getFirstName() + " " + lead.getLastName());
            } else if (lead.getFirstName() != null) {
                leadData.put("fullName", lead.getFirstName());
            } else if (lead.getLastName() != null) {
                leadData.put("fullName", lead.getLastName());
            }

            // Add company name from domain if available
            if (lead.getDomain() != null) {
                String companyName = extractCompanyNameFromDomain(lead.getDomain());
                leadData.put("companyName", companyName);
            }
        } catch (Exception e) {
            log.error("Error populating lead data for lead: {}", lead.getId(), e);
            // Continue with basic data even if there's an error
        }
    }

    /**
     * Extract first name from email address
     */
    private String extractFirstNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "there";
        }

        String localPart = email.split("@")[0];

        // Handle common patterns
        if (localPart.contains(".")) {
            String[] parts = localPart.split("\\.");
            if (parts.length > 0) {
                return capitalizeFirstLetter(parts[0]);
            }
        }

        if (localPart.contains("_")) {
            String[] parts = localPart.split("_");
            if (parts.length > 0) {
                return capitalizeFirstLetter(parts[0]);
            }
        }

        if (localPart.contains("-")) {
            String[] parts = localPart.split("-");
            if (parts.length > 0) {
                return capitalizeFirstLetter(parts[0]);
            }
        }

        // If no separators, use the whole local part
        return capitalizeFirstLetter(localPart);
    }

    /**
     * Extract company name from domain
     */
    private String extractCompanyNameFromDomain(String domain) {
        if (domain == null) {
            return "";
        }

        // Remove www. prefix
        String companyName = domain.replaceFirst("^www\\.", "");

        // Remove common TLDs
        companyName = companyName.replaceFirst("\\.[a-z]{2,4}$", "");

        // Capitalize first letter
        return capitalizeFirstLetter(companyName);
    }

    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Get basic lead data when no database record exists
     */
    private Map<String, String> getBasicLeadData(String email) {
        Map<String, String> basicData = new HashMap<>();
        basicData.put("email", email);
        basicData.put("firstName", extractFirstNameFromEmail(email));
        return basicData;
    }
}
