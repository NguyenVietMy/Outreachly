package com.outreachly.outreachly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvColumnMappingDto {

    private List<CsvColumn> detectedColumns;
    private List<FieldOption> availableFields;
    private Map<String, String> mapping; // columnName -> fieldName
    private boolean hasRequiredFields;
    private List<String> missingRequiredFields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsvColumn {
        private String name;
        private String displayName;
        private String sampleValue;
        private boolean isRequired;
        private String currentMapping; // null if not mapped
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldOption {
        private String value;
        private String label;
        private String description;
        private boolean isRequired;
        private String category; // "required", "optional", "custom"
    }
}
