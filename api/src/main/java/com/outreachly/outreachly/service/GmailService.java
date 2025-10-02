package com.outreachly.outreachly.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;

@Service
@Slf4j
public class GmailService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public GmailService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    /**
     * Send email using Gmail API with OAuth2 token
     */
    public void sendEmail(String to, String subject, String body, boolean isHtml, String fromEmail)
            throws IOException, MessagingException {
        try {
            // Get OAuth2 access token from current authentication
            OAuth2AccessToken accessToken = getCurrentAccessToken();
            if (accessToken == null) {
                throw new IllegalStateException("No valid OAuth2 access token found");
            }

            // Create Gmail service with OAuth2 credentials
            Gmail service = createGmailService(accessToken.getTokenValue());

            // Create email message
            Message message = createEmailMessage(to, subject, body, isHtml, fromEmail);

            // Send email
            service.users().messages().send("me", message).execute();

            log.info("Email sent successfully to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Get current OAuth2 access token from Spring Security context
     */
    private OAuth2AccessToken getCurrentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2AuthorizedClient authorizedClient = authorizedClientService
                    .loadAuthorizedClient(oauth2Token.getAuthorizedClientRegistrationId(), oauth2Token.getName());

            if (authorizedClient != null) {
                return authorizedClient.getAccessToken();
            }
        }

        return null;
    }

    /**
     * Create Gmail service instance with OAuth2 token
     */
    private Gmail createGmailService(String accessToken) throws IOException, GeneralSecurityException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Create credential with access token
        Credential credential = new Credential.Builder(
                com.google.api.client.auth.oauth2.BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .build();

        credential.setAccessToken(accessToken);

        return new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("Outreachly")
                .build();
    }

    /**
     * Create email message for Gmail API
     */
    private Message createEmailMessage(String to, String subject, String body, boolean isHtml, String fromEmail)
            throws MessagingException, IOException {
        MimeMessage email = new MimeMessage(Session.getDefaultInstance(System.getProperties()));

        // Set from address - use provided email or default to "me" for Gmail API
        if (fromEmail != null && !fromEmail.trim().isEmpty()) {
            email.setFrom(new InternetAddress(fromEmail));
        } else {
            email.setFrom(new InternetAddress("me"));
        }

        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);

        // Set content based on HTML flag
        if (isHtml) {
            email.setContent(body, "text/html; charset=utf-8");
        } else {
            email.setText(body);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = java.util.Base64.getUrlEncoder().encodeToString(bytes);

        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    /**
     * Send HTML email using Gmail API
     */
    public void sendHtmlEmail(String to, String subject, String htmlBody) throws IOException, MessagingException {
        try {
            OAuth2AccessToken accessToken = getCurrentAccessToken();
            if (accessToken == null) {
                throw new IllegalStateException("No valid OAuth2 access token found");
            }

            Gmail service = createGmailService(accessToken.getTokenValue());
            Message message = createHtmlEmailMessage(to, subject, htmlBody);

            service.users().messages().send("me", message).execute();

            log.info("HTML email sent successfully to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send HTML email to: {}", to, e);
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }

    /**
     * Create HTML email message for Gmail API
     */
    private Message createHtmlEmailMessage(String to, String subject, String htmlBody)
            throws MessagingException, IOException {
        MimeMessage email = new MimeMessage(Session.getDefaultInstance(System.getProperties()));
        email.setFrom(new InternetAddress("me"));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);
        email.setContent(htmlBody, "text/html; charset=utf-8");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = java.util.Base64.getUrlEncoder().encodeToString(bytes);

        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    /**
     * Check if the current user has Gmail API access by actually testing the API
     */
    public boolean hasGmailAccess() {
        try {
            OAuth2AccessToken accessToken = getCurrentAccessToken();
            if (accessToken == null) {
                return false;
            }

            // Check if token is expired by comparing with current time
            Instant now = Instant.now();
            Instant expiresAt = accessToken.getExpiresAt();
            if (expiresAt != null && now.isAfter(expiresAt)) {
                return false;
            }

            // Actually test Gmail API access by trying to create the service
            try {
                createGmailService(accessToken.getTokenValue());
                return true;
            } catch (Exception e) {
                log.warn("Gmail API access test failed: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to check Gmail API access", e);
            return false;
        }
    }
}
