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

## Step 6: Configure JWT Keys

**Important**: The backend requires an RSA private key to sign your application's JWT access tokens. Without this, you'll get the error: "RS256 requires private key".

### Development Setup (File-based Keys)

For local development, you can use file-based keys:

#### Generate RSA Key Pair

Generate an RSA key pair using OpenSSL:

```bash
# Generate private key (4096 bits recommended)
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:4096

# Extract public key
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem

# Set appropriate permissions
chmod 600 jwt-private.pem
chmod 644 jwt-public.pem
```

#### Configure Environment Variables

**Option A: File Paths (Recommended for Local Development)**

```bash
export JWT_PRIVATE_KEY_PATH=/path/to/jwt-private.pem
export JWT_PUBLIC_KEY_PATH=/path/to/jwt-public.pem
export JWT_KEY_ID=kid-1
```

**Option B: Inline Keys (Useful for Docker/Containers)**

```bash
# Read keys and set as environment variables
export JWT_PRIVATE_KEY="$(cat jwt-private.pem)"
export JWT_PUBLIC_KEY="$(cat jwt-public.pem)"
export JWT_KEY_ID=kid-1
```

**Option C: Docker Compose**

Add to your `docker-compose.yml`:

```yaml
services:
  security:
    environment:
      - JWT_PRIVATE_KEY_PATH=/app/keys/jwt-private.pem
      - JWT_PUBLIC_KEY_PATH=/app/keys/jwt-public.pem
      - JWT_KEY_ID=kid-1
    volumes:
      - ./keys:/app/keys:ro
```

### Production Setup (Vault)

**Important**: In production, JWT keys must be stored in HashiCorp Vault and provisioned via Terraform. File-based keys should NOT be used in production.

**📖 For complete Vault setup instructions, see [Vault Setup Guide](./vault-setup.md)**

The guide covers:
- Local development setup
- Creating secrets manually (CLI, UI, API)
- Creating secrets with Terraform
- Configuration and verification
- Troubleshooting
- Production considerations

**Quick Reference:**
- **Secret Path**: `secret/jwt/keys/{keyId}` (default: `secret/jwt/keys/kid-1`)
- **Secret Fields**: `private` (RSA private key) and `public` (RSA public key)
- **Configuration**: Set `VAULT_ENABLED=true`, `VAULT_ADDRESS`, `VAULT_TOKEN`, and `JWT_KEY_ID`

### Security Notes

**Development:**
- Never commit private keys to version control
- Use different keys for development and production
- Set appropriate file permissions (`chmod 600` for private key)

**Production:**
- ✅ **Use Vault** for key storage (required)
- ✅ Store Vault token securely (environment variable or K8s service account)
- ✅ Use Vault policies to restrict key access
- ✅ Enable Vault audit logging
- ✅ Rotate Vault tokens regularly
- ❌ **Never use file-based keys in production**
- ❌ Never commit Vault tokens to code
- ❌ Don't use root Vault tokens

## Step 7: Verify Dependencies

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

## Step 8: Test the Setup

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

**"RS256 requires private key"**
- Ensure `JWT_PRIVATE_KEY_PATH` or `JWT_PRIVATE_KEY` environment variable is set
- Verify the private key file exists and is readable
- Check that the private key is in PEM format
- Restart the backend service after setting environment variables

## Step 9: Production Checklist

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
10. Backend generates its own JWT access token and refresh token
    ↓
11. Frontend stores tokens and redirects user
```

### Two-JWT Token Flow

**Important**: The OAuth flow involves **two distinct JWT tokens** with different purposes:

1. **Google's ID Token (JWT)**:
   - **Issued by**: Google (`accounts.google.com`)
   - **Purpose**: Proves user identity with Google (authentication)
   - **Lifetime**: ~1 hour
   - **Contains**: Google user info (email, name, picture, etc.)
   - **Validation**: Backend validates using Google's public keys
   - **Usage**: One-time use during login to verify identity

2. **Application's Access Token (JWT)**:
   - **Issued by**: Your NeoTool security service
   - **Purpose**: Authorizes API access to your application (authorization)
   - **Lifetime**: 15 minutes (configurable)
   - **Contains**: Your user ID, permissions, email
   - **Validation**: Your services validate using your public keys (RS256)
   - **Usage**: Included in every API request (`Authorization: Bearer <token>`)

**Why Two Tokens?**

- **Different purposes**: Google's token authenticates with Google; your token authorizes access to your APIs
- **Different claims**: Google's token has Google-specific claims; your token has your application's user ID and permissions
- **Different lifetimes**: Google tokens are longer-lived; your access tokens are short-lived for security
- **Different validation**: Your services can't validate Google tokens directly; they need your tokens with your user context

**Note**: The backend requires an RSA private key to sign your application's JWT tokens. See [JWT Configuration](#jwt-configuration) for setup instructions.

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

