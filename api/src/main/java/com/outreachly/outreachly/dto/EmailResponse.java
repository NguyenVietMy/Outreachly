package com.outreachly.outreachly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {
    private String messageId;
    private boolean success;
    private String message;
    private LocalDateTime timestamp;
    private List<String> failedRecipients;
    private int totalRecipients;
    private int successfulRecipients;
}
