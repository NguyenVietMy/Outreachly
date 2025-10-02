package com.outreachly.outreachly.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.outreachly.outreachly.entity.Company;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.CompanyService;
import com.outreachly.outreachly.service.HunterClient;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CompanyController {

    private final CompanyService companyService;
    private final HunterClient hunterClient;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getCompanies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String companyType,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String headquartersCountry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication) {

        try {
            User user = getUser(authentication);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }

            // Companies are global (NULL org id) - show ALL companies
            UUID orgId = null;

            log.info(
                    "Fetching companies with filters: search='{}', type='{}', size='{}', country='{}', page: {}, size: {}, orgId: {}",
                    search, companyType, size, headquartersCountry, page, pageSize, orgId);

            Page<Company> companies = companyService.getCompanies(search, companyType, size,
                    headquartersCountry, page, pageSize, orgId);

            return ResponseEntity.ok(Map.of(
                    "companies", companies.getContent(),
                    "totalElements", companies.getTotalElements(),
                    "totalPages", companies.getTotalPages(),
                    "currentPage", companies.getNumber(),
                    "size", companies.getSize(),
                    "hasNext", companies.hasNext(),
                    "hasPrevious", companies.hasPrevious()));
        } catch (Exception e) {
            log.error("Error fetching companies: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch companies: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCompanyById(@PathVariable UUID id) {
        try {
            log.info("Fetching company by id: {}", id);

            return companyService.getCompanyById(id)
                    .map(company -> ResponseEntity.ok(company))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching company by id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch company: " + e.getMessage()));
        }
    }

    @GetMapping("/domain/{domain}")
    public ResponseEntity<?> getCompanyByDomain(@PathVariable String domain) {
        try {
            log.info("Fetching company by domain: {}", domain);

            return companyService.getCompanyByDomain(domain)
                    .map(company -> ResponseEntity.ok(company))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching company by domain {}: {}", domain, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch company: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createCompany(@RequestBody CreateCompanyRequest request, Authentication authentication) {
        try {
            User user = getUser(authentication);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }

            UUID orgId = user.getOrgId();
            if (orgId == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Organization required"));
            }

            log.info("Creating company: name='{}', domain='{}', orgId='{}'", request.getName(), request.getDomain(),
                    orgId);

            Company company = companyService.createCompany(request.getName(), request.getDomain(), orgId);
            return ResponseEntity.ok(company);
        } catch (Exception e) {
            log.error("Error creating company: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create company: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCompany(@PathVariable UUID id, @RequestBody UpdateCompanyRequest request) {
        try {
            log.info("Updating company: id='{}', name='{}', domain='{}'", id, request.getName(), request.getDomain());

            Company company = companyService.updateCompany(id, request.getName(), request.getDomain());
            return ResponseEntity.ok(company);
        } catch (Exception e) {
            log.error("Error updating company {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update company: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCompany(@PathVariable UUID id) {
        try {
            log.info("Deleting company: id='{}'", id);

            companyService.deleteCompany(id);
            return ResponseEntity.ok(Map.of("message", "Company deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting company {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete company: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/find-emails")
    public ResponseEntity<?> findEmails(@PathVariable UUID id, @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Finding emails for company: id='{}', limit={}", id, limit);

            Company company = companyService.getCompanyById(id)
                    .orElseThrow(() -> new RuntimeException("Company not found"));

            if (company.getDomain() == null || company.getDomain().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Company domain is required for email search"));
            }

            JsonNode domainSearchResult = hunterClient.domainSearch(company.getDomain(), limit);

            // Enrich company data with Hunter API response
            if (domainSearchResult.has("data")) {
                JsonNode data = domainSearchResult.get("data");
                boolean updated = false;

                // Update industry if available and not already set
                if (data.has("industry") && !data.get("industry").isNull() &&
                        (company.getIndustry() == null || company.getIndustry().isBlank())) {
                    company.setIndustry(data.get("industry").asText());
                    updated = true;
                    log.info("Updated industry for company {}: {}", company.getName(), company.getIndustry());
                }

                // Update company type if available and not already set
                if (data.has("company_type") && !data.get("company_type").isNull() &&
                        (company.getCompanyType() == null || company.getCompanyType().isBlank())) {
                    String hunterCompanyType = data.get("company_type").asText();
                    String mappedCompanyType = mapHunterCompanyType(hunterCompanyType);
                    if (mappedCompanyType != null) {
                        company.setCompanyType(mappedCompanyType);
                        updated = true;
                        log.info("Updated company type for company {}: {} (mapped from: {})",
                                company.getName(), company.getCompanyType(), hunterCompanyType);
                    } else {
                        log.warn("Could not map Hunter company type '{}' for company {}",
                                hunterCompanyType, company.getName());
                    }
                }

                // Update size if available and not already set
                if (data.has("headcount") && !data.get("headcount").isNull() &&
                        (company.getSize() == null || company.getSize().isBlank())) {
                    String hunterSize = data.get("headcount").asText();
                    String mappedSize = mapHunterSize(hunterSize);
                    if (mappedSize != null) {
                        company.setSize(mappedSize);
                        updated = true;
                        log.info("Updated size for company {}: {} (mapped from: {})",
                                company.getName(), company.getSize(), hunterSize);
                    } else {
                        log.warn("Could not map Hunter size '{}' for company {}",
                                hunterSize, company.getName());
                    }
                }

                // Update headquarters country if available and not already set
                if (data.has("country") && !data.get("country").isNull() &&
                        (company.getHeadquartersCountry() == null || company.getHeadquartersCountry().isBlank())) {
                    company.setHeadquartersCountry(data.get("country").asText());
                    updated = true;
                    log.info("Updated headquarters country for company {}: {}", company.getName(),
                            company.getHeadquartersCountry());
                }

                // Save the updated company if any changes were made
                if (updated) {
                    companyService.saveCompany(company);
                    log.info("Company {} enriched with Hunter API data", company.getName());
                }
            }

            return ResponseEntity.ok(domainSearchResult);
        } catch (Exception e) {
            log.error("Error finding emails for company {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to find emails: " + e.getMessage()));
        }
    }

    // Request DTOs
    public static class CreateCompanyRequest {
        private String name;
        private String domain;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }

    public static class UpdateCompanyRequest {
        private String name;
        private String domain;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }

    /**
     * Maps Hunter API company type values to our database constraint values
     */
    private String mapHunterCompanyType(String hunterType) {
        if (hunterType == null)
            return null;

        String normalized = hunterType.toLowerCase().trim();

        switch (normalized) {
            case "public company":
            case "public":
                return "Public Company";
            case "privately held":
            case "private":
            case "private company":
                return "Privately Held";
            case "educational":
            case "educational institution":
                return "Educational Institution";
            case "government agency":
            case "government":
                return "Government Agency";
            case "non profit":
            case "nonprofit":
            case "non profit partnership":
                return "Non Profit Partnership";
            case "self employed":
                return "Self Employed";
            case "self owned":
                return "Self Owned";
            case "self proprietorship":
                return "Self Proprietorship";
            default:
                return null; // Return null for unmapped types
        }
    }

    /**
     * Maps Hunter API size values to our database constraint values
     */
    private String mapHunterSize(String hunterSize) {
        if (hunterSize == null)
            return null;

        String normalized = hunterSize.toLowerCase().trim();

        // Direct matches
        if (normalized.equals("1-10") || normalized.equals("1 to 10"))
            return "1-10";
        if (normalized.equals("11-50") || normalized.equals("11 to 50"))
            return "11-50";
        if (normalized.equals("51-200") || normalized.equals("51 to 200"))
            return "51-200";
        if (normalized.equals("201-500") || normalized.equals("201 to 500"))
            return "201-500";
        if (normalized.equals("501-1000") || normalized.equals("501 to 1000"))
            return "501-1000";
        if (normalized.equals("1001-5000") || normalized.equals("1001 to 5000"))
            return "1001-5000";
        if (normalized.equals("5001-10000") || normalized.equals("5001 to 10000"))
            return "5001-10000";
        if (normalized.equals("10001+") || normalized.equals("10001+") || normalized.equals("10001 and above"))
            return "10001+";

        // Try to parse numeric ranges
        if (normalized.contains("+")) {
            return "10001+";
        }

        // For now, return null for unmapped sizes to avoid constraint violations
        return null;
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userService.findByEmail(authentication.getName());
    }
}
