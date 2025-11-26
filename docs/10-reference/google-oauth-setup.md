# Google OAuth Setup Guide

This guide walks you through setting up Google Identity Provider (OAuth 2.0) for NeoTool authentication.

## Overview

The NeoTool application uses Google OAuth 2.0 for social authentication. The implementation consists of:

- **Frontend**: Google Identity Services (GIS) library for client-side authentication
- **Backend**: Google OAuth token validation using Google's public keys
- **Flow**: User signs in with Google → receives ID token → backend validates token → creates/updates user → returns JWT

## Prerequisites

- A Google account
- Access to [Google Cloud Console](https://console.cloud.google.com/)
- Your application running locally or deployed

## Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click the project dropdown at the top
3. Click **"New Project"**
4. Enter a project name (e.g., "NeoTool")
5. Click **"Create"**
6. Wait for the project to be created and select it

## Step 2: Configure OAuth Consent Screen

1. In the Google Cloud Console, navigate to **APIs & Services** → **OAuth consent screen**
2. Select **External** (unless you have a Google Workspace account, then use Internal)
3. Click **"Create"**

### Fill in the OAuth consent screen:

- **App name**: NeoTool (or your app name)
- **User support email**: Your email address
- **Developer contact information**: Your email address
- Click **"Save and Continue"**

### Scopes (Step 2):

1. Click **"Add or Remove Scopes"**
2. Select the following scopes:
   - `openid`
   - `email`
   - `profile`
3. Click **"Update"**
4. Click **"Save and Continue"**

### Test users (Step 3):

1. If your app is in "Testing" mode, add test user emails
2. Click **"Save and Continue"**
3. Review and click **"Back to Dashboard"**

## Step 3: Create OAuth 2.0 Credentials

1. Navigate to **APIs & Services** → **Credentials**
2. Click **"+ CREATE CREDENTIALS"** → **"OAuth client ID"**
3. If prompted, configure the OAuth consent screen first (see Step 2)

### Application type:

Select **"Web application"**

### Name:

Enter a name (e.g., "NeoTool Web Client")

### Authorized JavaScript origins:

Add your application URLs:

**For local development:**
```
http://localhost:3000
http://127.0.0.1:3000
```

**For production:**
```
https://yourdomain.com
https://www.yourdomain.com
```

### Authorized redirect URIs:

For Google Identity Services (GIS), you typically don't need redirect URIs, but if you're using the older OAuth flow, add:

**For local development:**
```
http://localhost:3000/auth/callback
```

**For production:**
```
https://yourdomain.com/auth/callback
```

4. Click **"Create"**
5. **IMPORTANT**: Copy the **Client ID** (you'll need this in the next steps)
   - The Client ID looks like: `123456789-abcdefghijklmnop.apps.googleusercontent.com`
6. Click **"OK"**

## Step 4: Configure Backend Environment Variables

The backend needs the Google Client ID to validate ID tokens.

### Option A: Environment Variable (Recommended)

Set the environment variable when running the service:

```bash
export GOOGLE_CLIENT_ID="your-client-id-here.apps.googleusercontent.com"
```

### Option B: application.yml

Edit `service/kotlin/security/src/main/resources/application.yml`:

```yaml
oauth:
  google:
    client-id: ${GOOGLE_CLIENT_ID:your-client-id-here.apps.googleusercontent.com}
    issuer: ${GOOGLE_ISSUER:accounts.google.com}
```

**Note**: The `issuer` defaults to `accounts.google.com` and usually doesn't need to be changed.

### Option C: Docker Compose

If using Docker Compose, add to your `docker-compose.yml`:

```yaml
services:
  security:
    environment:
      - GOOGLE_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
```

## Step 5: Configure Frontend Environment Variables

The frontend needs the Google Client ID to initialize Google Identity Services.

### Create/Edit `.env.local`

In the `web/` directory, create or edit `.env.local`:

```bash
NEXT_PUBLIC_GOOGLE_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
```

**Important Notes:**
- The `NEXT_PUBLIC_` prefix makes the variable available in the browser
- Never commit `.env.local` to version control (it should be in `.gitignore`)
- Restart your Next.js dev server after adding environment variables

### For Production

Set the environment variable in your deployment platform:

**Vercel:**
- Go to Project Settings → Environment Variables
- Add `NEXT_PUBLIC_GOOGLE_CLIENT_ID` with your client ID

**Docker:**
```yaml
services:
  web:
    environment:
      - NEXT_PUBLIC_GOOGLE_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
```

## Step 6: Verify Dependencies

### Backend Dependencies

The backend already includes the required dependencies in `service/kotlin/security/build.gradle.kts`:

```kotlin
// Google OAuth JWT validation
implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
implementation("com.google.oauth-client:google-oauth-client:1.39.0")
implementation("com.google.api-client:google-api-client:2.2.0")
```

### Frontend Dependencies

The frontend uses Google Identity Services via a script tag (no npm package needed). The implementation is in `web/src/lib/auth/oauth/google.ts`.

## Step 7: Test the Setup

### 1. Start Your Services

**Backend:**
```bash
cd service/kotlin
./gradlew :security:run
```

**Frontend:**
```bash
cd web
npm run dev
```

### 2. Test Google Sign-In

1. Navigate to `http://localhost:3000/signin`
2. Click the **"Continue with Google"** button
3. You should see the Google sign-in popup
4. Sign in with a test user (if in testing mode) or any Google account
5. After successful authentication, you should be redirected to the home page

### 3. Verify Backend Logs

Check the backend logs for:
- Successful token validation messages
- User creation/authentication logs

### 4. Common Issues

**"Google OAuth client ID is not configured"**
- Check that `NEXT_PUBLIC_GOOGLE_CLIENT_ID` is set in `.env.local`
- Restart the Next.js dev server

**"Token verification failed"**
- Verify the Client ID matches between frontend and backend
- Check that the Client ID is correct in Google Cloud Console
- Ensure the token hasn't expired (ID tokens expire after 1 hour)

**"Redirect URI mismatch"**
- Verify authorized JavaScript origins in Google Cloud Console
- Ensure `http://localhost:3000` is added for local development

**"Access blocked: This app's request is invalid"**
- Check OAuth consent screen configuration
- Verify test users are added if app is in "Testing" mode
- Check that required scopes are configured

## Step 8: Production Checklist

Before deploying to production:

- [ ] OAuth consent screen is published (not in "Testing" mode)
- [ ] Production domain is added to Authorized JavaScript origins
- [ ] Production Client ID is configured in environment variables
- [ ] HTTPS is enabled (required for production OAuth)
- [ ] Privacy policy and terms of service URLs are added to OAuth consent screen
- [ ] App logo is uploaded to OAuth consent screen

## Architecture Overview

### Authentication Flow

```
1. User clicks "Continue with Google"
   ↓
2. Frontend loads Google Identity Services
   ↓
3. Google shows sign-in popup
   ↓
4. User authenticates with Google
   ↓
5. Google returns ID token (JWT) to frontend
   ↓
6. Frontend sends ID token to backend via GraphQL mutation
   ↓
7. Backend validates ID token using Google's public keys
   ↓
8. Backend extracts user claims (email, name, etc.)
   ↓
9. Backend creates/updates user in database
   ↓
10. Backend generates JWT and refresh token
    ↓
11. Frontend stores tokens and redirects user
```

### Key Components

**Backend:**
- `OAuthConfig`: Configuration properties for OAuth providers
- `GoogleOAuthProvider`: Validates Google ID tokens
- `OAuthService`: Orchestrates OAuth authentication
- `SecurityAuthResolver`: GraphQL resolver for `signInWithOAuth` mutation

**Frontend:**
- `google.ts`: Google Identity Services wrapper
- `useOAuth.ts`: React hook for OAuth sign-in
- `SignInForm.tsx`: UI component with Google sign-in button

## Security Considerations

1. **Never expose Client Secret**: Google Identity Services doesn't require a client secret for web applications
2. **Validate tokens server-side**: Always validate ID tokens on the backend
3. **Use HTTPS in production**: OAuth requires HTTPS for production
4. **Token expiration**: ID tokens expire after 1 hour; handle expiration gracefully
5. **Email verification**: Check `email_verified` claim before trusting email addresses

## Additional Resources

- [Google Identity Services Documentation](https://developers.google.com/identity/gsi/web)
- [Google OAuth 2.0 for Web Applications](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Validating ID Tokens](https://developers.google.com/identity/sign-in/web/backend-auth)

## Troubleshooting

### Check Configuration

**Backend:**
```bash
# Verify environment variable is set
echo $GOOGLE_CLIENT_ID
```

**Frontend:**
```bash
# In browser console
console.log(process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID)
```

### Enable Debug Logging

**Backend:**
Set log level in `application.yml`:
```yaml
logger:
  levels:
    io.github.salomax.neotool.security: DEBUG
```

### Test Token Validation

You can test token validation directly using curl:

```bash
# Get an ID token from the frontend (check browser console)
# Then test validation (this is just for debugging)
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { signInWithOAuth(input: { provider: \"google\", idToken: \"YOUR_ID_TOKEN_HERE\" }) { token user { email } } }"
  }'
```

## Next Steps

After setting up Google OAuth:

1. Test the complete authentication flow
2. Configure additional OAuth providers (Microsoft, GitHub, etc.) if needed
3. Set up production environment variables
4. Configure OAuth consent screen for production
5. Add error handling and user feedback
6. Implement account linking (link Google account to existing email/password account)

