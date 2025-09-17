package com.outreachly.outreachly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.outreachly.outreachly.dto.CsvColumnMappingDto;
import com.outreachly.outreachly.entity.ImportJob;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.entity.Campaign;
import com.outreachly.outreachly.repository.CampaignRepository;
import com.outreachly.outreachly.service.CsvImportService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final CsvImportService csvImportService;
    private final UserService userService;
    private final CampaignRepository campaignRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/detect-columns")
    public ResponseEntity<CsvColumnMappingDto> detectCsvColumns(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        try {
            // Validate file size (25MB limit)
            if (file.getSize() > 25 * 1024 * 1024) {
                return ResponseEntity.badRequest().build();
            }

            CsvColumnMappingDto result = csvImportService.detectCsvColumns(file);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error detecting CSV columns", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCsvFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        try {
            // Get user ID from authentication
            // String userEmail = authentication.getName();
            // UUID userId = getUserIdFromEmail(userEmail); // You'll need to implement this

            // Validate file size (25MB limit)
            if (file.getSize() > 25 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File size exceeds 25MB limit"));
            }

            CsvImportService.CsvValidationResult result = csvImportService.validateCsvFile(file);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", result.isValid());
            response.put("errors", result.getErrors());
            response.put("data", result.getData());
            response.put("totalRows", result.getData().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error validating CSV file", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error validating CSV file: " + e.getMessage()));
        }
    }

    @PostMapping("/process-with-mapping")
    public ResponseEntity<Map<String, Object>> processCsvImportWithMapping(
            @RequestParam("file") MultipartFile file,
            @RequestParam("columnMapping") String columnMappingJson,
            @RequestParam(value = "campaignId", required = false) String campaignId,
            Authentication authentication) {

        try {
            // Get user ID from authentication
            String userEmail = authentication.getName();
            User user = userService.findByEmail(userEmail);

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User not found"));
            }

            // Parse column mapping JSON
            Map<String, String> columnMapping;
            try {
                columnMapping = objectMapper.readValue(columnMappingJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid column mapping format"));
            }

            // Validate campaign if provided
            UUID campaignUuid = null;
            if (campaignId != null && !campaignId.trim().isEmpty() && !campaignId.equals("default")) {
                try {
                    campaignUuid = UUID.fromString(campaignId);
                    UUID orgId = user.getOrgId() != null ? user.getOrgId()
                            : csvImportService.getOrCreateDefaultOrganization();

                    // Validate campaign exists and belongs to user's organization
                    Campaign campaign = campaignRepository.findByIdAndOrgId(campaignUuid, orgId)
                            .orElseThrow(() -> new IllegalArgumentException("Campaign not found or access denied"));

                    log.info("Importing leads to campaign: {} for user: {}", campaign.getName(), user.getEmail());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid campaign ID or access denied"));
                }
            }

            // Parse CSV with mapping synchronously to avoid file access issues in async
            // method
            List<Map<String, String>> mappedData = csvImportService.parseCsvWithMapping(file, columnMapping);

            // Create import job
            UUID orgId = user.getOrgId() != null ? user.getOrgId() : csvImportService.getOrCreateDefaultOrganization();
            ImportJob importJob = csvImportService.createImportJob(
                    user.getId(),
                    orgId,
                    file.getOriginalFilename(),
                    mappedData.size());

            // Process import asynchronously with parsed data
            csvImportService.processImportJob(importJob.getId(), mappedData, campaignUuid);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", importJob.getId());
            response.put("status", importJob.getStatus());
            response.put("message", "Import job created successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing CSV import with mapping", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error processing CSV import: " + e.getMessage()));
        }
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processCsvImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "campaignId", required = false) String campaignId,
            Authentication authentication) {

        try {
            // Get user ID from authentication
            String userEmail = authentication.getName();
            User user = userService.findByEmail(userEmail);

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User not found"));
            }

            // Validate file first
            CsvImportService.CsvValidationResult validationResult = csvImportService.validateCsvFile(file);

            if (!validationResult.isValid()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "CSV validation failed", "errors", validationResult.getErrors()));
            }

            // Validate campaign if provided
            UUID campaignUuid = null;
            if (campaignId != null && !campaignId.trim().isEmpty() && !campaignId.equals("default")) {
                try {
                    campaignUuid = UUID.fromString(campaignId);
                    UUID orgId = user.getOrgId() != null ? user.getOrgId()
                            : csvImportService.getOrCreateDefaultOrganization();

                    // Validate campaign exists and belongs to user's organization
                    Campaign campaign = campaignRepository.findByIdAndOrgId(campaignUuid, orgId)
                            .orElseThrow(() -> new IllegalArgumentException("Campaign not found or access denied"));

                    log.info("Importing leads to campaign: {} for user: {}", campaign.getName(), user.getEmail());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid campaign ID or access denied"));
                }
            }

            // Create import job with actual user ID and org ID
            UUID orgId = user.getOrgId() != null ? user.getOrgId() : csvImportService.getOrCreateDefaultOrganization();
            ImportJob importJob = csvImportService.createImportJob(
                    user.getId(),
                    orgId,
                    file.getOriginalFilename(),
                    validationResult.getData().size());

            // Process import asynchronously with campaign assignment
            csvImportService.processImportJob(importJob.getId(), validationResult.getData(), campaignUuid);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", importJob.getId());
            response.put("status", importJob.getStatus());
            response.put("message", "Import job created successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing CSV import", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error processing CSV import: " + e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<ImportJob>> getImportHistory(Authentication authentication) {
        try {
            // Get user ID from authentication
            String userEmail = authentication.getName();
            User user = userService.findByEmail(userEmail);

            if (user == null) {
                return ResponseEntity.badRequest().build();
            }

            List<ImportJob> jobs = csvImportService.getImportHistoryByUserId(user.getId());
            return ResponseEntity.ok(jobs);

        } catch (Exception e) {
            log.error("Error getting import history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ImportJob> getImportJob(@PathVariable UUID jobId, Authentication authentication) {
        try {
            // Get user ID from authentication
            String userEmail = authentication.getName();
            User user = userService.findByEmail(userEmail);

            if (user == null) {
                return ResponseEntity.badRequest().build();
            }

            ImportJob job = csvImportService.getImportJob(jobId);

            // Check if the job belongs to the current user
            if (job.getUserId().equals(user.getId())) {
                return ResponseEntity.ok(job);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Error getting import job", e);
            return ResponseEntity.notFound().build();
        }
    }

}
