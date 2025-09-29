package com.outreachly.outreachly.service.email;

import com.outreachly.outreachly.dto.EmailRequest;
import com.outreachly.outreachly.dto.EmailResponse;
import com.outreachly.outreachly.entity.EmailEvent;

import java.util.List;

/**
 * Interface for email providers to ensure consistent email sending across
 * different services.
 * This abstraction allows easy switching between email providers (SES, Resend,
 * Mailgun, etc.)
 */
public interface EmailProvider {

    /**
     * Send a single email
     * 
     * @param emailRequest The email request containing recipient, subject, content,
     *                     etc.
     * @return EmailResponse with success status, message ID, and recipient counts
     */
    EmailResponse sendEmail(EmailRequest emailRequest);

    /**
     * Send multiple emails in bulk
     * 
     * @param emailRequests List of email requests to send
     * @return EmailResponse with aggregated results
     */
    EmailResponse sendBulkEmail(List<EmailRequest> emailRequests);

    /**
     * Verify an email address (if supported by provider)
     * 
     * @param emailAddress The email address to verify
     * @return true if verification request was successful
     */
    boolean verifyEmailAddress(String emailAddress);

    /**
     * Get list of verified email addresses
     * 
     * @return List of verified email addresses
     */
    List<String> getVerifiedEmailAddresses();

    /**
     * Check if an email address is suppressed (bounced/complained)
     * 
     * @param emailAddress The email address to check
     * @return true if the email is suppressed
     */
    boolean isEmailSuppressed(String emailAddress);

    /**
     * Get email history for a specific email address
     * 
     * @param emailAddress The email address to get history for
     * @return List of email events
     */
    List<EmailEvent> getEmailHistory(String emailAddress);

    /**
     * Get the provider type
     * 
     * @return The provider type enum
     */
    EmailProviderType getProviderType();

    /**
     * Check if the provider is healthy and ready to send emails
     * 
     * @return true if the provider is healthy
     */
    boolean isHealthy();

    /**
     * Get provider-specific configuration info
     * 
     * @return Provider configuration details
     */
    String getProviderInfo();
}
