package com.outreachly.outreachly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.outreachly.outreachly.dto.CsvColumnMappingDto;
import com.outreachly.outreachly.entity.ImportJob;
import com.outreachly.outreachly.entity.Lead;
import com.outreachly.outreachly.entity.Organization;
import com.outreachly.outreachly.repository.ImportJobRepository;
import com.outreachly.outreachly.repository.LeadRepository;
import com.outreachly.outreachly.repository.OrganizationRepository;
import com.outreachly.outreachly.service.CampaignLeadService;
// import com.outreachly.outreachly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
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
    private final CampaignLeadService campaignLeadService;
    private final ObjectMapper objectMapper;
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

    public CsvColumnMappingDto detectCsvColumns(MultipartFile file) {
        List<CsvColumnMappingDto.CsvColumn> detectedColumns = new ArrayList<>();
        List<CsvColumnMappingDto.FieldOption> availableFields = getAvailableFieldOptions();
        Map<String, String> autoMapping = new HashMap<>();
        Set<String> usedFields = new HashSet<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();

            if (rows.isEmpty()) {
                return CsvColumnMappingDto.builder()
                        .detectedColumns(detectedColumns)
                        .availableFields(availableFields)
                        .mapping(autoMapping)
                        .hasRequiredFields(false)
                        .missingRequiredFields(List.of("CSV file is empty"))
                        .build();
            }

            String[] headers = rows.get(0);

            // Process headers and detect columns
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim()
                        .replace("\uFEFF", "") // Remove BOM
                        .replace("?", "") // Remove question marks
                        .toLowerCase();

                String originalHeader = headers[i];
                String sampleValue = rows.size() > 1 ? (rows.get(1)[i] != null ? rows.get(1)[i] : "") : "";

                // Auto-detect field type
                String autoDetectedField = autoDetectFieldType(header);
                if (autoDetectedField != null && !usedFields.contains(autoDetectedField)) {
                    autoMapping.put(originalHeader, autoDetectedField);
                    usedFields.add(autoDetectedField);
                }

                detectedColumns.add(CsvColumnMappingDto.CsvColumn.builder()
                        .name(originalHeader)
                        .displayName(originalHeader)
                        .sampleValue(sampleValue)
                        .isRequired(isRequiredField(autoDetectedField))
                        .currentMapping(autoDetectedField)
                        .build());
            }

            // Check if we have required fields
            boolean hasEmail = autoMapping.values().contains("email");
            boolean hasFirstName = autoMapping.values().contains("first_name");
            List<String> missingRequired = new ArrayList<>();
            if (!hasEmail)
                missingRequired.add("email");
            if (!hasFirstName)
                missingRequired.add("first_name");

            return CsvColumnMappingDto.builder()
                    .detectedColumns(detectedColumns)
                    .availableFields(availableFields)
                    .mapping(autoMapping)
                    .hasRequiredFields(hasEmail && hasFirstName)
                    .missingRequiredFields(missingRequired)
                    .build();

        } catch (IOException | CsvException e) {
            log.error("Error reading CSV file", e);
            return CsvColumnMappingDto.builder()
                    .detectedColumns(detectedColumns)
                    .availableFields(availableFields)
                    .mapping(autoMapping)
                    .hasRequiredFields(false)
                    .missingRequiredFields(List.of("Error reading CSV file: " + e.getMessage()))
                    .build();
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
            boolean hasEmail = hasAnyColumn(headerMap, getEmailColumnNames());
            boolean hasFirstName = hasAnyColumn(headerMap, getFirstNameColumnNames());

            if (!hasEmail) {
                errors.add("Missing required column: email (supports all common variations including BOM versions)");
            }
            if (!hasFirstName) {
                errors.add(
                        "Missing required column: first_name (supports all common variations including BOM versions)");
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
                        rowData.put(entry.getKey(), value != null ? value.trim() : "");
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
        String email = getValueFlexible(row, headerMap, getEmailColumnNames());
        String firstName = getValueFlexible(row, headerMap, getFirstNameColumnNames());

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
        return processImportJob(jobId, data, null);
    }

    @Async
    public CompletableFuture<Void> processImportJobWithMapping(UUID jobId, MultipartFile file,
            Map<String, String> columnMapping, UUID campaignId) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Import job not found"));

        try {
            job.setStatus(ImportJob.ImportStatus.PROCESSING);
            importJobRepository.save(job);

            // Parse CSV with user mapping
            List<Map<String, String>> mappedData = parseCsvWithMapping(file, columnMapping);

            int processedRows = 0;
            int errorRows = 0;
            Set<String> processedEmails = new HashSet<>();

            for (Map<String, String> row : mappedData) {
                try {
                    String email = row.get("email");
                    if (email == null || email.trim().isEmpty()) {
                        log.warn("Skipping row with null or empty email in import job {}", jobId);
                        continue;
                    }
                    email = email.trim().toLowerCase();

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

                    Lead lead = buildLeadFromMappedRow(row, job.getOrgId(), columnMapping);
                    lead = leadRepository.save(lead);

                    // If campaignId is provided, create campaign-lead relationship
                    if (campaignId != null) {
                        campaignLeadService.addLeadToCampaign(campaignId, lead.getId(), null);
                    }

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

    @Async
    public CompletableFuture<Void> processImportJob(UUID jobId, List<Map<String, String>> data, UUID campaignId) {
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
                    String emailValue = row.get("email");
                    if (emailValue == null || emailValue.trim().isEmpty()) {
                        log.warn("Skipping row with null or empty email in import job {}", jobId);
                        continue;
                    }
                    String email = emailValue.trim().toLowerCase();

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
                    lead = leadRepository.save(lead);

                    // If campaignId is provided, create campaign-lead relationship
                    if (campaignId != null) {
                        campaignLeadService.addLeadToCampaign(campaignId, lead.getId(), null);
                    }

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
        String email = getValueFromRow(row, getEmailColumnNames());
        String domain = extractDomainFromEmail(email);

        // Use provided domain if available, otherwise extract from email
        String providedDomain = getValueFromRow(row, getDomainColumnNames());
        if (providedDomain != null && !providedDomain.trim().isEmpty()) {
            domain = providedDomain.trim().toLowerCase();
        }

        return Lead.builder()
                .orgId(orgId)
                .firstName(trimValue(getValueFromRow(row, getFirstNameColumnNames())))
                .lastName(trimValue(getValueFromRow(row, getLastNameColumnNames())))
                .company(trimValue(getValueFromRow(row, getCompanyColumnNames())))
                .domain(domain)
                .title(trimValue(getValueFromRow(row, getTitleColumnNames())))
                .email(email)
                .phone(trimValue(getValueFromRow(row, getPhoneColumnNames())))
                .linkedinUrl(trimValue(getValueFromRow(row, getLinkedInColumnNames())))
                .country(trimValue(getValueFromRow(row, getCountryColumnNames())))
                .state(trimValue(getValueFromRow(row, getStateColumnNames())))
                .city(trimValue(getValueFromRow(row, getCityColumnNames())))
                .customTextField(trimValue(getValueFromRow(row, getCustomTextFieldColumnNames())))
                .source("csv_import")
                .verifiedStatus(Lead.VerifiedStatus.unknown)
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

    public List<ImportJob> getImportHistoryByUserId(Long userId) {
        return importJobRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public ImportJob getImportJob(UUID jobId) {
        return importJobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Import job not found"));
    }

    // Helper methods for column name variations (with and without BOM)
    private boolean hasAnyColumn(Map<String, Integer> headerMap, String[] columnNames) {
        for (String columnName : columnNames) {
            if (headerMap.containsKey(columnName)) {
                return true;
            }
        }
        return false;
    }

    private String[] getEmailColumnNames() {
        return new String[] {
                "email", "?email",
                "e-mail", "?e-mail",
                "email address", "?email address",
                "Email address", "?Email address",
                "mail", "?mail",
                "email_address", "?email_address",
                "e_mail", "?e_mail",
                "emailaddress", "?emailaddress",
                "email-address", "?email-address",
                "e-mail address", "?e-mail address",
                "email addr", "?email addr",
                "mail address", "?mail address"
        };
    }

    private String[] getFirstNameColumnNames() {
        return new String[] {
                "first_name", "?first_name",
                "firstname", "?firstname",
                "first name", "?first name",
                "fname", "?fname",
                "given_name", "?given_name",
                "givenname", "?givenname",
                "given name", "?given name",
                "name", "?name"
        };
    }

    private String[] getLastNameColumnNames() {
        return new String[] {
                "last_name", "?last_name",
                "lastname", "?lastname",
                "last name", "?last name",
                "lname", "?lname",
                "surname", "?surname",
                "family_name", "?family_name",
                "family name", "?family name"
        };
    }

    private String[] getCompanyColumnNames() {
        return new String[] {
                "company", "?company",
                "company name", "?company name",
                "organization", "?organization",
                "org", "?org",
                "company_name", "?company_name",
                "organization_name", "?organization_name"
        };
    }

    private String[] getTitleColumnNames() {
        return new String[] {
                "title", "?title",
                "job title", "?job title",
                "position", "?position",
                "role", "?role",
                "job_title", "?job_title",
                "job_position", "?job_position",
                "job_role", "?job_role"
        };
    }

    private String[] getPhoneColumnNames() {
        return new String[] {
                "phone", "?phone",
                "phone number", "?phone number",
                "mobile", "?mobile",
                "telephone", "?telephone",
                "phone_number", "?phone_number",
                "mobile_number", "?mobile_number",
                "tel", "?tel"
        };
    }

    private String[] getLinkedInColumnNames() {
        return new String[] {
                "linkedin_url", "?linkedin_url",
                "linkedin", "?linkedin",
                "linkedin profile", "?linkedin profile",
                "linkedin_profile", "?linkedin_profile",
                "linkedinurl", "?linkedinurl"
        };
    }

    private String[] getCountryColumnNames() {
        return new String[] {
                "country", "?country",
                "nation", "?nation",
                "country_name", "?country_name",
                "countryname", "?countryname"
        };
    }

    private String[] getStateColumnNames() {
        return new String[] {
                "state", "?state",
                "province", "?province",
                "region", "?region",
                "state_name", "?state_name",
                "province_name", "?province_name"
        };
    }

    private String[] getCityColumnNames() {
        return new String[] {
                "city", "?city",
                "town", "?town",
                "city_name", "?city_name",
                "town_name", "?town_name"
        };
    }

    private String[] getDomainColumnNames() {
        return new String[] {
                "domain", "?domain",
                "website", "?website",
                "company domain", "?company domain",
                "company_domain", "?company_domain",
                "domain_name", "?domain_name"
        };
    }

    private String[] getCustomTextFieldColumnNames() {
        return new String[] {
                "custom_text_field", "?custom_text_field",
                "notes", "?notes",
                "comments", "?comments",
                "custom", "?custom",
                "custom_field", "?custom_field",
                "custom_text", "?custom_text"
        };
    }

    // Helper methods for column mapping
    private String autoDetectFieldType(String normalizedHeader) {
        // Email detection
        if (normalizedHeader.contains("email") || normalizedHeader.contains("mail")) {
            return "email";
        }
        // First name detection
        if (normalizedHeader.contains("first") || normalizedHeader.contains("given") ||
                normalizedHeader.equals("name") || normalizedHeader.contains("fname")) {
            return "first_name";
        }
        // Last name detection
        if (normalizedHeader.contains("last") || normalizedHeader.contains("surname") ||
                normalizedHeader.contains("family") || normalizedHeader.contains("lname")) {
            return "last_name";
        }
        // Company detection
        if (normalizedHeader.contains("company") || normalizedHeader.contains("organization") ||
                normalizedHeader.contains("org") || normalizedHeader.contains("employer")) {
            return "company";
        }
        // Title detection
        if (normalizedHeader.contains("title") || normalizedHeader.contains("position") ||
                normalizedHeader.contains("role") || normalizedHeader.contains("job")) {
            return "title";
        }
        // Phone detection
        if (normalizedHeader.contains("phone") || normalizedHeader.contains("mobile") ||
                normalizedHeader.contains("telephone") || normalizedHeader.contains("tel")) {
            return "phone";
        }
        // No match found - only detect fields that are available in the dropdown
        return null;
    }

    private boolean isRequiredField(String fieldType) {
        return "email".equals(fieldType) || "first_name".equals(fieldType);
    }

    private List<Map<String, String>> parseCsvWithMapping(MultipartFile file, Map<String, String> columnMapping) {
        List<Map<String, String>> mappedData = new ArrayList<>();

        try {
            // Read the file content into a byte array first to avoid stream consumption
            // issues
            byte[] fileBytes = file.getBytes();

            try (CSVReader reader = new CSVReader(
                    new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {
                List<String[]> rows = reader.readAll();

                if (rows.isEmpty()) {
                    return mappedData;
                }

                String[] headers = rows.get(0);

                // Process data rows
                for (int i = 1; i < rows.size(); i++) {
                    String[] row = rows.get(i);
                    Map<String, String> mappedRow = new HashMap<>();

                    for (int j = 0; j < headers.length && j < row.length; j++) {
                        String originalColumn = headers[j];
                        String fieldName = columnMapping.get(originalColumn);
                        String value = row[j] != null ? row[j].trim() : "";

                        if (fieldName != null && !fieldName.equals("skip") && !value.isEmpty()) {
                            mappedRow.put(fieldName, value);
                        }
                    }

                    if (!mappedRow.isEmpty()) {
                        mappedData.add(mappedRow);
                    }
                }
            }

        } catch (IOException | CsvException e) {
            log.error("Error parsing CSV with mapping", e);
            throw new RuntimeException("Error parsing CSV file", e);
        }

        return mappedData;
    }

    private Lead buildLeadFromMappedRow(Map<String, String> mappedRow, UUID orgId, Map<String, String> columnMapping) {
        // Get standard fields
        String email = mappedRow.get("email");
        String domain = extractDomainFromEmail(email);

        // Use provided domain if available
        String providedDomain = mappedRow.get("domain");
        if (providedDomain != null && !providedDomain.trim().isEmpty()) {
            domain = providedDomain.trim().toLowerCase();
        }

        // Build custom fields JSON for extra columns
        Map<String, Object> customFields = new HashMap<>();
        for (Map.Entry<String, String> entry : mappedRow.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            // Skip standard fields - they go to their own columns
            if (isStandardField(fieldName)) {
                continue;
            }

            // Add to custom fields
            customFields.put(fieldName, value);
        }

        // Create enriched JSON with custom fields
        String enrichedJson = "{}";
        if (!customFields.isEmpty()) {
            try {
                Map<String, Object> enrichedData = new HashMap<>();
                enrichedData.put("custom_csv_fields", customFields);
                enrichedJson = objectMapper.writeValueAsString(enrichedData);
            } catch (Exception e) {
                log.warn("Error creating enriched JSON for custom fields", e);
            }
        }

        return Lead.builder()
                .orgId(orgId)
                .firstName(trimValue(mappedRow.get("first_name")))
                .lastName(trimValue(mappedRow.get("last_name")))
                .company(trimValue(mappedRow.get("company")))
                .domain(domain)
                .title(trimValue(mappedRow.get("title")))
                .email(email)
                .phone(trimValue(mappedRow.get("phone")))
                .linkedinUrl(trimValue(mappedRow.get("linkedin_url")))
                .country(trimValue(mappedRow.get("country")))
                .state(trimValue(mappedRow.get("state")))
                .city(trimValue(mappedRow.get("city")))
                .customTextField(trimValue(mappedRow.get("custom_text_field")))
                .source("csv_import")
                .verifiedStatus(Lead.VerifiedStatus.unknown)
                .enrichedJson(enrichedJson)
                .build();
    }

    private boolean isStandardField(String fieldName) {
        return fieldName.equals("email") ||
                fieldName.equals("first_name") ||
                fieldName.equals("last_name") ||
                fieldName.equals("company") ||
                fieldName.equals("domain") ||
                fieldName.equals("title") ||
                fieldName.equals("phone") ||
                fieldName.equals("linkedin_url") ||
                fieldName.equals("country") ||
                fieldName.equals("state") ||
                fieldName.equals("city") ||
                fieldName.equals("custom_text_field");
    }

    private List<CsvColumnMappingDto.FieldOption> getAvailableFieldOptions() {
        return List.of(
                // Required fields
                CsvColumnMappingDto.FieldOption.builder()
                        .value("email")
                        .label("Email")
                        .description("Email address")
                        .isRequired(true)
                        .category("required")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("first_name")
                        .label("First Name")
                        .description("First name")
                        .isRequired(true)
                        .category("required")
                        .build(),

                // Essential optional fields
                CsvColumnMappingDto.FieldOption.builder()
                        .value("last_name")
                        .label("Last Name")
                        .description("Last name")
                        .isRequired(false)
                        .category("optional")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("company")
                        .label("Company")
                        .description("Company name")
                        .isRequired(false)
                        .category("optional")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("title")
                        .label("Job Title")
                        .description("Job title")
                        .isRequired(false)
                        .category("optional")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("phone")
                        .label("Phone")
                        .description("Phone number")
                        .isRequired(false)
                        .category("optional")
                        .build(),

                // Special options
                CsvColumnMappingDto.FieldOption.builder()
                        .value("custom_field")
                        .label("Custom Field")
                        .description("Store as custom data")
                        .isRequired(false)
                        .category("custom")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("skip")
                        .label("Skip")
                        .description("Ignore this column")
                        .isRequired(false)
                        .category("skip")
                        .build());
    }

}
