# Authentication Feature - Comprehensive Documentation

## Table of Contents

1. [Overview](#overview)
2. [Authentication Methods](#authentication-methods)
3. [Architecture](#architecture)
4. [Implementation Details](#implementation-details)
5. [Token Management](#token-management)
6. [Password Security](#password-security)
7. [OAuth Integration](#oauth-integration)
8. [API Reference](#api-reference)
9. [Testing](#testing)
10. [Troubleshooting](#troubleshooting)
11. [Future Improvements](#future-improvements)

---

## Overview

The Authentication feature handles user identity verification across NeoTool applications. It supports multiple authentication methods including password-based login, OAuth 2.0, and JWT token-based authentication for stateless API access.

### Key Features

- **Password Authentication**: Secure password-based login with Argon2id hashing
- **OAuth 2.0**: Social login integration (Google, extensible to others)
- **JWT Tokens**: Stateless authentication with access and refresh tokens
- **Remember Me**: Long-lived sessions with refresh tokens
- **Password Reset**: Secure password recovery flow with rate limiting
- **User Registration**: Account creation with email validation
- **Principal Management**: Unified identity system for users and services

### Design Principles

- **Security First**: Industry-standard cryptographic practices
- **Stateless Authentication**: JWT tokens for horizontal scalability
- **Token Rotation**: Refresh tokens prevent long-lived access tokens
- **Rate Limiting**: Protection against brute force attacks
- **Audit Trail**: All authentication events are logged
- **User Privacy**: Minimal exposure of authentication details

---

## Authentication Methods

### 1. Password-Based Authentication

Standard email/password authentication with secure password hashing.

**Flow:**
1. User submits email and password
2. System validates credentials
3. Password verified using Argon2id
4. Principal status checked (enabled/disabled)
5. JWT access and refresh tokens generated
6. Tokens returned to client

**Security:**
- Argon2id password hashing (winner of Password Hashing Competition)
- Salt is automatically generated per password
- Configurable hash parameters for performance vs. security tradeoff
- Rate limiting on login attempts
- Account lockout after repeated failures

**Code Example:**
```kotlin
val user = authenticationService.authenticate(
    email = "user@example.com",
    password = "SecurePassword123!"
)

if (user != null) {
    val accessToken = authenticationService.generateAccessToken(user)
    val refreshToken = authenticationService.generateRefreshToken(user)
    // Return tokens to client
} else {
    // Authentication failed
}
```

### 2. OAuth 2.0 Authentication

Social login integration for seamless user onboarding.

**Supported Providers:**
- Google (implemented)
- GitHub (planned)
- Microsoft (planned)
- Custom OIDC providers (extensible)

**Flow:**
1. User clicks "Sign in with Google"
2. Frontend loads Google Identity Services
3. Google shows sign-in popup
4. User authenticates with Google
5. Google returns ID token (JWT) to frontend
6. Frontend sends ID token to backend via GraphQL mutation
7. Backend validates ID token using Google's public keys
8. Backend extracts user claims (email, name, etc.)
9. Backend creates/updates user in database
10. **Backend generates its own JWT access token and refresh token**
11. Frontend stores tokens and redirects user

**Important: Two Different JWTs**

The OAuth flow involves **two distinct JWT tokens** with different purposes:

1. **Google's ID Token (JWT)**:
   - **Issued by**: Google
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
   - **Validation**: Your services validate using your public keys
   - **Usage**: Included in every API request (`Authorization: Bearer <token>`)

**Why Two Tokens?**

- **Different purposes**: Google's token authenticates with Google; your token authorizes access to your APIs
- **Different claims**: Google's token has Google-specific claims; your token has your application's user ID and permissions
- **Different lifetimes**: Google tokens are longer-lived; your access tokens are short-lived for security
- **Different validation**: Your services can't validate Google tokens directly; they need your tokens with your user context

**Security:**
- HTTPS-only OAuth redirects
- State parameter prevents CSRF attacks
- ID token signature validation using Google's public keys
- Email verification status checked
- Nonce validation for replay protection
- Application tokens signed with RS256 using your private key

**Code Example:**
```kotlin
// Step 1: Validate Google's ID token and get/create user
val user = authenticationService.authenticateWithOAuth(
    provider = "google",
    idToken = googleIdToken  // Google's JWT token
)

// Step 2: Generate your application's JWT tokens
val accessToken = authenticationService.generateAccessToken(user)  // Your JWT token
val refreshToken = authenticationService.generateRefreshToken(user)  // Your JWT token
```

### 3. JWT Token Authentication

Stateless authentication for API requests.

**Token Types:**

| Token Type | Lifetime | Purpose | Storage |
|-----------|----------|---------|---------|
| Access Token | 15 minutes | API authentication | Memory only |
| Refresh Token | 7 days | Token renewal | httpOnly cookie |

**Flow:**
1. Client includes JWT in Authorization header
2. System validates token signature
3. System checks token expiration
4. User loaded from database
5. Principal status verified (enabled)
6. Request principal created
7. Request proceeds if valid

**Security:**
- HMAC-SHA256 signature (configurable)
- Short-lived access tokens (15 min)
- Refresh tokens for extended sessions
- Token blacklist for immediate revocation
- Audience and issuer validation

**Code Example:**
```kotlin
// Validate access token
val user = authenticationService.validateAccessToken(accessToken)
if (user != null) {
    // Token valid, proceed with request
}

// Refresh tokens
val refreshedUser = authenticationService.validateRefreshToken(refreshToken)
if (refreshedUser != null) {
    val newAccessToken = authenticationService.generateAccessToken(refreshedUser)
    val newRefreshToken = authenticationService.generateRefreshToken(refreshedUser)
    // Return new tokens
}
```

### 4. Remember Me

Long-lived sessions using refresh tokens.

**Flow:**
1. User checks "Remember Me" during login
2. Refresh token generated with extended lifetime
3. Refresh token stored in httpOnly cookie
4. Access token expires after 15 minutes
5. Client automatically refreshes using refresh token
6. New access and refresh tokens issued
7. Session continues seamlessly

**Security:**
- Refresh tokens stored in database
- Token rotation on every refresh
- One-time use refresh tokens
- Automatic invalidation on logout
- Protection against token replay attacks

---

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Login Form   │  │OAuth Button  │  │ Token        │     │
│  │              │  │              │  │ Interceptor  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼ HTTP/GraphQL
┌─────────────────────────────────────────────────────────────┐
│                  GraphQL/HTTP Layer                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │     AuthenticationResolver / Controller              │  │
│  │  - login(email, password)                            │  │
│  │  - loginWithOAuth(provider, idToken)                 │  │
│  │  - register(name, email, password)                   │  │
│  │  - refreshToken(refreshToken)                        │  │
│  │  - logout()                                           │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Service Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │Authentication│  │   JWT        │  │   OAuth      │     │
│  │   Service    │  │  Service     │  │  Provider    │     │
│  │              │  │              │  │  Registry    │     │
│  │- authenticate│  │- generate    │  │- validate    │     │
│  │- register    │  │- validate    │  │- extract     │     │
│  │- resetPW     │  │- refresh     │  │  claims      │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│         │                  │                  │            │
│         └──────────────────┼──────────────────┘            │
│                            │                                │
│                  ┌─────────▼─────────┐                     │
│                  │ Rate Limit        │                     │
│                  │ Service           │                     │
│                  └───────────────────┘                     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Repository Layer                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │  User    │ │Principal │ │Password  │ │  Audit   │      │
│  │Repository│ │Repository│ │  Reset   │ │   Log    │      │
│  │          │ │          │ │Repository│ │Repository│      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Database (PostgreSQL)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │  users   │ │principals│ │password_ │                    │
│  │          │ │          │ │reset_    │                    │
│  │          │ │          │ │attempts  │                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

### Authentication Flow

```
┌─────────┐
│  User   │
└────┬────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 1. Submit Credentials               │
│    - Email + Password               │
│    OR                               │
│    - OAuth Provider + ID Token      │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 2. Validate Credentials             │
│    a. Password Authentication:      │
│       - Find user by email          │
│       - Verify password hash        │
│    b. OAuth Authentication:         │
│       - Validate ID token           │
│       - Extract user claims         │
│       - Create/find user account    │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 3. Check Principal Status           │
│    - Load principal by user ID      │
│    - Verify enabled = true          │
│    - Reject if disabled             │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 4. Generate Tokens                  │
│    - Create access token (JWT)      │
│    - Create refresh token (JWT)     │
│    - Save refresh token to DB       │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 5. Return Tokens                    │
│    - Access token in response       │
│    - Refresh token in httpOnly      │
│      cookie (optional)              │
└────┬────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ 6. Audit Logging                    │
│    - Log authentication event       │
│    - Record timestamp, IP, UA       │
│    - Track success/failure          │
└─────────────────────────────────────┘
```

---

## Implementation Details

### Backend Implementation

#### AuthenticationService

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AuthenticationService.kt`

**Key Methods:**

```kotlin
class AuthenticationService {
    // Password authentication
    fun authenticate(email: String, password: String): UserEntity?

    // OAuth authentication
    fun authenticateWithOAuth(provider: String, idToken: String): UserEntity?

    // User registration
    fun registerUser(name: String, email: String, password: String): UserEntity

    // Token management
    fun generateAccessToken(user: UserEntity): String
    fun generateRefreshToken(user: UserEntity): String
    fun validateAccessToken(token: String): UserEntity?
    fun validateRefreshToken(token: String): UserEntity?

    // Password management
    fun hashPassword(password: String): String
    fun verifyPassword(password: String, hash: String): Boolean
    fun validatePasswordStrength(password: String): Boolean

    // Remember Me
    fun saveRememberMeToken(userId: UUID, token: String): UserEntity
    fun clearRememberMeToken(userId: UUID): UserEntity
    fun authenticateByToken(token: String): UserEntity?
}
```

**Implementation Highlights:**

1. **Password Hashing**: Argon2id with automatic salt generation
2. **Token Generation**: JWT with configurable expiration
3. **Principal Checking**: Validates user enabled status before authentication
4. **OAuth Integration**: Extensible provider registry pattern
5. **Error Handling**: Detailed logging without exposing sensitive info

#### JwtService

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/JwtService.kt`

**Responsibilities:**
- Generate JWT tokens with claims
- Validate token signatures
- Check token expiration
- Extract user ID from tokens
- Differentiate access vs. refresh tokens

**Configuration:**
```yaml
jwt:
  secret: "your-secret-key-min-32-chars"
  accessTokenExpirationSeconds: 900     # 15 minutes
  refreshTokenExpirationSeconds: 604800  # 7 days
```

#### OAuth Provider Registry

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/OAuthProviderRegistry.kt`

**Supported Providers:**
```kotlin
interface OAuthProvider {
    fun getProviderName(): String
    fun validateAndExtractClaims(idToken: String): OAuthUserClaims?
}

class GoogleOAuthProvider : OAuthProvider {
    override fun getProviderName() = "google"

    override fun validateAndExtractClaims(idToken: String): OAuthUserClaims? {
        // Validate Google ID token
        // Extract email, name, picture
        // Return user claims
    }
}
```

### Frontend Implementation

#### Login Form

**Location**: `web/src/features/auth/components/LoginForm.tsx`

**Features:**
- Email/password input
- Client-side validation
- Error handling
- Loading states
- Remember Me checkbox
- OAuth buttons

**Example:**
```tsx
function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login, loginWithOAuth } = useAuth();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await login(email, password);
      // Redirect to dashboard
    } catch (error) {
      // Show error message
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input type="email" value={email} onChange={e => setEmail(e.target.value)} />
      <input type="password" value={password} onChange={e => setPassword(e.target.value)} />
      <button type="submit">Sign In</button>

      <button onClick={() => loginWithOAuth('google')}>
        Sign in with Google
      </button>
    </form>
  );
}
```

#### Token Management

**Location**: `web/src/lib/auth/token-manager.ts`

**Responsibilities:**
- Store access token in memory
- Store refresh token in httpOnly cookie
- Automatic token refresh before expiration
- Token cleanup on logout
- Request interceptor for adding Authorization header

**Example:**
```typescript
class TokenManager {
  private accessToken: string | null = null;

  setAccessToken(token: string) {
    this.accessToken = token;
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  clearTokens() {
    this.accessToken = null;
    // Clear refresh token cookie
  }

  async refreshAccessToken(): Promise<string> {
    // Call refresh endpoint
    // Update access token
    // Return new token
  }
}
```

---

## Token Management

### Access Tokens

**Purpose**: Short-lived tokens for API authentication

**Lifetime**: 15 minutes (configurable)

**Claims:**
```json
{
  "sub": "user-id",
  "type": "access",
  "email": "user@example.com",
  "permissions": ["security:user:view", "..."],
  "iat": 1234567890,
  "exp": 1234568790
}
```

**Storage**: Memory only (never localStorage)

**Usage:**
```http
GET /api/users
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Refresh Tokens

**Purpose**: Long-lived tokens for obtaining new access tokens

**Lifetime**: 7 days (configurable)

**Claims:**
```json
{
  "sub": "user-id",
  "type": "refresh",
  "iat": 1234567890,
  "exp": 1235172690
}
```

**Storage**: httpOnly cookie or secure storage

**Usage:**
```http
POST /api/auth/refresh
Cookie: refreshToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Token Rotation

**Flow:**
1. Access token expires
2. Client detects 401 Unauthorized
3. Client calls refresh endpoint with refresh token
4. Server validates refresh token
5. Server generates new access token
6. Server generates new refresh token
7. Server invalidates old refresh token
8. Server returns both tokens

**Benefits:**
- Limits exposure window
- Prevents token replay attacks
- Enables immediate revocation
- Improves security posture

---

## Password Security

### Password Hashing

**Algorithm**: Argon2id (Argon2 variant resistant to both GPU and side-channel attacks)

**Why Argon2id?**
- Winner of Password Hashing Competition (2015)
- Memory-hard algorithm (resistant to ASIC attacks)
- Configurable parameters (memory, iterations, parallelism)
- Resistant to timing attacks
- Better than BCrypt, PBKDF2, Scrypt

**Parameters:**
```kotlin
// Default Argon2id parameters
val argon2 = Argon2Factory.create(
    Argon2Factory.Argon2Types.ARGON2id,
    saltLength = 16,      // 16 bytes = 128 bits
    hashLength = 32,      // 32 bytes = 256 bits
    parallelism = 1,      // Single thread
    memory = 65536,       // 64 MB
    iterations = 3        // 3 iterations
)
```

**Hash Format:**
```
$argon2id$v=19$m=65536,t=3,p=1$BASE64_SALT$BASE64_HASH
```

### Password Requirements

**Minimum Requirements:**
- Length: 8 characters minimum
- Uppercase: At least 1 uppercase letter
- Lowercase: At least 1 lowercase letter
- Number: At least 1 digit
- Special: At least 1 special character
- No common passwords (dictionary check)

**Validation:**
```kotlin
fun validatePasswordStrength(password: String): Boolean {
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasLowercase = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecial = password.any { !it.isLetterOrDigit() }

    return hasMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecial
}
```

### Password Reset

**Flow:**
1. User requests password reset
2. System generates unique reset token
3. Token stored with expiration (1 hour)
4. Email sent with reset link
5. User clicks link, submits new password
6. System validates token and expiration
7. Password updated with new hash
8. All sessions invalidated
9. User must login with new password

**Security:**
- Rate limiting: Max 3 attempts per hour per email
- Token expiration: 1 hour
- One-time use tokens
- HTTPS-only reset links
- Token invalidation after use or timeout

**Code Example:**
```kotlin
// Request password reset
passwordResetService.requestPasswordReset(email)

// Validate reset token
val isValid = passwordResetService.validateResetToken(token)

// Reset password
passwordResetService.resetPassword(token, newPassword)
```

---

## OAuth Integration

### Google OAuth

**Configuration:**
```yaml
oauth:
  google:
    clientId: "your-google-client-id"
    clientSecret: "your-google-client-secret"
    redirectUri: "https://yourapp.com/auth/google/callback"
```

**Flow:**
1. User clicks "Sign in with Google"
2. Redirect to Google OAuth consent page
3. User authorizes application
4. Google redirects back with authorization code
5. Backend exchanges code for ID token
6. ID token validated using Google's public keys
7. User claims extracted (email, name, picture)
8. User account created or retrieved
9. JWT tokens generated

**Scopes:**
- `openid`: OpenID Connect authentication
- `email`: Access to user's email
- `profile`: Access to user's profile information

**Security:**
- State parameter prevents CSRF
- Nonce prevents replay attacks
- HTTPS-only redirect URIs
- ID token signature validation
- Email verification check

### Adding New OAuth Providers

**Steps:**
1. Implement `OAuthProvider` interface
2. Register provider in `OAuthProviderRegistry`
3. Add configuration properties
4. Update frontend with provider button
5. Test authentication flow
6. Document provider-specific requirements

**Example:**
```kotlin
@Singleton
class GitHubOAuthProvider(
    @Value("\${oauth.github.clientId}") private val clientId: String,
    @Value("\${oauth.github.clientSecret}") private val clientSecret: String,
) : OAuthProvider {

    override fun getProviderName() = "github"

    override fun validateAndExtractClaims(idToken: String): OAuthUserClaims? {
        // Validate GitHub token
        // Extract user info
        // Return claims
    }
}
```

---

## API Reference

### GraphQL Mutations

#### login

Authenticate user with email and password.

```graphql
mutation Login($email: String!, $password: String!) {
  login(email: $email, password: $password) {
    accessToken
    refreshToken
    user {
      id
      email
      displayName
    }
  }
}
```

#### loginWithOAuth

Authenticate user with OAuth provider.

```graphql
mutation LoginWithOAuth($provider: String!, $idToken: String!) {
  loginWithOAuth(provider: $provider, idToken: $idToken) {
    accessToken
    refreshToken
    user {
      id
      email
      displayName
    }
  }
}
```

#### register

Create new user account.

```graphql
mutation Register($name: String!, $email: String!, $password: String!) {
  register(name: $name, email: $email, password: $password) {
    accessToken
    refreshToken
    user {
      id
      email
      displayName
    }
  }
}
```

#### refreshToken

Obtain new access token using refresh token.

```graphql
mutation RefreshToken($refreshToken: String!) {
  refreshToken(refreshToken: $refreshToken) {
    accessToken
    refreshToken
  }
}
```

#### logout

Invalidate current session.

```graphql
mutation Logout {
  logout
}
```

### REST Endpoints

#### POST /api/auth/login

Authenticate user with credentials.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123!"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "user": {
    "id": "123",
    "email": "user@example.com",
    "displayName": "User Name"
  }
}
```

#### POST /api/auth/refresh

Refresh access token.

**Request:**
```json
{
  "refreshToken": "eyJhbGci..."
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci..."
}
```

---

## Testing

### Unit Tests

**Location**: `service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/AuthenticationServiceTest.kt`

**Test Coverage:**
- Password hashing and verification
- User authentication (success/failure cases)
- OAuth authentication
- Token generation and validation
- Password strength validation
- User registration
- Remember Me functionality

**Running Tests:**
```bash
# Run all authentication unit tests
./gradlew :security:test --tests "*AuthenticationServiceTest*"

# Run with coverage
./gradlew :security:test :security:koverXmlReport --tests "*AuthenticationServiceTest*"
```

**Key Test Scenarios:**
```kotlin
@Test
fun `should authenticate user with correct credentials`() {
    // Arrange
    val user = createUserWithPassword("test@example.com", "password123")

    // Act
    val result = authenticationService.authenticate("test@example.com", "password123")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result?.email).isEqualTo("test@example.com")
}

@Test
fun `should return null for disabled user`() {
    // Arrange
    val user = createDisabledUser("test@example.com")

    // Act
    val result = authenticationService.authenticate("test@example.com", "password123")

    // Assert
    assertThat(result).isNull()
}
```

### Integration Tests

**Location**: `service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/`

**Test Scenarios:**
- Full authentication flow with database
- Token refresh flow
- Password reset flow
- OAuth authentication end-to-end
- Rate limiting enforcement
- Session management

### E2E Tests

**Location**: `web/tests/e2e/auth.e2e.spec.ts`

**Test Scenarios:**
- Login with valid credentials
- Login with invalid credentials
- OAuth login flow
- Token refresh on expiration
- Logout and session cleanup
- Password reset flow
- User registration

**Example:**
```typescript
test('should login with valid credentials', async ({ page }) => {
  await page.goto('/login');
  await page.fill('[name="email"]', 'test@example.com');
  await page.fill('[name="password"]', 'password123');
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL('/dashboard');
});
```

---

## Troubleshooting

### Common Issues

#### Issue: "Invalid credentials"

**Symptoms:** Login fails with correct password

**Causes:**
1. Password hash mismatch
2. User account disabled
3. Email case sensitivity

**Solutions:**
```sql
-- Check user status
SELECT id, email, enabled FROM users u
JOIN principals p ON p.external_id = u.id::text
WHERE u.email ILIKE 'user@example.com';

-- Reset password
UPDATE users
SET password_hash = '$argon2id$v=19$...'
WHERE email = 'user@example.com';
```

#### Issue: "Token expired"

**Symptoms:** Requests fail with 401 Unauthorized

**Causes:**
1. Access token expired (15 min)
2. Refresh token expired (7 days)
3. Token not refreshed properly

**Solutions:**
- Implement automatic token refresh
- Check token expiration before requests
- Handle 401 responses with refresh flow

#### Issue: "OAuth authentication fails"

**Symptoms:** OAuth login doesn't work

**Causes:**
1. Invalid OAuth configuration
2. Redirect URI mismatch
3. ID token validation failure

**Solutions:**
```yaml
# Verify OAuth configuration
oauth:
  google:
    clientId: "correct-client-id"
    redirectUri: "https://yourapp.com/auth/google/callback"
```

---

## Future Improvements

### Planned Features

#### Multi-Factor Authentication (MFA)
- TOTP-based (Google Authenticator, Authy)
- SMS-based OTP
- Backup codes
- Recovery codes
- MFA enforcement policies

#### Passwordless Authentication
- WebAuthn / FIDO2
- Magic links via email
- Biometric authentication
- Passkeys support

#### Enhanced Security
- Device fingerprinting
- Suspicious login detection
- Geo-blocking
- Login notifications
- Account recovery options

#### Session Management
- Active session listing
- Remote session termination
- Concurrent session limits
- Session activity monitoring

#### Password Security
- Breach password detection (HaveIBeenPwned)
- Password history (prevent reuse)
- Custom password policies
- Password expiration

---

## Related Documentation

- [Authorization](../authorization/README.md) - Access control and permissions
- [Security Overview](../README.md) - Complete security feature guide
- [Feature Files](./signin/) - Gherkin specifications
- [ADR 0008](../../../09-adr/0008-interservice-security-migration-plan.md) - Interservice security architecture

---

**Last Updated**: December 2024
**Version**: 1.0
**Status**: Production Ready
