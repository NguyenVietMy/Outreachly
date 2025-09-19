package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.Company;
import com.outreachly.outreachly.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public ResponseEntity<?> getCompanies(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            log.info("Fetching companies with search: '{}', page: {}, size: {}", search, page, size);

            Page<Company> companies = companyService.getCompanies(search, page, size);
            long totalCount = companyService.getCompanyCount(search);

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
    public ResponseEntity<?> createCompany(@RequestBody CreateCompanyRequest request) {
        try {
            log.info("Creating company: name='{}', domain='{}'", request.getName(), request.getDomain());

            Company company = companyService.createCompany(request.getName(), request.getDomain());
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
}
