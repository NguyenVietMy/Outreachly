package com.pulse.pulse;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ArchitectureRulesTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/pulse/pulse");
    private static final Set<String> MODULES = Set.of(
            "identity", "organizations", "personal", "integrations", "activity", "platform");

    @Test
    void sourceDoesNotUseLegacyLayerPackages() throws IOException {
        List<Path> offenders = Files.walk(SOURCE_ROOT)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(this::usesLegacyLayerPackage)
                .collect(Collectors.toList());

        assertFalse(!offenders.isEmpty(), "Legacy layer packages still present: " + offenders);
    }

    @Test
    void apiAndApplicationLayersDoNotImportOtherModuleInfrastructure() throws IOException {
        List<Path> offenders = Files.walk(SOURCE_ROOT)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(this::crossesInfrastructureBoundary)
                .collect(Collectors.toList());

        assertFalse(!offenders.isEmpty(), "Cross-module infrastructure imports found: " + offenders);
    }

    private boolean usesLegacyLayerPackage(Path path) {
        String source = read(path);
        return source.contains("package com.pulse.pulse.controller;")
                || source.contains("package com.pulse.pulse.service;")
                || source.contains("package com.pulse.pulse.repository;")
                || source.contains("package com.pulse.pulse.entity;")
                || source.contains("package com.pulse.pulse.dto;")
                || source.contains("package com.pulse.pulse.config;")
                || source.contains("package com.pulse.pulse.security;");
    }

    private boolean crossesInfrastructureBoundary(Path path) {
        String source = read(path);
        String packageName = extractPackageName(source);
        if (packageName == null) {
            return false;
        }

        String ownerModule = extractModule(packageName);
        if (ownerModule == null) {
            return false;
        }

        boolean isPublicLayer = packageName.contains(".api") || packageName.contains(".application");
        if (!isPublicLayer) {
            return false;
        }

        Matcher matcher = Pattern.compile("import\\s+([\\w.]+);").matcher(source);
        while (matcher.find()) {
            String imported = matcher.group(1);
            String importedModule = extractModule(imported);
            if (importedModule == null || importedModule.equals(ownerModule)) {
                continue;
            }
            if (imported.contains(".infrastructure.")) {
                return true;
            }
        }

        return false;
    }

    private String extractPackageName(String source) {
        Matcher matcher = Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractModule(String packageName) {
        for (String module : MODULES) {
            if (packageName.startsWith("com.pulse.pulse." + module + ".")) {
                return module;
            }
        }
        return null;
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
    }
}
