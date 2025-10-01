# Debug Email Providers Issue

## Problem

The settings page is not showing any email providers.

## Debugging Steps

### 1. Test Backend API Directly

First, let's test if the backend is working:

```bash
# Test the basic endpoint (no auth required)
curl -X GET http://localhost:8080/api/settings/email-providers/test

# Expected response:
{
  "providerCount": 3,
  "providers": [
    {
      "type": "AWS_SES",
      "displayName": "AWS SES",
      "description": "Amazon Simple Email Service"
    },
    {
      "type": "RESEND",
      "displayName": "Resend",
      "description": "Resend Email API"
    },
    {
      "type": "MOCK",
      "displayName": "Mock",
      "description": "Mock Email Provider for Testing"
    }
  ]
}
```

### 2. Check Backend Logs

Look for these log messages in your backend logs:

```
INFO  - Getting available email providers for org: [org-id]
INFO  - Found 3 email providers: [AWS_SES, RESEND, MOCK]
INFO  - Current settings for org [org-id]: provider=resend
INFO  - Returning 3 email provider DTOs
```

### 3. Test Frontend Network Requests

1. Open browser dev tools (F12)
2. Go to Network tab
3. Refresh the settings page
4. Look for requests to `/api/settings/email-providers`
5. Check the response status and content

### 4. Check Browser Console

Look for these console messages:

```
Loading email providers...
Response status: 200
Loaded providers: [array of providers]
```

### 5. Common Issues and Solutions

#### Issue 1: 401 Unauthorized

**Problem**: Authentication is not working
**Solution**: Check if you're logged in and the session is valid

#### Issue 2: 500 Internal Server Error

**Problem**: Backend error (likely organization ID issue)
**Solution**: Check backend logs for the specific error

#### Issue 3: Empty Array Response

**Problem**: No providers are being returned
**Solution**: Check if EmailProviderFactory is properly configured

#### Issue 4: CORS Error

**Problem**: Frontend can't call backend API
**Solution**: Check CORS configuration in Spring Boot

### 6. Quick Fixes to Try

#### Fix 1: Restart Backend

```bash
# Stop and restart your Spring Boot application
# This ensures all new dependencies are loaded
```

#### Fix 2: Clear Browser Cache

- Hard refresh the page (Ctrl+Shift+R)
- Clear browser cache and cookies

#### Fix 3: Check Database

```sql
-- Check if organization_settings table exists
SELECT * FROM organization_settings;

-- Check if there are any organizations
SELECT * FROM organizations;
```

### 7. Expected Behavior After Fix

1. ✅ Settings page loads without errors
2. ✅ Email providers section shows loading spinner
3. ✅ 3 providers appear: AWS SES, Resend, Mock
4. ✅ One provider shows as "Active" (green dot)
5. ✅ Other providers show as "Healthy" (blue dot)
6. ✅ Test buttons are enabled for active provider

### 8. Next Steps

Once providers are showing:

1. **Test Provider Switching**: Try switching between providers
2. **Test Configuration**: Enter API keys and test configuration
3. **Test Email Sending**: Send a test email with different providers

## Still Having Issues?

If the providers still don't show up:

1. **Check the test endpoint**: `GET /api/settings/email-providers/test`
2. **Check backend logs** for any errors
3. **Check browser console** for JavaScript errors
4. **Verify authentication** is working properly

The test endpoint should work without authentication and will tell us if the basic provider factory is working correctly.

