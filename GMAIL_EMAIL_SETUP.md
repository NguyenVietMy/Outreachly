# Gmail Email Integration Setup

## Overview

This document describes the Gmail API integration that allows users to send emails using their own Gmail accounts via OAuth2 authentication.

## What's Been Implemented

### Backend Changes

1. **Extended OAuth2 Configuration** (`application.properties`)

   - Added Gmail API scope: `https://www.googleapis.com/auth/gmail.send`
   - Updated Google OAuth2 client configuration

2. **New Dependencies** (`pom.xml`)

   - `google-api-services-gmail` - Gmail API client library
   - `google-auth-library-oauth2-http` - OAuth2 authentication
   - `jakarta.mail-api` & `angus-mail` - Email composition

3. **New Services & Controllers**
   - `GmailService.java` - Handles Gmail API operations
   - `GmailController.java` - REST endpoints for Gmail functionality
   - Updated `OAuth2Config.java` - Added OAuth2AuthorizedClientService

### Frontend Changes

1. **New Gmail Send Page** (`/send-gmail`)

   - Similar UI to original send-email page
   - Gmail-specific branding and features
   - OAuth2 status checking
   - Gmail API integration

2. **Navigation Updates**
   - Changed "Send Email" to "Send Gmail" in navigation
   - Old SES-based page moved to `/send-email-ses` (hidden)
   - Added redirect from `/send-email` to `/send-gmail`

## API Endpoints

### Gmail API Endpoints

- `GET /api/gmail/status` - Check Gmail API access status
- `POST /api/gmail/send` - Send email via Gmail API
- `GET /api/gmail/test` - Test Gmail API connection

### Request/Response Format

```typescript
// Send Email Request
{
  "to": "recipient@example.com",
  "subject": "Email Subject",
  "body": "Email content",
  "html": true
}

// Response
{
  "success": true,
  "message": "Email sent successfully via Gmail API",
  "to": "recipient@example.com",
  "subject": "Email Subject",
  "provider": "Gmail API"
}
```

## Setup Instructions

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to **APIs & Services** → **Credentials**
3. Edit your existing OAuth 2.0 Client ID
4. In **Authorized scopes**, add: `https://www.googleapis.com/auth/gmail.send`
5. Save the changes

### 2. Environment Variables

Ensure these are set in your environment:

```bash
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

### 3. Testing

1. Start the application
2. Navigate to `/send-gmail`
3. Click "Test Gmail Connection" to verify setup
4. Send a test email

## Key Features

### Gmail-Specific Features

- **OAuth2 Authentication**: Users authenticate with their Google account
- **Gmail API Integration**: Uses official Gmail API for sending emails
- **User's Own Account**: Emails are sent from the user's Gmail account
- **No Daily Limits**: Unlike SES, no daily sending limits
- **Sent Folder Integration**: Emails appear in user's Gmail Sent folder

### Security Features

- OAuth2 secure authentication
- Token-based access control
- HTTPS-only communication
- Google security standards compliance
- User consent for Gmail access

## Differences from SES Email

| Feature        | SES Email              | Gmail Email            |
| -------------- | ---------------------- | ---------------------- |
| Provider       | AWS SES                | Gmail API              |
| Authentication | AWS Credentials        | OAuth2                 |
| Daily Limits   | 200 emails (sandbox)   | User's Gmail limits    |
| Sent Folder    | No                     | Yes (appears in Gmail) |
| Setup          | Complex (verification) | Simple (OAuth2)        |
| Cost           | Pay per email          | Free (user's Gmail)    |
| Branding       | Your domain            | User's Gmail           |

## File Structure

```
app/src/app/
├── send-gmail/           # New Gmail-based send page
│   └── page.tsx
├── send-email/           # Redirect to Gmail page
│   └── page.tsx
└── send-email-ses/       # Original SES page (hidden)
    └── page.tsx

api/src/main/java/com/outreachly/outreachly/
├── controller/
│   └── GmailController.java    # Gmail API endpoints
├── service/
│   └── GmailService.java       # Gmail API service
└── config/
    └── OAuth2Config.java       # Updated OAuth2 config
```

## Usage

1. **For Users**: Navigate to "Send Gmail" in the sidebar
2. **Authentication**: First-time users need to authenticate with Google
3. **Sending**: Compose and send emails using Gmail API
4. **Monitoring**: Check Gmail API status and test connection

## Troubleshooting

### Common Issues

1. **"Gmail API Access Required"**

   - User needs to re-authenticate with Google
   - Click "Re-authenticate with Google" button

2. **"Gmail API Test Failed"**

   - Check Google Cloud Console setup
   - Verify Gmail API scope is added
   - Check environment variables

3. **Emails Not Sending**
   - Verify OAuth2 token is valid
   - Check Gmail API quotas
   - Ensure user has Gmail access

### Debug Steps

1. Check Gmail API status: `GET /api/gmail/status`
2. Test connection: `GET /api/gmail/test`
3. Verify OAuth2 scopes in Google Cloud Console
4. Check application logs for detailed error messages

## Future Enhancements

- Gmail API read operations (inbox, sent items)
- Email templates specific to Gmail
- Gmail-specific analytics
- Bulk email operations
- Gmail labels integration
