# OAuth2 Setup for Outreachly

This document provides instructions for setting up OAuth2 authentication in your Outreachly application.

## Database Recommendation: Aurora Serverless v2 ✅

**Stay with Aurora Serverless v2** - it's already configured and perfect for your SaaS application:

- Auto-scaling from 0.5 to 1.0 ACUs
- Cost-effective for variable workloads
- Better performance for OAuth token management
- Already integrated with Secrets Manager

## What's Been Implemented

### Backend (Spring Boot)

1. **OAuth2 Dependencies** - Added to `pom.xml`
2. **User Entity** - JPA entity with OAuth provider support
3. **Database Migration** - V2 migration for users table
4. **OAuth2 Configuration** - Google, GitHub, Microsoft providers
5. **Security Handlers** - Success/failure authentication handlers
6. **Auth Controller** - REST endpoints for user management
7. **User Service** - Business logic for user operations

### Frontend (Next.js)

1. **Auth Page** - Login page with OAuth provider buttons
2. **Callback Page** - Handles OAuth redirects
3. **Provider Integration** - Fetches available providers from API

## Setup Instructions

### 1. OAuth Provider Setup

#### Google OAuth2

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable Google+ API
4. Go to Credentials → Create Credentials → OAuth 2.0 Client ID
5. Set authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
6. Copy Client ID and Client Secret

#### GitHub OAuth2

1. Go to GitHub Settings → Developer settings → OAuth Apps
2. Create new OAuth App
3. Set Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
4. Copy Client ID and Client Secret

#### Microsoft OAuth2

1. Go to [Azure Portal](https://portal.azure.com/)
2. Register new application
3. Add redirect URI: `http://localhost:8080/login/oauth2/code/microsoft`
4. Copy Application (client) ID and create client secret

### 2. Environment Variables

Add these to your environment or AWS Secrets Manager:

```bash
# OAuth2 Client IDs and Secrets
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
MICROSOFT_CLIENT_ID=your-microsoft-client-id
MICROSOFT_CLIENT_SECRET=your-microsoft-client-secret
```

### 3. Database Migration

The V2 migration will create the users table automatically when you start the application.

### 4. Running the Application

#### Backend

```bash
cd api
mvn spring-boot:run
```

#### Frontend

```bash
cd app
npm run dev
```

### 5. Testing OAuth2 Flow

1. Navigate to `http://localhost:3000/auth`
2. Click on any OAuth provider button
3. Complete OAuth flow with the provider
4. You'll be redirected back to `/auth/callback`
5. Check the database to see the created user

## API Endpoints

### Authentication

- `GET /auth/providers` - Get available OAuth providers
- `GET /auth/me` - Get current user info (requires authentication)
- `POST /auth/logout` - Logout current user

### OAuth2 Endpoints (Spring Security)

- `GET /oauth2/authorization/{provider}` - Initiate OAuth flow
- `GET /login/oauth2/code/{provider}` - OAuth callback (handled by Spring)

## Database Schema

The `users` table includes:

- Basic user info (email, name, profile picture)
- OAuth provider details
- User roles and permissions
- Account status flags
- Timestamps

## Security Features

1. **CORS Configuration** - Allows frontend communication
2. **JWT Support** - Ready for token-based authentication
3. **Role-based Access** - USER, ADMIN, PREMIUM_USER roles
4. **Provider Validation** - Secure OAuth2 flow
5. **Session Management** - Proper logout handling

## Next Steps

1. Set up OAuth providers with your credentials
2. Test the authentication flow
3. Customize the frontend UI as needed
4. Add additional OAuth providers if required
5. Implement role-based authorization for protected endpoints

## Troubleshooting

### Common Issues

1. **CORS errors** - Ensure frontend URL is in CORS configuration
2. **OAuth redirect mismatch** - Check redirect URIs in provider settings
3. **Database connection** - Verify Aurora Serverless v2 is running
4. **Missing environment variables** - Check all OAuth credentials are set

### Logs

Check application logs for detailed error messages:

```bash
# Backend logs
tail -f api/logs/application.log

# Database logs (if needed)
aws logs describe-log-groups --log-group-name-prefix "/ecs/outreachly"
```
