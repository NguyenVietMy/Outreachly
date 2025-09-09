package com.outreachly.outreachly.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.outreachly.outreachly.entity.ImportJob;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.Organization;
import com.outreachly.outreachly.repository.ImportJobRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.OrganizationRepository;
// import com.outreachly.outreachly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final LeadRepository leadRepository;
    private final ImportJobRepository importJobRepository;
    private final OrganizationRepository organizationRepository;
    // private final UserRepository userRepository; // Will be used when
    // implementing proper user lookup
    // private final EmailValidator emailValidator = EmailValidator.getInstance();

    public static class CsvValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<Map<String, String>> data;

        public CsvValidationResult(boolean valid, List<String> errors, List<Map<String, String>> data) {
            this.valid = valid;
            this.errors = errors;
            this.data = data;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<Map<String, String>> getData() {
            return data;
        }
    }

    public CsvValidationResult validateCsvFile(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> data = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();

            if (rows.isEmpty()) {
                errors.add("CSV file is empty");
                return new CsvValidationResult(false, errors, data);
            }

            String[] headers = rows.get(0);
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                // Remove BOM and clean the header
                String header = headers[i].trim()
                        .replace("\uFEFF", "") // Remove BOM
                        .replace("?", "") // Remove question marks
                        .toLowerCase();
                log.info("Found header: '{}' (original: '{}')", header, headers[i]);
                headerMap.put(header, i);
            }
            log.info("All headers: {}", headerMap.keySet());

            // Check required headers - be more flexible (including BOM versions)
            boolean hasEmail = headerMap.containsKey("email") ||
                    headerMap.containsKey("?email") ||
                    headerMap.containsKey("e-mail") ||
                    headerMap.containsKey("email address") ||
                    headerMap.containsKey("mail");
            boolean hasFirstName = headerMap.containsKey("first_name") ||
                    headerMap.containsKey("?first_name") ||
                    headerMap.containsKey("firstname") ||
                    headerMap.containsKey("first name") ||
                    headerMap.containsKey("fname");

            if (!hasEmail) {
                errors.add("Missing required column: email (or e-mail, email address, mail)");
            }
            if (!hasFirstName) {
                errors.add("Missing required column: first_name (or firstname, first name, fname)");
            }

            if (!errors.isEmpty()) {
                return new CsvValidationResult(false, errors, data);
            }

            // Validate data rows
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                List<String> rowErrors = validateRow(row, headerMap, i + 1);
                errors.addAll(rowErrors);

                if (rowErrors.isEmpty()) {
                    Map<String, String> rowData = new HashMap<>();
                    for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                        String value = entry.getValue() < row.length ? row[entry.getValue()] : "";
                        rowData.put(entry.getKey(), value.trim());
                    }
                    data.add(rowData);
                }
            }

            return new CsvValidationResult(errors.isEmpty(), errors, data);

        } catch (IOException | CsvException e) {
            log.error("Error reading CSV file", e);
            errors.add("Error reading CSV file: " + e.getMessage());
            return new CsvValidationResult(false, errors, data);
        }
    }

    private List<String> validateRow(String[] row, Map<String, Integer> headerMap, int rowNumber) {
        List<String> errors = new ArrayList<>();

        // Check required fields - be flexible with column names (including BOM
        // versions)
        String email = getValueFlexible(row, headerMap, "email", "?email", "e-mail", "email address", "mail");
        String firstName = getValueFlexible(row, headerMap, "first_name", "?first_name", "firstname", "first name",
                "fname");

        if (email == null || email.trim().isEmpty()) {
            errors.add("Row " + rowNumber + ": Email is required");
        } else if (!isValidEmail(email.trim().toLowerCase())) {
            errors.add("Row " + rowNumber + ": Invalid email format");
        }

        if (firstName == null || firstName.trim().isEmpty()) {
            errors.add("Row " + rowNumber + ": First name is required");
        }

        return errors;
    }

    private String getValue(String[] row, Map<String, Integer> headerMap, String columnName) {
        Integer index = headerMap.get(columnName);
        if (index != null && index < row.length) {
            return row[index];
        }
        return null;
    }

    private String getValueFlexible(String[] row, Map<String, Integer> headerMap, String... possibleNames) {
        for (String name : possibleNames) {
            String value = getValue(row, headerMap, name);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String getValueFromRow(Map<String, String> row, String... possibleNames) {
        for (String name : possibleNames) {
            String value = row.get(name);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public ImportJob createImportJob(Long userId, UUID orgId, String filename, int totalRows) {
        ImportJob importJob = ImportJob.builder()
                .orgId(orgId)
                .userId(userId)
                .filename(filename)
                .status(ImportJob.ImportStatus.PENDING)
                .totalRows(totalRows)
                .processedRows(0)
                .errorRows(0)
                .build();

        return importJobRepository.save(importJob);
    }

    @Async
    public CompletableFuture<Void> processImportJob(UUID jobId, List<Map<String, String>> data) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Import job not found"));

        try {
            job.setStatus(ImportJob.ImportStatus.PROCESSING);
            importJobRepository.save(job);

            int processedRows = 0;
            int errorRows = 0;
            Set<String> processedEmails = new HashSet<>();

            for (Map<String, String> row : data) {
                try {
                    String email = row.get("email").trim().toLowerCase();

                    // Skip duplicates
                    if (processedEmails.contains(email)) {
                        continue;
                    }
                    processedEmails.add(email);

                    // Check if lead already exists
                    Optional<Lead> existingLead = leadRepository.findByEmailAndOrgId(email, job.getOrgId());
                    if (existingLead.isPresent()) {
                        continue; // Skip existing leads
                    }

                    Lead lead = buildLeadFromRow(row, job.getOrgId());
                    leadRepository.save(lead);
                    processedRows++;

                } catch (Exception e) {
                    log.error("Error processing row in import job {}: {}", jobId, e.getMessage());
                    errorRows++;
                }
            }

            job.setProcessedRows(processedRows);
            job.setErrorRows(errorRows);
            job.setStatus(ImportJob.ImportStatus.COMPLETED);
            importJobRepository.save(job);

        } catch (Exception e) {
            log.error("Error processing import job {}: {}", jobId, e.getMessage());
            job.setStatus(ImportJob.ImportStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            importJobRepository.save(job);
        }

        return CompletableFuture.completedFuture(null);
    }

    public UUID getOrCreateDefaultOrganization() {
        // Try to find an existing organization first
        return organizationRepository.findAll().stream()
                .findFirst()
                .map(Organization::getId)
                .orElseGet(() -> {
                    // Create a default organization if none exists
                    Organization defaultOrg = Organization.builder()
                            .name("Default Organization")
                            .plan("free")
                            .build();
                    Organization savedOrg = organizationRepository.save(defaultOrg);
                    log.info("Created default organization with ID: {}", savedOrg.getId());
                    return savedOrg.getId();
                });
    }

    private Lead buildLeadFromRow(Map<String, String> row, UUID orgId) {
        // Get email flexibly (including BOM versions)
        String email = getValueFromRow(row, "email", "?email", "e-mail", "email address", "mail");
        String domain = extractDomainFromEmail(email);

        // Use provided domain if available, otherwise extract from email
        String providedDomain = getValueFromRow(row, "domain", "website", "company domain");
        if (providedDomain != null && !providedDomain.trim().isEmpty()) {
            domain = providedDomain.trim().toLowerCase();
        }

        return Lead.builder()
                .orgId(orgId)
                .firstName(trimValue(
                        getValueFromRow(row, "first_name", "?first_name", "firstname", "first name", "fname")))
                .lastName(trimValue(getValueFromRow(row, "last_name", "lastname", "last name", "lname")))
                .company(trimValue(getValueFromRow(row, "company", "company name", "organization")))
                .domain(domain)
                .title(trimValue(getValueFromRow(row, "title", "job title", "position", "role")))
                .email(email)
                .phone(trimValue(getValueFromRow(row, "phone", "phone number", "mobile", "telephone")))
                .linkedinUrl(trimValue(getValueFromRow(row, "linkedin_url", "linkedin", "linkedin profile")))
                .country(trimValue(getValueFromRow(row, "country", "nation")))
                .state(trimValue(getValueFromRow(row, "state", "province", "region")))
                .city(trimValue(getValueFromRow(row, "city", "town")))
                .customTextField(trimValue(getValueFromRow(row, "custom_text_field", "notes", "comments", "custom")))
                .source("csv_import")
                .verifiedStatus(Lead.VerifiedStatus.UNKNOWN)
                .enrichedJson("{}")
                .build();
    }

    private String extractDomainFromEmail(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf("@") + 1).toLowerCase();
        }
        return null;
    }

    private String trimValue(String value) {
        return value != null ? value.trim() : null;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Simple email validation regex
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }

    public List<ImportJob> getImportHistory(UUID orgId) {
        return importJobRepository.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    public ImportJob getImportJob(UUID jobId) {
        return importJobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Import job not found"));
    }
}
