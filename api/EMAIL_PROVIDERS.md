# Email Provider Abstraction

This document explains how to use and configure the email provider abstraction layer in Outreachly.

## Overview

The email provider abstraction allows you to easily switch between different email services (AWS SES, Resend, Mailgun, etc.) without changing your application code. All providers implement the same interface, making switching seamless.

## Available Providers

### 1. AWS SES (Default)

- **Type**: `aws-ses`
- **Configuration**: Uses existing AWS SES setup
- **Features**: Full SES functionality including verification, suppression checking

### 2. Resend

- **Type**: `resend`
- **Configuration**: Requires `RESEND_API_KEY` and `RESEND_FROM_EMAIL`
- **Features**: Simple API, no verification required

### 3. Mock Provider

- **Type**: `mock`
- **Configuration**: No configuration needed
- **Features**: Simulates email sending for testing (5% failure rate)

## Configuration

### 1. Set Provider Type

In `application.properties`:

```properties
# Use AWS SES (default)
email.provider=aws-ses

# Or use Resend
email.provider=resend

# Or use Mock for testing
email.provider=mock
```

### 2. Provider-Specific Configuration

#### AWS SES

```properties
aws.ses.region=us-east-1
aws.ses.from-email=noreply@outreach-ly.com
aws.ses.from-name=Outreachly
```

#### Resend

```properties
resend.api-key=${RESEND_API_KEY}
resend.from-email=${RESEND_FROM_EMAIL}
```

## Usage

### 1. Using the Unified Service (Recommended)

```java
@Autowired
private UnifiedEmailService emailService;

// Send email with configured provider
EmailResponse response = emailService.sendEmail(emailRequest);

// Send email with specific provider
EmailResponse response = emailService.sendEmail(emailRequest, EmailProviderType.RESEND);
```

### 2. Using Provider Factory

```java
@Autowired
private EmailProviderFactory providerFactory;

// Get configured provider
EmailProvider provider = providerFactory.getConfiguredProvider();

// Get specific provider
EmailProvider provider = providerFactory.getProvider(EmailProviderType.RESEND);
```

### 3. API Endpoints

#### Send Email (Configured Provider)

```http
POST /api/email/send
Content-Type: application/json

{
  "subject": "Test Email",
  "content": "Hello World",
  "recipients": ["test@example.com"],
  "isHtml": true
}
```

#### Send Email (Specific Provider)

```http
POST /api/email/send/resend
Content-Type: application/json

{
  "subject": "Test Email",
  "content": "Hello World",
  "recipients": ["test@example.com"],
  "isHtml": true
}
```

#### Get Provider Information

```http
GET /api/email/providers
```

Response:

```json
{
  "currentProvider": "AWS SES",
  "availableProviders": {
    "AWS_SES": "AWS SES (Amazon Simple Email Service) - Healthy: true",
    "RESEND": "Resend (Resend Email API) - From: test@example.com, Healthy: true",
    "MOCK": "Mock Provider (Mock Email Provider for Testing) - Always healthy, simulates 5% failure rate"
  },
  "healthStatus": {
    "AWS_SES": true,
    "RESEND": true,
    "MOCK": true
  }
}
```

## Adding New Providers

### 1. Create Provider Class

```java
@Service
@Slf4j
public class NewEmailProvider extends AbstractEmailProvider {

    private final EmailEventService emailEventService;

    public NewEmailProvider(EmailEventService emailEventService) {
        super(emailEventService);
        this.emailEventService = emailEventService;
    }

    @Override
    public EmailProviderType getProviderType() {
        return EmailProviderType.NEW_PROVIDER;
    }

    @Override
    protected EmailResponse doSendEmail(EmailRequest emailRequest) {
        // Implement provider-specific email sending logic
        return EmailResponse.builder()
                .messageId("unique-id")
                .success(true)
                .message("Email sent successfully")
                .timestamp(LocalDateTime.now())
                .totalRecipients(emailRequest.getRecipients().size())
                .successfulRecipients(emailRequest.getRecipients().size())
                .failedRecipients(new ArrayList<>())
                .build();
    }

    // Implement other required methods...
}
```

### 2. Add Provider Type

```java
public enum EmailProviderType {
    // ... existing types
    NEW_PROVIDER("New Provider", "New Email Service");
}
```

### 3. Update Configuration

The provider will be automatically registered through Spring's component scanning.

## Benefits

1. **Easy Switching**: Change providers with a single configuration property
2. **Risk Mitigation**: If one provider fails, switch to another instantly
3. **A/B Testing**: Test different providers with the same codebase
4. **Gradual Migration**: Move from one provider to another over time
5. **Development**: Use mock provider for testing without sending real emails

## Health Monitoring

All providers include health checking:

- `isHealthy()`: Basic health check
- `getProviderInfo()`: Detailed provider information
- `/api/email/providers`: API endpoint for monitoring

## Error Handling

The abstraction layer handles:

- Provider-specific errors
- Network timeouts
- Rate limiting
- Email suppression
- Fallback mechanisms

All errors are logged and returned in a consistent `EmailResponse` format.
