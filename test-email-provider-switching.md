# Email Provider Switching Test Guide

## Overview

This guide helps you test the email provider switching functionality that has been implemented.

## Backend Implementation Summary

### New Components Created:

1. **OrganizationSettings Entity** - Stores per-organization email provider preferences
2. **OrganizationSettingsRepository** - Database access for organization settings
3. **SettingsService** - Business logic for managing organization settings
4. **SettingsController** - REST API endpoints for settings management
5. **OrganizationEmailService** - Organization-specific email service wrapper
6. **DTOs** - Data transfer objects for API communication

### API Endpoints Added:

- `GET /api/settings` - Get organization settings
- `PUT /api/settings` - Update organization settings
- `GET /api/settings/email-providers` - Get available email providers
- `POST /api/settings/email-providers/{providerId}/switch` - Switch email provider
- `POST /api/settings/email-providers/{providerId}/test` - Test provider configuration
- `GET /api/settings/email-providers/status` - Get current provider status
- `PUT /api/settings/notifications` - Update notification settings
- `PUT /api/settings/feature-flags` - Update feature flags

## Frontend Implementation Summary

### Updated Components:

1. **Settings Page** - Enhanced with real API integration
2. **Email Provider Management** - Dynamic loading and switching
3. **Configuration Testing** - Test provider configurations
4. **Health Status Display** - Visual indicators for provider health

## Testing Steps

### 1. Backend Testing

#### Test Database Migration

```bash
# Ensure the organization_settings table exists
# Check if V21 migration was applied successfully
```

#### Test API Endpoints

```bash
# Test getting settings
curl -X GET http://localhost:8080/api/settings \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Test getting email providers
curl -X GET http://localhost:8080/api/settings/email-providers \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Test switching to Resend
curl -X POST http://localhost:8080/api/settings/email-providers/resend/switch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "apiKey": "your-resend-api-key",
    "fromEmail": "noreply@yourdomain.com",
    "fromName": "Your Company"
  }'

# Test switching to AWS SES
curl -X POST http://localhost:8080/api/settings/email-providers/aws-ses/switch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "apiKey": "your-aws-access-key",
    "fromEmail": "noreply@yourdomain.com",
    "fromName": "Your Company",
    "region": "us-east-1"
  }'
```

### 2. Frontend Testing

#### Access Settings Page

1. Navigate to `/settings` in your browser
2. Click on the "Email" tab
3. Verify that email providers are loaded dynamically

#### Test Provider Switching

1. Select a different email provider
2. Enter the required configuration (API key, from email, etc.)
3. Click "Test" to verify the configuration
4. Toggle the switch to activate the provider
5. Verify the provider status changes to "Active"

#### Test Configuration Validation

1. Enter invalid API credentials
2. Click "Test" - should show error message
3. Enter valid credentials
4. Click "Test" - should show success message

### 3. Integration Testing

#### Test Email Sending with Different Providers

1. Switch to Resend provider
2. Send a test email
3. Switch to AWS SES provider
4. Send another test email
5. Verify both emails are sent using the correct provider

#### Test Organization Isolation

1. Create multiple organizations
2. Set different email providers for each
3. Verify each organization uses its configured provider

## Configuration Requirements

### Resend Configuration

- API Key: Get from Resend dashboard
- From Email: Must be verified in Resend
- From Name: Display name for emails

### AWS SES Configuration

- API Key: AWS Access Key ID
- Secret Key: AWS Secret Access Key
- Region: AWS region (e.g., us-east-1)
- From Email: Must be verified in AWS SES
- From Name: Display name for emails

## Troubleshooting

### Common Issues

1. **Provider Not Loading**

   - Check if the provider is properly registered in EmailProviderConfig
   - Verify the provider implements EmailProvider interface

2. **Configuration Test Failing**

   - Verify API credentials are correct
   - Check if the provider service is healthy
   - Review logs for specific error messages

3. **Email Not Sending**

   - Verify the organization settings are saved correctly
   - Check if the provider is active and healthy
   - Review email provider logs

4. **Frontend Not Updating**
   - Check browser console for API errors
   - Verify CORS settings allow frontend requests
   - Check if authentication is working properly

### Debug Steps

1. **Check Backend Logs**

   ```bash
   # Look for settings-related log messages
   tail -f logs/application.log | grep -i "settings\|email.*provider"
   ```

2. **Check Database**

   ```sql
   -- Verify organization settings are saved
   SELECT * FROM organization_settings;

   -- Check if RLS policies are working
   SELECT * FROM organization_settings WHERE org_id = 'your-org-id';
   ```

3. **Check Frontend Network Tab**
   - Open browser dev tools
   - Go to Network tab
   - Check API requests and responses
   - Look for 401/403 errors (authentication issues)

## Expected Behavior

### Successful Implementation Should Show:

1. ✅ Email providers load dynamically from backend
2. ✅ Provider health status is displayed correctly
3. ✅ Configuration testing works for both providers
4. ✅ Provider switching updates the active provider
5. ✅ Email sending uses the correct provider
6. ✅ Settings persist across page refreshes
7. ✅ Error handling works for invalid configurations

### Performance Considerations:

- Settings are cached per organization
- Provider health checks are lightweight
- Configuration testing doesn't send actual emails
- Database queries use proper indexing

## Next Steps

After successful testing, consider:

1. Adding more email providers (Mailgun, SendGrid, etc.)
2. Implementing provider-specific rate limiting
3. Adding email delivery analytics per provider
4. Implementing provider failover mechanisms
5. Adding bulk configuration import/export

