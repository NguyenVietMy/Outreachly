package com.outreachly.outreachly.service.email;

/**
 * Enum representing different email provider types
 */
public enum EmailProviderType {
    AWS_SES("AWS SES", "Amazon Simple Email Service"),
    RESEND("Resend", "Resend Email API"),
    MAILGUN("Mailgun", "Mailgun Email API"),
    SENDGRID("SendGrid", "SendGrid Email API"),
    POSTMARK("Postmark", "Postmark Transactional Email"),
    MOCK("Mock", "Mock Email Provider for Testing");

    private final String displayName;
    private final String description;

    EmailProviderType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
