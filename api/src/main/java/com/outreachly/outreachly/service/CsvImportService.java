package com.outreachly.outreachly.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.outreachly.outreachly.dto.CsvColumnMappingDto;
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
    private final OrgLeadService orgLeadService;
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

                    // Ensure global lead exists (create if missing)
                    leadRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
                        Lead newLead = Lead.builder()
                                .orgId(java.util.UUID.fromString("b8470f71-e5c8-4974-b6af-3d7af17aa55c"))
                                .firstName(trimValue(getValueFromRow(row, getFirstNameColumnNames())))
                                .lastName(trimValue(getValueFromRow(row, getLastNameColumnNames())))
                                .email(email)
                                .domain(extractDomainFromEmail(email))
                                .position(trimValue(getValueFromRow(row, getPositionColumnNames())))
                                .positionRaw(trimValue(getValueFromRow(row, getPositionRawColumnNames())))
                                .seniority(trimValue(getValueFromRow(row, getSeniorityColumnNames())))
                                .department(trimValue(getValueFromRow(row, getDepartmentColumnNames())))
                                .phone(trimValue(getValueFromRow(row, getPhoneColumnNames())))
                                .linkedinUrl(trimValue(getValueFromRow(row, getLinkedInColumnNames())))
                                .twitter(trimValue(getValueFromRow(row, getTwitterColumnNames())))
                                .confidenceScore(parseInteger(getValueFromRow(row, getConfidenceScoreColumnNames())))
                                .emailType(mapEmailType(getValueFromRow(row, getEmailTypeColumnNames())))
                                .source("csv_import")
                                .verifiedStatus(Lead.VerifiedStatus.unknown)
                                .enrichedJson("{}")
                                .build();
                        return leadRepository.save(newLead);
                    });

                    // Ensure org_leads mapping exists for org
                    orgLeadService.ensureOrgLeadForEmail(job.getOrgId(), email, "csv_import");

                    // If campaignId is provided, create campaign-lead relationship
                    // TODO: Implement campaign-lead relationship creation
                    if (campaignId != null) {
                        log.warn("Campaign ID provided but campaign-lead relationship not implemented yet");
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

    // Removed unused helper to avoid linter warning

    private String extractDomainFromEmail(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf("@") + 1).toLowerCase();
        }
        return null;
    }

    private String trimValue(String value) {
        return value != null ? value.trim() : null;
    }

    private Lead.EmailType mapEmailType(String emailType) {
        if (emailType == null)
            return Lead.EmailType.unknown;

        try {
            return Lead.EmailType.valueOf(emailType.toLowerCase());
        } catch (IllegalArgumentException e) {
            return Lead.EmailType.unknown;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Could not parse integer value: {}", value);
            return null;
        }
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

    private String[] getPositionColumnNames() {
        return new String[] {
                "position", "?position",
                "job position", "?job position",
                "role", "?role",
                "job_position", "?job_position",
                "job_role", "?job_role"
        };
    }

    private String[] getPositionRawColumnNames() {
        return new String[] {
                "position_raw", "?position_raw",
                "position raw", "?position raw",
                "raw_position", "?raw_position",
                "raw position", "?raw position"
        };
    }

    private String[] getSeniorityColumnNames() {
        return new String[] {
                "seniority", "?seniority",
                "level", "?level",
                "senior", "?senior",
                "junior", "?junior",
                "seniority_level", "?seniority_level"
        };
    }

    private String[] getDepartmentColumnNames() {
        return new String[] {
                "department", "?department",
                "dept", "?dept",
                "team", "?team",
                "division", "?division"
        };
    }

    private String[] getTwitterColumnNames() {
        return new String[] {
                "twitter", "?twitter",
                "x.com", "?x.com",
                "twitter_handle", "?twitter_handle",
                "twitter handle", "?twitter handle"
        };
    }

    private String[] getConfidenceScoreColumnNames() {
        return new String[] {
                "confidence_score", "?confidence_score",
                "confidence", "?confidence",
                "score", "?score",
                "confidence score", "?confidence score"
        };
    }

    private String[] getEmailTypeColumnNames() {
        return new String[] {
                "email_type", "?email_type",
                "email type", "?email type",
                "type", "?type"
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

    @SuppressWarnings("unused")
    private String[] getDomainColumnNames() {
        return new String[] {
                "domain", "?domain",
                "website", "?website",
                "company domain", "?company domain",
                "company_domain", "?company_domain",
                "domain_name", "?domain_name"
        };
    }

    @SuppressWarnings("unused")
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
        // EMAIL DETECTION
        if (normalizedHeader.contains("email") || normalizedHeader.contains("mail") ||
                normalizedHeader.contains("e_mail") || normalizedHeader.contains("emailaddress")) {
            return "email";
        }

        // PERSONAL INFO DETECTION
        if (normalizedHeader.contains("first") || normalizedHeader.contains("given") ||
                normalizedHeader.equals("name") || normalizedHeader.contains("fname")) {
            return "first_name";
        }
        if (normalizedHeader.contains("last") || normalizedHeader.contains("surname") ||
                normalizedHeader.contains("family") || normalizedHeader.contains("lname")) {
            return "last_name";
        }
        if (normalizedHeader.contains("phone") || normalizedHeader.contains("mobile") ||
                normalizedHeader.contains("telephone") || normalizedHeader.contains("tel") ||
                normalizedHeader.contains("contact_number")) {
            return "phone";
        }
        if (normalizedHeader.contains("position") || normalizedHeader.contains("job_position")) {
            return "position";
        }
        if (normalizedHeader.contains("position_raw") || normalizedHeader.contains("positionraw") ||
                normalizedHeader.contains("raw_position")) {
            return "position_raw";
        }
        if (normalizedHeader.contains("seniority") || normalizedHeader.contains("level") ||
                normalizedHeader.contains("senior") || normalizedHeader.contains("junior")) {
            return "seniority";
        }
        if (normalizedHeader.contains("department") || normalizedHeader.contains("dept") ||
                normalizedHeader.contains("team") || normalizedHeader.contains("division")) {
            return "department";
        }

        // COMPANY INFO DETECTION
        if (normalizedHeader.contains("domain") || normalizedHeader.contains("website") ||
                normalizedHeader.contains("company_domain") || normalizedHeader.contains("domain_name")) {
            return "domain";
        }

        // SOCIAL & PROFESSIONAL DETECTION
        if (normalizedHeader.contains("linkedin") || normalizedHeader.contains("linkedin_url") ||
                normalizedHeader.contains("linkedin_profile")) {
            return "linkedin_url";
        }
        if (normalizedHeader.contains("twitter") || normalizedHeader.contains("x.com")) {
            return "twitter";
        }

        // VERIFICATION DETECTION
        if (normalizedHeader.contains("confidence") || normalizedHeader.contains("score") ||
                normalizedHeader.contains("confidence_score")) {
            return "confidence_score";
        }
        if (normalizedHeader.contains("email_type") || normalizedHeader.contains("emailtype") ||
                normalizedHeader.contains("type")) {
            return "email_type";
        }

        // META DATA DETECTION
        if (normalizedHeader.contains("source") || normalizedHeader.contains("origin")) {
            return "source";
        }
        if (normalizedHeader.contains("custom") || normalizedHeader.contains("custom_field") ||
                normalizedHeader.contains("custom_text")) {
            return "custom_text_field";
        }

        // No match found - only detect fields that are available in the dropdown
        return null;
    }

    private boolean isRequiredField(String fieldType) {
        return "email".equals(fieldType) || "first_name".equals(fieldType);
    }

    public List<Map<String, String>> parseCsvWithMapping(MultipartFile file, Map<String, String> columnMapping) {
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

    private List<CsvColumnMappingDto.FieldOption> getAvailableFieldOptions() {
        return List.of(
                // REQUIRED FIELDS
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

                // PERSONAL INFO
                CsvColumnMappingDto.FieldOption.builder()
                        .value("last_name")
                        .label("Last Name")
                        .description("Last name")
                        .isRequired(false)
                        .category("personal")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("phone")
                        .label("Phone")
                        .description("Phone number")
                        .isRequired(false)
                        .category("personal")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("position")
                        .label("Position")
                        .description("Job position")
                        .isRequired(false)
                        .category("personal")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("position_raw")
                        .label("Position Raw")
                        .description("Raw position title")
                        .isRequired(false)
                        .category("personal")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("seniority")
                        .label("Seniority")
                        .description("Seniority level")
                        .isRequired(false)
                        .category("personal")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("department")
                        .label("Department")
                        .description("Department")
                        .isRequired(false)
                        .category("personal")
                        .build(),

                // COMPANY INFO
                CsvColumnMappingDto.FieldOption.builder()
                        .value("domain")
                        .label("Domain")
                        .description("Company domain")
                        .isRequired(false)
                        .category("company")
                        .build(),

                // SOCIAL & PROFESSIONAL
                CsvColumnMappingDto.FieldOption.builder()
                        .value("linkedin_url")
                        .label("LinkedIn URL")
                        .description("LinkedIn profile URL")
                        .isRequired(false)
                        .category("social")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("twitter")
                        .label("Twitter")
                        .description("Twitter handle/URL")
                        .isRequired(false)
                        .category("social")
                        .build(),

                // VERIFICATION
                CsvColumnMappingDto.FieldOption.builder()
                        .value("confidence_score")
                        .label("Confidence Score")
                        .description("Confidence score (0-100)")
                        .isRequired(false)
                        .category("verification")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("email_type")
                        .label("Email Type")
                        .description("Email type (personal, generic, role, etc.)")
                        .isRequired(false)
                        .category("verification")
                        .build(),

                // META DATA
                CsvColumnMappingDto.FieldOption.builder()
                        .value("source")
                        .label("Source")
                        .description("Lead source")
                        .isRequired(false)
                        .category("meta")
                        .build(),
                CsvColumnMappingDto.FieldOption.builder()
                        .value("custom_text_field")
                        .label("Custom Text Field")
                        .description("Custom text field")
                        .isRequired(false)
                        .category("meta")
                        .build(),

                // SPECIAL OPTIONS
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
