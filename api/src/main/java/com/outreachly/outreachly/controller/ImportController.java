package com.outreachly.outreachly.controller;

import com.outreachly.outreachly.entity.ImportJob;
import com.outreachly.outreachly.entity.User;
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

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processCsvImport(
            @RequestParam("file") MultipartFile file,
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

            // Create import job with actual user ID and org ID
            UUID orgId = user.getOrgId() != null ? user.getOrgId() : csvImportService.getOrCreateDefaultOrganization();
            ImportJob importJob = csvImportService.createImportJob(
                    user.getId(),
                    orgId,
                    file.getOriginalFilename(),
                    validationResult.getData().size());

            // Process import asynchronously
            csvImportService.processImportJob(importJob.getId(), validationResult.getData());

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
