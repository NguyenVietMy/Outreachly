package com.outreachly.outreachly.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outreachly.outreachly.entity.Template;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.TemplateService;
import com.outreachly.outreachly.service.CsvImportService;
import com.outreachly.outreachly.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Slf4j
public class TemplateController {

    private final TemplateService templateService;
    private final UserService userService;
    private final CsvImportService csvImportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> ALLOWED_VARS = Set.of("first_name", "last_name", "company", "title");

    @GetMapping
    public ResponseEntity<List<Template>> listTemplates(
            @RequestParam(value = "platform", required = false) String platform,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        Template.Platform pf = null;
        if (platform != null) {
            try {
                pf = Template.Platform.valueOf(platform.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(templateService.listTemplates(getOrgIdOrForbidden(user), pf));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Template> getTemplate(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();
        Template t = templateService.getTemplate(getOrgIdOrForbidden(user), id);
        return t != null ? ResponseEntity.ok(t) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();

        try {
            String name = (String) body.get("name");
            String platform = (String) body.get("platform");
            String category = (String) body.getOrDefault("category", null);
            Object contentObj = body.get("content");

            if (name == null || name.isBlank() || platform == null || contentObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "name, platform, and content are required"));
            }

            Template.Platform pf = Template.Platform.valueOf(platform.toUpperCase());

            String contentJson = objectMapper.writeValueAsString(contentObj);

            // Validate required vars for EMAIL and LINKEDIN bodies (plain text)
            List<String> missing = validateRequiredVariables(pf, contentJson);
            if (!missing.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing required variables",
                        "missing", missing));
            }

            Template created = templateService.createTemplate(getOrgIdOrForbidden(user), name, pf, category,
                    contentJson);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid platform"));
        } catch (Exception ex) {
            log.error("Template create failed", ex);
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();
        try {
            String name = (String) body.getOrDefault("name", null);
            String category = (String) body.getOrDefault("category", null);
            String platform = (String) body.getOrDefault("platform", null);
            Object contentObj = body.getOrDefault("content", null);

            String contentJson = null;
            if (contentObj != null) {
                contentJson = objectMapper.writeValueAsString(contentObj);

                Template existing = templateService.getTemplate(getOrgIdOrForbidden(user), id);
                if (existing == null)
                    return ResponseEntity.notFound().build();

                // Validate required vars
                List<String> missing = validateRequiredVariables(existing.getPlatform(), contentJson);
                if (!missing.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Missing required variables",
                            "missing", missing));
                }
            }

            Template.Platform pf = null;
            if (platform != null) {
                try {
                    pf = Template.Platform.valueOf(platform.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid platform"));
                }
            }

            Template updated = templateService.updateTemplate(getOrgIdOrForbidden(user), id, name, category,
                    contentJson, pf);
            if (updated == null)
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok(updated);
        } catch (Exception ex) {
            log.error("Template update failed", ex);
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id, Authentication authentication) {
        User user = getUser(authentication);
        if (user == null)
            return ResponseEntity.status(401).build();
        boolean ok = templateService.deleteTemplate(getOrgIdOrForbidden(user), id);
        return ok ? ResponseEntity.ok(Map.of("deleted", true)) : ResponseEntity.notFound().build();
    }

    // --- Helpers ---
    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return null;
        return userService.findByEmail(authentication.getName());
    }

    private java.util.UUID getOrgIdOrForbidden(User user) {
        if (user.getOrgId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Organization required");
        }
        return user.getOrgId();
    }

    private List<String> validateRequiredVariables(Template.Platform platform, String contentJson) {
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            String subject = node.has("subject") ? node.get("subject").asText("") : "";
            String body = node.has("body") ? node.get("body").asText("") : "";

            String combined = (subject + "\n" + body);
            Pattern pattern = Pattern.compile("\\{\\{(.*?)}}");
            Matcher matcher = pattern.matcher(combined);
            List<String> used = new ArrayList<>();
            while (matcher.find()) {
                used.add(matcher.group(1).trim());
            }

            // For now require that if variables are referenced, they are within allowed set
            List<String> invalid = used.stream()
                    .filter(v -> !ALLOWED_VARS.contains(v))
                    .distinct()
                    .collect(Collectors.toList());

            return invalid; // treat invalid variables as "missing/invalid" for the client
        } catch (Exception e) {
            log.warn("validateRequiredVariables parse error", e);
            return List.of();
        }
    }
}
