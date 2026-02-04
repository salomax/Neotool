---
title: Email Verification Implementation Backlog
type: feature
category: authentication
status: draft
version: 1.0.0
tags: [authentication, signup, email-verification, backlog, tasks]
ai_optimized: true
search_keywords: [signup, email-verification, backlog, tasks, implementation, sprint-planning]
related:
  - 03-features/security/authentication/signup/email-verification-design.md
  - 03-features/security/authentication/signup/signup.feature
  - 08-workflows/feature-development.md
last_updated: 2026-02-02
---

# Email Verification Implementation Backlog

> **Purpose**: Detailed task breakdown for implementing email verification in the signup flow, organized by phase and following Specification-Driven Development principles.

## Overview

This backlog breaks down the email verification feature into implementable tasks across 6 phases:

1. **Phase 0**: Specification & Planning (Pre-Development)
2. **Phase 1**: Backend Foundation (Domain & Service Layer)
3. **Phase 2**: Email Integration (Comms Module)
4. **Phase 3**: GraphQL API Layer
5. **Phase 4**: Frontend Implementation
6. **Phase 5**: Authorization & Cleanup Jobs
7. **Phase 6**: Testing, Documentation & Production

**Estimated Timeline**: 3 weeks (15 working days)

**Dependencies**:
- Existing Comms module for email delivery
- Existing Security module for authentication
- Existing Authorization infrastructure

**Current implementation (as of refactor)**: Email verification uses **magic link only**. The 6-digit code flow has been removed. Users receive an email with a single verification link; they click it to land on `/verify-email-link?token=<uuid>`, which calls `verifyEmailWithToken`. The `/verify-email` page shows "check your email for the link" and a "Resend link" button (no code entry). See [email-verification-design.md](email-verification-design.md) for the updated design.

---

### Task 0.3: Create Database Migration Script

**Description**: Create Flyway migration script for email verification tables

**Acceptance Criteria**:
- [ ] Create `V0_XX__add_email_verification.sql` migration
- [ ] Add `email_verifications` table with all required columns
- [ ] Add indexes for performance (user_id, expires_at)
- [ ] Add `email_verified` and `email_verified_at` columns to `users` table
- [ ] Add unique constraint on active verifications
- [ ] Migration is reversible (down migration script)
- [ ] Test migration on local database

**Files to Create**:
- `service/kotlin/security/src/main/resources/db/migration/V0_XX__add_email_verification.sql`

**Schema**:
```sql
-- See email-verification-design.md section "Database Schema Changes"
CREATE TABLE email_verifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    attempts INT DEFAULT 0,
    max_attempts INT DEFAULT 5,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    invalidated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resend_count INT DEFAULT 0,
    created_by_ip VARCHAR(45),
    verified_from_ip VARCHAR(45)
);

CREATE INDEX idx_email_verifications_user_id ON email_verifications(user_id);
CREATE INDEX idx_email_verifications_expires_at ON email_verifications(expires_at)
WHERE verified_at IS NULL AND invalidated_at IS NULL;

CREATE UNIQUE INDEX idx_email_verifications_active
ON email_verifications(user_id)
WHERE invalidated_at IS NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP;

CREATE INDEX idx_users_email_verified ON users(email_verified, created_at)
WHERE email_verified = FALSE;
```

**Assigned To**: Backend Developer
**Estimated Time**: 2 hours
**Priority**: High
**Depends On**: Task 0.1

---

### Task 0.4: Define Configuration Properties

**Description**: Add configuration properties for email verification

**Acceptance Criteria**:
- [ ] Add properties to `application.yml`
- [ ] Document each property with comments
- [ ] Set sensible defaults
- [ ] Add environment-specific overrides (dev, staging, prod)
- [ ] Add feature flag for gradual rollout

**Files to Update**:
- `service/kotlin/security/src/main/resources/application.yml`

**Configuration**:
```yaml
app:
  verification:
    # Feature flag for gradual rollout
    enabled: ${EMAIL_VERIFICATION_ENABLED:true}

    # Code expires after 8 hours (OWASP recommendation)
    code-expiration-hours: ${VERIFICATION_CODE_EXPIRATION_HOURS:8}

    # Max failed verification attempts before new code required
    max-attempts: ${VERIFICATION_MAX_ATTEMPTS:5}

    # Rate limit: max resends per hour
    resend-rate-limit-per-hour: ${VERIFICATION_RESEND_RATE_LIMIT:3}

    # Auto-delete unverified accounts after 7 days
    unverified-account-ttl-days: ${UNVERIFIED_ACCOUNT_TTL_DAYS:7}

    # Auto-verify existing users (migration)
    grandfather-existing-users: ${GRANDFATHER_EXISTING_USERS:true}
    grandfather-cutoff-date: ${GRANDFATHER_CUTOFF_DATE:2026-02-15T00:00:00Z}

    # Email templates
    verification-email-template: "email/verify-email"
    deletion-warning-template: "email/account-deletion-warning"

    # URLs for magic links
    verification-link-base-url: ${APP_BASE_URL:http://localhost:3000}/verify-email-link
```

**Assigned To**: Backend Developer
**Estimated Time**: 1 hour
**Priority**: Medium
**Depends On**: Task 0.1

---

## Phase 1: Backend Foundation (3 days)

### Task 1.1: Create Domain Entities

**Description**: Create JPA entities for email verification

**Acceptance Criteria**:
- [ ] Create `EmailVerificationEntity.kt` following entity pattern
- [ ] Use UUID v7 for primary key
- [ ] Add all required fields (see schema)
- [ ] Add relationship to `UserEntity`
- [ ] Add helper methods (isExpired, isVerified, etc.)
- [ ] Add validation annotations
- [ ] Follow Kotlin coding standards

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/entity/EmailVerificationEntity.kt`

**Pattern Reference**: [05-backend/patterns/entity-pattern.md]

**Code Template**:
```kotlin
@Entity
@Table(name = "email_verifications")
data class EmailVerificationEntity(
    @Id
    val id: UUID = UuidV7.generate(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(name = "code_hash", nullable = false)
    val codeHash: String,

    @Column(name = "token_hash", nullable = false)
    val tokenHash: String,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    val maxAttempts: Int = 5,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    @Column(name = "invalidated_at")
    var invalidatedAt: Instant? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "resend_count", nullable = false)
    var resendCount: Int = 0,

    @Column(name = "created_by_ip", length = 45)
    val createdByIp: String? = null,

    @Column(name = "verified_from_ip", length = 45)
    var verifiedFromIp: String? = null
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun isVerified(): Boolean = verifiedAt != null

    fun isInvalidated(): Boolean = invalidatedAt != null

    fun canRetry(): Boolean = attempts < maxAttempts && !isExpired() && !isInvalidated()

    fun markVerified(ipAddress: String) {
        verifiedAt = Instant.now()
        verifiedFromIp = ipAddress
    }

    fun markInvalidated() {
        invalidatedAt = Instant.now()
    }

    fun incrementAttempts() {
        attempts++
    }
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 2 hours
**Priority**: High
**Depends On**: Task 0.3

---

### Task 1.2: Create Repository Layer

**Description**: Create repository for email verification operations

**Acceptance Criteria**:
- [ ] Create `EmailVerificationRepository.kt` interface
- [ ] Extend `JpaRepository<EmailVerificationEntity, UUID>`
- [ ] Add custom query methods (findActiveByUserId, findRecentByUserId, etc.)
- [ ] Add method documentation
- [ ] Follow repository pattern

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/repository/EmailVerificationRepository.kt`

**Pattern Reference**: [05-backend/patterns/repository-pattern.md]

**Code Template**:
```kotlin
@Repository
interface EmailVerificationRepository : JpaRepository<EmailVerificationEntity, UUID> {

    /**
     * Find active (non-invalidated) verification record for user
     */
    @Query("""
        SELECT ev FROM EmailVerificationEntity ev
        WHERE ev.user.id = :userId
          AND ev.invalidatedAt IS NULL
        ORDER BY ev.createdAt DESC
    """)
    fun findActiveByUserId(userId: UUID): EmailVerificationEntity?

    /**
     * Find recent verification records (for rate limiting)
     */
    @Query("""
        SELECT ev FROM EmailVerificationEntity ev
        WHERE ev.user.id = :userId
          AND ev.createdAt > :since
        ORDER BY ev.createdAt DESC
    """)
    fun findRecentByUserId(userId: UUID, since: Instant): List<EmailVerificationEntity>

    /**
     * Find by token hash (for magic link verification)
     */
    @Query("""
        SELECT ev FROM EmailVerificationEntity ev
        WHERE ev.tokenHash = :tokenHash
          AND ev.invalidatedAt IS NULL
    """)
    fun findByTokenHash(tokenHash: String): EmailVerificationEntity?

    /**
     * Invalidate all verification records for user
     */
    @Modifying
    @Query("""
        UPDATE EmailVerificationEntity ev
        SET ev.invalidatedAt = :timestamp
        WHERE ev.user.id = :userId
          AND ev.invalidatedAt IS NULL
    """)
    fun invalidateAllForUser(userId: UUID, timestamp: Instant = Instant.now())
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 1 hour
**Priority**: High
**Depends On**: Task 1.1

---

### Task 1.3: Create Domain Models

**Description**: Create domain models for email verification (separate from entities)

**Acceptance Criteria**:
- [ ] Create `EmailVerification.kt` domain model
- [ ] Create `VerificationResult.kt` sealed class for operation results
- [ ] Create `ResendResult.kt` sealed class
- [ ] Add mapper to convert between entity and domain
- [ ] Follow domain-driven design principles

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/EmailVerification.kt`
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/VerificationResult.kt`
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/domain/ResendResult.kt`

**Pattern Reference**: [05-backend/patterns/domain-entity-conversion.md]

**Code Template**:
```kotlin
// Domain model
data class EmailVerification(
    val id: UUID,
    val userId: UUID,
    val expiresAt: Instant,
    val verifiedAt: Instant?,
    val attempts: Int,
    val maxAttempts: Int,
    val canRetry: Boolean,
    val isExpired: Boolean,
    val isVerified: Boolean
)

// Result types
sealed class VerificationResult {
    data class Success(val user: UserDomain) : VerificationResult()
    data class InvalidCode(val attemptsRemaining: Int) : VerificationResult()
    object InvalidToken : VerificationResult()
    object NotFound : VerificationResult()
    object Expired : VerificationResult()
    object TooManyAttempts : VerificationResult()
    object AlreadyVerified : VerificationResult()
}

sealed class ResendResult {
    object Success : ResendResult()
    data class RateLimited(val canResendAt: Instant) : ResendResult()
}

// Mapper
fun EmailVerificationEntity.toDomain(): EmailVerification = EmailVerification(
    id = this.id,
    userId = this.user.id,
    expiresAt = this.expiresAt,
    verifiedAt = this.verifiedAt,
    attempts = this.attempts,
    maxAttempts = this.maxAttempts,
    canRetry = this.canRetry(),
    isExpired = this.isExpired(),
    isVerified = this.isVerified()
)
```

**Assigned To**: Backend Developer
**Estimated Time**: 1 hour
**Priority**: Medium
**Depends On**: Task 1.1

---

### Task 1.4: Implement EmailVerificationService

**Description**: Core service for email verification business logic

**Acceptance Criteria**:
- [ ] Create `EmailVerificationService.kt`
- [ ] Implement `initiateVerification()` method
- [ ] Implement `verifyWithCode()` method
- [ ] Implement `verifyWithToken()` method
- [ ] Implement `resendVerification()` method
- [ ] Implement `completeVerification()` (transactional)
- [ ] Add secure code generation using `SecureRandom`
- [ ] Add code/token hashing using `PasswordEncoder`
- [ ] Add rate limiting logic
- [ ] Add audit logging
- [ ] Add comprehensive documentation
- [ ] Handle all edge cases (expired, max attempts, etc.)
- [ ] Follow service pattern

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/EmailVerificationService.kt`

**Pattern Reference**: [05-backend/patterns/service-pattern.md]

**Full Implementation**: See [email-verification-design.md](email-verification-design.md) Section "Backend Implementation" → "EmailVerificationService.kt"

**Key Methods**:
```kotlin
@Singleton
class EmailVerificationService(/* dependencies */) {
    fun initiateVerification(userId: UUID, userEmail: String, locale: String): EmailVerification

    fun verifyWithCode(userId: UUID, code: String, ipAddress: String): VerificationResult

    fun verifyWithToken(token: UUID, ipAddress: String): VerificationResult

    fun resendVerification(userId: UUID, userEmail: String, locale: String): ResendResult

    @Transactional
    private fun completeVerification(userId: UUID, verification: EmailVerificationEntity, ipAddress: String): VerificationResult

    private fun generateSecureCode(): String

    private fun calculateNextResendTime(userId: UUID): Instant?
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 6 hours
**Priority**: Critical
**Depends On**: Task 1.2, Task 1.3

---

### Task 1.5: Update AuthenticationService

**Description**: Update authentication service to trigger verification on signup

**Acceptance Criteria**:
- [ ] Modify `signUp()` method to call `initiateVerification()`
- [ ] Set new users to `email_verified = false`
- [ ] Still issue JWT token (for limited access)
- [ ] Return flag indicating verification required
- [ ] Add audit logging for signup

**Files to Update**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AuthenticationService.kt`

**Code Changes**:
```kotlin
@Transactional
fun signUp(input: SignUpInput): SignUpResult {
    // Existing validation...

    // Create user (unverified)
    val user = UserEntity(
        id = UuidV7.generate(),
        email = input.email,
        passwordHash = passwordEncoder.encode(input.password),
        emailVerified = false  // NEW
    )

    userRepository.save(user)

    // Create principal (enabled for limited access)
    createPrincipal(user)

    // Initiate email verification (NEW)
    emailVerificationService.initiateVerification(
        userId = user.id,
        userEmail = user.email,
        locale = input.locale ?: "en-US"
    )

    // Generate access token (limited permissions)
    val accessToken = jwtTokenIssuer.generateAccessToken(user)

    return SignUpResult(
        user = user.toDomain(),
        accessToken = accessToken,
        requiresVerification = true  // NEW
    )
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 2 hours
**Priority**: High
**Depends On**: Task 1.4

---

### Task 1.6: Unit Tests for EmailVerificationService

**Description**: Comprehensive unit tests for service layer

**Acceptance Criteria**:
- [ ] Test code generation (format, uniqueness, security)
- [ ] Test token generation (UUID v7 format)
- [ ] Test successful verification with valid code
- [ ] Test verification failure with invalid code
- [ ] Test verification failure after expiration
- [ ] Test verification failure after max attempts
- [ ] Test successful verification with valid token
- [ ] Test token verification failure scenarios
- [ ] Test resend invalidates previous codes
- [ ] Test resend respects rate limit
- [ ] Test concurrent verification attempts
- [ ] Test audit logging
- [ ] Test transactional behavior
- [ ] Achieve 100% code coverage for service

**Files to Create**:
- `service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/service/EmailVerificationServiceTest.kt`

**Test Examples**:
```kotlin
@MicronautTest
class EmailVerificationServiceTest {

    @Inject
    lateinit var emailVerificationService: EmailVerificationService

    @Test
    fun `generate code creates 6-digit numeric code`() {
        val code = emailVerificationService.generateSecureCode()
        assertThat(code).matches("\\d{6}")
        assertThat(code.toInt()).isBetween(0, 999999)
    }

    @Test
    fun `code verification succeeds with valid code`() {
        val verification = emailVerificationService.initiateVerification(userId, email, "en-US")
        // Mock: get plaintext code from email service
        val result = emailVerificationService.verifyWithCode(userId, validCode, "127.0.0.1")

        assertThat(result).isInstanceOf(VerificationResult.Success::class.java)
    }

    @Test
    fun `code verification fails with invalid code`() {
        emailVerificationService.initiateVerification(userId, email, "en-US")

        val result = emailVerificationService.verifyWithCode(userId, "999999", "127.0.0.1")

        assertThat(result).isInstanceOf(VerificationResult.InvalidCode::class.java)
        assertThat((result as VerificationResult.InvalidCode).attemptsRemaining).isEqualTo(4)
    }

    // ... more tests
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 4 hours
**Priority**: High
**Depends On**: Task 1.4

---

## Phase 2: Email Integration (2 days)

### Task 2.1: Create Email Templates

**Description**: Create Freemarker templates for verification emails

**Acceptance Criteria**:
- [ ] Create verification email template (HTML + plain text)
- [ ] Create deletion warning email template
- [ ] Include 6-digit code prominently in design
- [ ] Include magic link as alternative
- [ ] Include clear expiration time
- [ ] Include support contact information
- [ ] Follow brand guidelines
- [ ] Test rendering with sample data
- [ ] Ensure mobile-responsive design

**Files to Create**:
- `service/kotlin/comms/src/main/resources/templates/email/verify-email.ftl`
- `service/kotlin/comms/src/main/resources/templates/email/verify-email.txt.ftl` (plain text fallback)
- `service/kotlin/comms/src/main/resources/templates/email/account-deletion-warning.ftl`

**Template Variables**:
```freemarker
${userName}
${verificationCode}
${verificationLink}
${expirationHours}
${supportEmail}
${logoUrl}
```

**Design Reference**: See [email-verification-design.md](email-verification-design.md) Section "Email Template"

**Assigned To**: Frontend Developer / Designer
**Estimated Time**: 4 hours
**Priority**: High
**Depends On**: None

---

### Task 2.2: Add i18n Translations for Email Templates

**Description**: Create translations for email content

**Acceptance Criteria**:
- [ ] Create `en-US` translations
- [ ] Create `pt-BR` translations
- [ ] Translate email subject lines
- [ ] Translate email body content
- [ ] Test email rendering in both languages

**Files to Create/Update**:
- `service/kotlin/comms/src/main/resources/i18n/email_en_US.properties`
- `service/kotlin/comms/src/main/resources/i18n/email_pt_BR.properties`

**Translation Keys**:
```properties
# en-US
email.verification.subject=Verify your email - NeoTool
email.verification.title=Verify Your Email Address
email.verification.greeting=Hi {0},
email.verification.intro=Thanks for signing up for NeoTool! To complete your registration and unlock all features, please verify your email address.
email.verification.code.label=Your verification code:
email.verification.code.expiry=This code expires in {0} hours
email.verification.link.label=Alternatively, you can click this button to verify:
email.verification.link.button=Verify Email Address
email.verification.footer=If you didn't create an account with NeoTool, you can safely ignore this email.
email.verification.support=Need help? Contact us at {0}

# pt-BR
email.verification.subject=Verifique seu e-mail - NeoTool
email.verification.title=Verifique Seu Endereço de E-mail
# ... (full translations)
```

**Assigned To**: Content Writer / Translator
**Estimated Time**: 2 hours
**Priority**: Medium
**Depends On**: Task 2.1

---

### Task 2.3: Create Email Sending Service Integration

**Description**: Integrate with Comms module to send verification emails

**Acceptance Criteria**:
- [ ] Create `VerificationEmailService.kt` in Security module
- [ ] Interface with Comms module via Kafka events
- [ ] Support template rendering with variables
- [ ] Support locale-based template selection
- [ ] Handle email delivery failures gracefully
- [ ] Add retry logic
- [ ] Log email sending attempts

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/VerificationEmailService.kt`

**Code Template**:
```kotlin
@Singleton
class VerificationEmailService(
    private val emailProducer: KafkaEmailProducer,
    private val templateRenderer: TemplateRenderer,
    @Value("\${app.verification.verification-link-base-url}")
    private val verificationLinkBaseUrl: String
) {

    fun sendVerificationEmail(
        to: String,
        userName: String,
        code: String,
        token: UUID,
        expiresAt: Instant,
        locale: String
    ) {
        val verificationLink = "$verificationLinkBaseUrl?token=$token"
        val expirationHours = ChronoUnit.HOURS.between(Instant.now(), expiresAt)

        val emailRequest = EmailSendRequest(
            to = to,
            template = "email/verify-email",
            locale = locale,
            variables = mapOf(
                "userName" to userName,
                "verificationCode" to code,
                "verificationLink" to verificationLink,
                "expirationHours" to expirationHours.toString(),
                "supportEmail" to "support@neotool.io",
                "logoUrl" to "https://neotool.io/logo.png"
            )
        )

        // Publish to Kafka (Comms module handles delivery)
        emailProducer.send(emailRequest)

        logger.info("Verification email queued for $to")
    }

    fun sendDeletionWarning(to: String, userName: String) {
        val emailRequest = EmailSendRequest(
            to = to,
            template = "email/account-deletion-warning",
            locale = "en-US",  // TODO: Get user's locale
            variables = mapOf(
                "userName" to userName,
                "deletionDate" to Instant.now().plus(Duration.ofDays(1)).toString()
            )
        )

        emailProducer.send(emailRequest)
    }
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 3 hours
**Priority**: High
**Depends On**: Task 2.1

---

### Task 2.4: Test Email Delivery End-to-End

**Description**: Test complete email flow from trigger to delivery

**Acceptance Criteria**:
- [ ] Test email sent on signup
- [ ] Test email received in inbox
- [ ] Test email rendering (HTML and plain text)
- [ ] Test magic link URL correctness
- [ ] Test code display formatting
- [ ] Test email delivery to different providers (Gmail, Outlook, etc.)
- [ ] Test spam score (should pass spam filters)
- [ ] Test i18n (both en-US and pt-BR)
- [ ] Document any email client rendering issues

**Testing Tools**:
- MailHog (local testing)
- Mailtrap (staging testing)
- Real email accounts (production validation)
- Email on Acid / Litmus (cross-client testing)

**Assigned To**: QA Engineer
**Estimated Time**: 3 hours
**Priority**: High
**Depends On**: Task 2.3

---

## Phase 3: GraphQL API Layer (2 days)

### Task 3.1: Update GraphQL Schema

**Description**: Add email verification mutations and queries to GraphQL schema

**Acceptance Criteria**:
- [ ] Add `verifyEmail` mutation
- [ ] Add `verifyEmailWithToken` mutation
- [ ] Add `resendVerificationEmail` mutation
- [ ] Add `myVerificationStatus` query
- [ ] Add `adminVerifyUserEmail` mutation (admin-only)
- [ ] Update `SignUpPayload` to include `requiresVerification` field
- [ ] Update `User` type to include `emailVerified` and `emailVerifiedAt` fields
- [ ] Add input/output types
- [ ] Add documentation to schema

**Files to Update**:
- `contracts/graphql/subgraphs/security/schema.graphqls`

**Schema Additions**:
```graphql
# Mutations
extend type Mutation {
    """
    Verify email address using 6-digit code sent via email.
    """
    verifyEmail(code: String!): VerifyEmailPayload!

    """
    Verify email address using magic link token (alternative to code).
    """
    verifyEmailWithToken(token: String!): VerifyEmailPayload!

    """
    Resend verification email with new code. Rate-limited to 3 per hour.
    """
    resendVerificationEmail: ResendVerificationEmailPayload!

    """
    Admin-only: Manually verify a user's email (with audit log).
    """
    adminVerifyUserEmail(userId: ID!): AdminVerifyEmailPayload!
    @requiresPermission(permission: "security:user:admin")
}

# Queries
extend type Query {
    """
    Get current user's verification status.
    """
    myVerificationStatus: VerificationStatus!
}

# Types
type VerifyEmailPayload {
    success: Boolean!
    user: User
    message: String
    attemptsRemaining: Int
}

type ResendVerificationEmailPayload {
    success: Boolean!
    message: String
    canResendAt: DateTime
}

type AdminVerifyEmailPayload {
    success: Boolean!
    user: User
}

type VerificationStatus {
    emailVerified: Boolean!
    emailVerifiedAt: DateTime
    verificationCodeSentAt: DateTime
    verificationCodeExpiresAt: DateTime
    canResendCode: Boolean!
    nextResendAvailableAt: DateTime
}

# Updated types
type User {
    # ... existing fields
    emailVerified: Boolean!
    emailVerifiedAt: DateTime
}

type SignUpPayload {
    user: User
    accessToken: String!
    refreshToken: String!
    requiresVerification: Boolean!  # NEW
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 2 hours
**Priority**: High
**Depends On**: None

---

### Task 3.2: Implement GraphQL Resolvers

**Description**: Implement resolvers for email verification operations

**Acceptance Criteria**:
- [ ] Create `EmailVerificationResolver.kt`
- [ ] Implement `verifyEmail` resolver
- [ ] Implement `verifyEmailWithToken` resolver
- [ ] Implement `resendVerificationEmail` resolver
- [ ] Implement `myVerificationStatus` resolver
- [ ] Implement `adminVerifyUserEmail` resolver (with permission check)
- [ ] Extract client IP address for audit logging
- [ ] Handle all error cases gracefully
- [ ] Return user-friendly error messages
- [ ] Follow GraphQL resolver pattern

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolvers/EmailVerificationResolver.kt`

**Pattern Reference**: [05-backend/patterns/graphql-resolver-pattern.md]

**Full Implementation**: See [email-verification-design.md](email-verification-design.md) Section "GraphQL Resolvers"

**Assigned To**: Backend Developer
**Estimated Time**: 4 hours
**Priority**: High
**Depends On**: Task 3.1, Task 1.4

---

### Task 3.3: Update SignUp Resolver

**Description**: Update signup resolver to return verification requirement

**Acceptance Criteria**:
- [ ] Update `signUp` resolver to return `requiresVerification = true`
- [ ] Ensure user created with `emailVerified = false`
- [ ] Ensure JWT token still issued (for limited access)

**Files to Update**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolvers/AuthenticationResolver.kt`

**Code Changes**:
```kotlin
@GraphQLMutation
fun signUp(input: SignUpInput): SignUpPayload {
    val result = authenticationService.signUp(input)

    return SignUpPayload(
        user = result.user,
        accessToken = result.accessToken,
        refreshToken = result.refreshToken,
        requiresVerification = result.requiresVerification  // NEW
    )
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 1 hour
**Priority**: High
**Depends On**: Task 1.5

---

### Task 3.4: Add REST Endpoint for Magic Links

**Description**: Add REST endpoint to handle magic link redirects from email

**Acceptance Criteria**:
- [ ] Create `GET /api/auth/verify-email` endpoint
- [ ] Extract token from query parameter
- [ ] Call `verifyEmailWithToken` internally
- [ ] Redirect to frontend with status
- [ ] Handle errors gracefully (redirect to error page)

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/EmailVerificationController.kt`

**Code Template**:
```kotlin
@Controller("/api/auth")
class EmailVerificationController(
    private val emailVerificationService: EmailVerificationService
) {

    @Get("/verify-email")
    fun verifyEmailViaLink(
        @QueryValue token: String,
        @Header("X-Forwarded-For") clientIp: String?
    ): HttpResponse<*> {
        val tokenUuid = try {
            UUID.fromString(token)
        } catch (e: IllegalArgumentException) {
            return HttpResponse.redirect(
                URI.create("/verify-email-link?status=invalid_token")
            )
        }

        val ipAddress = clientIp ?: "unknown"

        return when (emailVerificationService.verifyWithToken(tokenUuid, ipAddress)) {
            is VerificationResult.Success -> {
                HttpResponse.redirect(URI.create("/verify-email-link?status=success"))
            }
            else -> {
                HttpResponse.redirect(URI.create("/verify-email-link?status=error"))
            }
        }
    }
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 1 hour
**Priority**: Medium
**Depends On**: Task 1.4

---

### Task 3.5: Generate TypeScript Types

**Description**: Generate TypeScript types from updated GraphQL schema

**Acceptance Criteria**:
- [ ] Run `npm run codegen` in web directory
- [ ] Verify new types generated for mutations/queries
- [ ] Commit generated types

**Commands**:
```bash
cd web
npm run codegen
```

**Files Updated** (auto-generated):
- `web/src/lib/graphql/types/__generated__/graphql.ts`
- `web/src/lib/graphql/operations/security/mutations.generated.ts`

**Assigned To**: Backend Developer
**Estimated Time**: 0.5 hours
**Priority**: High
**Depends On**: Task 3.1

---

### Task 3.6: Integration Tests for GraphQL API

**Description**: Test GraphQL resolvers end-to-end

**Acceptance Criteria**:
- [ ] Test `signUp` mutation creates unverified user
- [ ] Test `verifyEmail` mutation with valid code
- [ ] Test `verifyEmail` mutation with invalid code
- [ ] Test `verifyEmail` mutation with expired code
- [ ] Test `verifyEmailWithToken` mutation
- [ ] Test `resendVerificationEmail` mutation
- [ ] Test rate limiting on resend
- [ ] Test `myVerificationStatus` query
- [ ] Test admin verification mutation

**Files to Create**:
- `service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/graphql/EmailVerificationIntegrationTest.kt`

**Assigned To**: Backend Developer / QA Engineer
**Estimated Time**: 4 hours
**Priority**: High
**Depends On**: Task 3.2

---

## Phase 4: Frontend Implementation (3 days)

### Task 4.1: Create GraphQL Operations

**Description**: Create GraphQL operation files for email verification

**Acceptance Criteria**:
- [ ] Create `verifyEmail.graphql` mutation
- [ ] Create `verifyEmailWithToken.graphql` mutation
- [ ] Create `resendVerificationEmail.graphql` mutation
- [ ] Create `myVerificationStatus.graphql` query
- [ ] Run codegen to generate hooks
- [ ] Test generated hooks

**Files to Create**:
- `web/src/lib/graphql/operations/security/verify-email.graphql`
- `web/src/lib/graphql/operations/security/verify-email-with-token.graphql`
- `web/src/lib/graphql/operations/security/resend-verification-email.graphql`
- `web/src/lib/graphql/operations/security/my-verification-status.graphql`

**Pattern Reference**: [07-frontend/patterns/graphql-mutation-pattern.md]

**Example**:
```graphql
# verify-email.graphql
mutation VerifyEmail($code: String!) {
  verifyEmail(code: $code) {
    success
    message
    attemptsRemaining
    user {
      id
      email
      emailVerified
      emailVerifiedAt
    }
  }
}
```

**Assigned To**: Frontend Developer
**Estimated Time**: 1 hour
**Priority**: High
**Depends On**: Task 3.5

---

### Task 4.2: Create Verification Prompt Component

**Description**: Create the main email verification screen/component

**Route**: `/verify-email`

**Acceptance Criteria**:
- [ ] Create `VerificationPrompt.tsx` component
- [ ] Create 6-digit code input component
- [ ] Add form validation
- [ ] Call `verifyEmail` mutation on submit
- [ ] Show success/error messages
- [ ] Show attempts remaining on error
- [ ] Add "Resend code" button
- [ ] Add "Skip for now" option
- [ ] Add loading states
- [ ] Make responsive (mobile-friendly)
- [ ] Add i18n support

**Files to Create**:
- `web/src/app/(authentication)/verify-email/page.tsx`
- `web/src/shared/components/auth/VerificationPrompt.tsx`
- `web/src/shared/components/auth/CodeInput.tsx`

**Pattern Reference**: [07-frontend/patterns/shared-components-pattern.md]

**Component Structure**:
```tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useVerifyEmailMutation } from '@/lib/graphql/operations/security/mutations.generated';
import { CodeInput } from './CodeInput';
import { Button } from '@/shared/ui/button';

export function VerificationPrompt() {
  const router = useRouter();
  const [code, setCode] = useState('');
  const [verifyEmail, { loading, error }] = useVerifyEmailMutation();

  const handleSubmit = async () => {
    const result = await verifyEmail({ variables: { code } });

    if (result.data?.verifyEmail.success) {
      // Show success message
      router.push('/home');
    } else {
      // Show error with attempts remaining
    }
  };

  return (
    <div className="verification-prompt">
      <h1>Verify your email address</h1>
      <p>We sent a 6-digit code to {userEmail}</p>

      <CodeInput
        length={6}
        value={code}
        onChange={setCode}
        onComplete={handleSubmit}
      />

      <Button onClick={handleSubmit} disabled={loading || code.length < 6}>
        Verify Email
      </Button>

      <ResendButton />
      <SkipButton />
    </div>
  );
}
```

**Assigned To**: Frontend Developer
**Estimated Time**: 4 hours
**Priority**: High
**Depends On**: Task 4.1

---

### Task 4.3: Create Magic Link Landing Page

**Description**: Create landing page for magic link verification

**Route**: `/verify-email-link`

**Acceptance Criteria**:
- [ ] Extract token from URL query parameter
- [ ] Call `verifyEmailWithToken` mutation automatically
- [ ] Show loading state while verifying
- [ ] Show success message on success
- [ ] Show error message on failure
- [ ] Auto-redirect to home after 3 seconds on success
- [ ] Provide "Resend code" option on failure

**Files to Create**:
- `web/src/app/(authentication)/verify-email-link/page.tsx`

**Code Template**:
```tsx
'use client';

import { useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useVerifyEmailWithTokenMutation } from '@/lib/graphql/operations/security/mutations.generated';

export default function VerifyEmailLinkPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const token = searchParams.get('token');
  const [verifyEmail, { loading, data, error }] = useVerifyEmailWithTokenMutation();

  useEffect(() => {
    if (token) {
      verifyEmail({ variables: { token } });
    }
  }, [token, verifyEmail]);

  useEffect(() => {
    if (data?.verifyEmailWithToken.success) {
      // Auto-redirect after 3 seconds
      setTimeout(() => router.push('/home'), 3000);
    }
  }, [data, router]);

  if (loading) {
    return <LoadingSpinner />;
  }

  if (data?.verifyEmailWithToken.success) {
    return (
      <SuccessMessage>
        Email verified successfully! Redirecting...
      </SuccessMessage>
    );
  }

  return (
    <ErrorMessage>
      Verification failed. Please try again.
      <ResendButton />
    </ErrorMessage>
  );
}
```

**Assigned To**: Frontend Developer
**Estimated Time**: 2 hours
**Priority**: Medium
**Depends On**: Task 4.1

---

### Task 4.4: Create Verification Banner Component

**Description**: Create persistent banner shown to unverified users

**Acceptance Criteria**:
- [ ] Create `VerificationBanner.tsx` component
- [ ] Add to main layout (shown on all pages)
- [ ] Only show for unverified users
- [ ] Add "Verify Now" CTA button
- [ ] Add dismiss button (dismissible for 24 hours)
- [ ] Store dismiss state in localStorage
- [ ] Make non-intrusive but noticeable
- [ ] Make responsive

**Files to Create**:
- `web/src/shared/components/auth/VerificationBanner.tsx`

**Files to Update**:
- `web/src/app/layout.tsx` (add banner)

**Code Template**:
```tsx
'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/shared/providers/AuthProvider';
import { Alert, AlertDescription } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { X } from 'lucide-react';

export function VerificationBanner() {
  const { currentUser } = useAuth();
  const router = useRouter();
  const [isDismissed, setIsDismissed] = useState(false);

  // Check localStorage for dismissal
  useEffect(() => {
    const dismissedUntil = localStorage.getItem('verificationBannerDismissed');
    if (dismissedUntil && new Date(dismissedUntil) > new Date()) {
      setIsDismissed(true);
    }
  }, []);

  // Don't show if user is verified or banner is dismissed
  if (!currentUser || currentUser.emailVerified || isDismissed) {
    return null;
  }

  const handleDismiss = () => {
    const dismissUntil = new Date(Date.now() + 24 * 60 * 60 * 1000); // 24 hours
    localStorage.setItem('verificationBannerDismissed', dismissUntil.toISOString());
    setIsDismissed(true);
  };

  return (
    <Alert variant="warning" className="verification-banner">
      <AlertDescription>
        Verify your email to unlock all features.
      </AlertDescription>
      <Button onClick={() => router.push('/verify-email')} size="sm">
        Verify Now
      </Button>
      <Button onClick={handleDismiss} variant="ghost" size="sm">
        <X className="h-4 w-4" />
      </Button>
    </Alert>
  );
}
```

**Assigned To**: Frontend Developer
**Estimated Time**: 2 hours
**Priority**: Medium
**Depends On**: None

---

### Task 4.5: Enhance PermissionGate Component

**Description**: Update PermissionGate to support email verification checks

**Acceptance Criteria**:
- [ ] Add `requireVerifiedEmail` prop
- [ ] Check user's `emailVerified` status
- [ ] Show "Verify email" prompt if unverified
- [ ] Allow custom verification prompt message
- [ ] Maintain backward compatibility

**Files to Update**:
- `web/src/shared/components/authorization/PermissionGate.tsx`

**Code Changes**:
```tsx
interface PermissionGateProps {
  require: string;
  requireVerifiedEmail?: boolean;  // NEW
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function PermissionGate({
  require,
  requireVerifiedEmail = false,  // NEW
  children,
  fallback
}: PermissionGateProps) {
  const { hasPermission } = useAuthorization();
  const { currentUser } = useAuth();

  // Check permission
  if (!hasPermission(require)) {
    return fallback || null;
  }

  // NEW: Check email verification
  if (requireVerifiedEmail && !currentUser?.emailVerified) {
    return (
      <UnverifiedEmailPrompt>
        You need to verify your email to access this feature.
        <Button onClick={() => router.push('/verify-email')}>
          Verify Now
        </Button>
      </UnverifiedEmailPrompt>
    );
  }

  return <>{children}</>;
}
```

**Assigned To**: Frontend Developer
**Estimated Time**: 1 hour
**Priority**: Medium
**Depends On**: None

---

### Task 4.6: Update SignUpForm Component

**Description**: Update signup form to handle post-signup verification flow

**Acceptance Criteria**:
- [ ] After successful signup, redirect to `/verify-email`
- [ ] Show brief success message before redirect
- [ ] Store user's email for display on verification screen
- [ ] Handle case where `requiresVerification = false` (should not happen for new users)

**Files to Update**:
- `web/src/shared/components/auth/SignUpForm.tsx`

**Code Changes**:
```tsx
const handleSignUpSuccess = (data: SignUpPayload) => {
  // Store tokens
  storeAuthTokens(data.accessToken, data.refreshToken);

  // Update auth context
  setCurrentUser(data.user);

  // Redirect based on verification requirement
  if (data.requiresVerification) {
    // Store email for verification screen
    sessionStorage.setItem('pendingVerificationEmail', data.user.email);

    // Show success toast
    toast.success('Account created! Please verify your email.');

    // Redirect to verification
    router.push('/verify-email');
  } else {
    // Shouldn't happen for new signups, but handle gracefully
    router.push('/home');
  }
};
```

**Assigned To**: Frontend Developer
**Estimated Time**: 1 hour
**Priority**: High
**Depends On**: Task 4.2

---

### Task 4.7: Add i18n Translations

**Description**: Add frontend translations for verification UI

**Acceptance Criteria**:
- [ ] Add English translations
- [ ] Add Portuguese translations
- [ ] Cover all UI text
- [ ] Test locale switching

**Files to Update**:
- `web/src/app/(authentication)/verify-email/i18n/locales/en.json`
- `web/src/app/(authentication)/verify-email/i18n/locales/pt.json`

**Translations**: See [email-verification-design.md](email-verification-design.md) Section "i18n Translations"

**Assigned To**: Frontend Developer / Content Writer
**Estimated Time**: 2 hours
**Priority**: Medium
**Depends On**: None

---

### Task 4.8: E2E Tests (Playwright)

**Description**: End-to-end tests for verification flow

**Acceptance Criteria**:
- [ ] Test signup redirects to verification prompt
- [ ] Test successful code verification
- [ ] Test invalid code error
- [ ] Test expired code error
- [ ] Test resend functionality
- [ ] Test rate limiting
- [ ] Test magic link verification
- [ ] Test verification banner
- [ ] Test restricted feature access for unverified users
- [ ] Test full access for verified users

**Files to Create**:
- `web/e2e/auth/email-verification.spec.ts`

**Test Examples**: See [email-verification-design.md](email-verification-design.md) Section "E2E Tests"

**Assigned To**: QA Engineer
**Estimated Time**: 6 hours
**Priority**: High
**Depends On**: Task 4.2, Task 4.3, Task 4.4

---

## Phase 5: Authorization & Cleanup (2 days)

### Task 5.1: Create @RequiresVerifiedEmail Annotation

**Description**: Create annotation for methods requiring verified email

**Acceptance Criteria**:
- [ ] Create `@RequiresVerifiedEmail` annotation
- [ ] Create interceptor to enforce verification check
- [ ] Throw custom exception if unverified
- [ ] Add audit logging
- [ ] Test with sample endpoints

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/annotation/RequiresVerifiedEmail.kt`
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/interceptor/VerifiedEmailInterceptor.kt`

**Implementation**: See [email-verification-design.md](email-verification-design.md) Section "Authorization Changes"

**Assigned To**: Backend Developer
**Estimated Time**: 2 hours
**Priority**: Medium
**Depends On**: Task 1.4

---

### Task 5.2: Identify Features Requiring Verification

**Description**: Audit codebase and identify which features should require email verification

**Acceptance Criteria**:
- [ ] Create list of features requiring verification
- [ ] Document rationale for each
- [ ] Get product owner approval
- [ ] Create ticket for each feature to be updated

**Example Features** (requires verification):
- Creating products
- Creating orders
- Modifying account settings
- Accessing payment methods
- Exporting data

**Example Features** (no verification required):
- Viewing products
- Viewing public content
- Updating basic profile (name)
- Logging out

**Deliverable**: Document with feature list and plan

**Assigned To**: Product Owner + Tech Lead
**Estimated Time**: 2 hours
**Priority**: High
**Depends On**: None

---

### Task 5.3: Apply Verification Checks to Features

**Description**: Apply `@RequiresVerifiedEmail` annotation to selected features

**Acceptance Criteria**:
- [ ] Add annotation to identified GraphQL resolvers
- [ ] Add annotation to identified HTTP controllers
- [ ] Update frontend to handle `EmailVerificationRequiredException`
- [ ] Test each protected feature
- [ ] Update feature documentation

**Files to Update** (examples):
- `service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/app/graphql/resolvers/ProductResolver.kt`
- Others as identified in Task 5.2

**Code Example**:
```kotlin
@GraphQLMutation
@RequiresAuthorization(permission = "product:create")
@RequiresVerifiedEmail  // NEW
fun createProduct(input: CreateProductInput): Product {
    // Implementation
}
```

**Assigned To**: Backend Developer
**Estimated Time**: 4 hours
**Priority**: High
**Depends On**: Task 5.1, Task 5.2

---

### Task 5.4: Implement Cleanup Job

**Description**: Scheduled job to delete old unverified accounts

**Acceptance Criteria**:
- [ ] Create `UnverifiedAccountCleanupJob.kt`
- [ ] Run daily at 2 AM UTC
- [ ] Find accounts created > 6 days ago (send warning)
- [ ] Find accounts created > 7 days ago (delete)
- [ ] Send warning emails 24 hours before deletion
- [ ] Soft delete accounts (disable principal)
- [ ] Add comprehensive audit logging
- [ ] Add metrics tracking
- [ ] Test job execution

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/jobs/UnverifiedAccountCleanupJob.kt`

**Implementation**: See [email-verification-design.md](email-verification-design.md) Section "Scheduled Cleanup Job"

**Assigned To**: Backend Developer
**Estimated Time**: 3 hours
**Priority**: Medium
**Depends On**: Task 2.1, Task 2.3

---

### Task 5.5: Create Account Deletion Warning Email Template

**Description**: Email template for warning users before account deletion

**Acceptance Criteria**:
- [ ] Create warning email template
- [ ] Include clear deletion date
- [ ] Include "Verify now" CTA with verification link
- [ ] Include support contact
- [ ] Test rendering
- [ ] Add i18n translations

**Files to Create**:
- `service/kotlin/comms/src/main/resources/templates/email/account-deletion-warning.ftl`

**Template Structure**:
```html
<h1>Your NeoTool account will be deleted soon</h1>
<p>Hi ${userName},</p>
<p>Your NeoTool account was created on ${createdDate}, but your email address has not been verified.</p>
<p><strong>Your account will be automatically deleted on ${deletionDate} if you don't verify your email.</strong></p>
<a href="${verificationLink}">Verify Your Email Now</a>
<p>If you no longer want this account, you can ignore this email and it will be deleted automatically.</p>
```

**Assigned To**: Frontend Developer / Designer
**Estimated Time**: 1 hour
**Priority**: Medium
**Depends On**: None

---

### Task 5.6: Add Metrics and Monitoring

**Description**: Add metrics for monitoring verification flow

**Acceptance Criteria**:
- [ ] Add Prometheus metrics (see design doc)
- [ ] Add Grafana dashboard
- [ ] Configure alerts
- [ ] Test metrics collection
- [ ] Document metrics

**Metrics** (from design doc):
- `email_verification_initiated_total`
- `email_verification_completed_total`
- `email_verification_failed_total`
- `verification_completion_rate`
- `unverified_accounts_deleted_total`
- (See full list in design doc)

**Files to Create**:
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/metrics/EmailVerificationMetrics.kt`
- Grafana dashboard JSON

**Assigned To**: DevOps Engineer / Backend Developer
**Estimated Time**: 3 hours
**Priority**: Medium
**Depends On**: Task 1.4

---

## Phase 6: Testing, Documentation & Production (3 days)

### Task 6.1: Manual Testing Checklist

**Description**: Comprehensive manual testing before production

**Checklist**: See [email-verification-design.md](email-verification-design.md) Section "Manual Testing Checklist"

**Assigned To**: QA Lead
**Estimated Time**: 4 hours
**Priority**: Critical
**Depends On**: All implementation tasks

---

### Task 6.2: Load Testing

**Description**: Test verification flow under load

**Acceptance Criteria**:
- [ ] Simulate 1000 concurrent signups
- [ ] Verify email delivery performance
- [ ] Test database performance (code lookups)
- [ ] Test rate limiting behavior under load
- [ ] Verify no deadlocks or race conditions
- [ ] Document performance benchmarks

**Tools**: JMeter, k6, or Gatling

**Assigned To**: QA Engineer / Performance Engineer
**Estimated Time**: 3 hours
**Priority**: High
**Depends On**: All implementation tasks

---

### Task 6.3: Security Audit

**Description**: Security review of verification implementation

**Acceptance Criteria**:
- [ ] Review code generation (SecureRandom usage)
- [ ] Review hashing implementation
- [ ] Review rate limiting
- [ ] Review SQL injection risks
- [ ] Review XSS risks in email templates
- [ ] Review CSRF protection
- [ ] Test brute force attack scenarios
- [ ] Verify OWASP compliance
- [ ] Document findings and remediations

**Assigned To**: Security Engineer
**Estimated Time**: 4 hours
**Priority**: Critical
**Depends On**: All implementation tasks

---

### Task 6.4: Update Feature Documentation

**Description**: Update all relevant documentation

**Acceptance Criteria**:
- [ ] Update Security README
- [ ] Update Authentication README
- [ ] Create user guide for email verification
- [ ] Create troubleshooting guide
- [ ] Update API documentation
- [ ] Update GraphQL schema documentation
- [ ] Add examples to docs

**Files to Update**:
- `docs/03-features/security/README.md`
- `docs/03-features/security/authentication/README.md`

**Files to Create**:
- `docs/03-features/security/authentication/email-verification-user-guide.md`
- `docs/03-features/security/authentication/email-verification-troubleshooting.md`

**Assigned To**: Technical Writer / Developer
**Estimated Time**: 4 hours
**Priority**: Medium
**Depends On**: All implementation tasks

---

### Task 6.5: Migration Script for Existing Users

**Description**: Create and test migration script to grandfather existing users

**Acceptance Criteria**:
- [ ] Create SQL migration script
- [ ] Mark all existing users as verified
- [ ] Set `email_verified_at` to account creation date
- [ ] Add audit log entries
- [ ] Test on staging database
- [ ] Document rollback procedure

**Files to Create**:
- `service/kotlin/security/src/main/resources/db/migration/V0_XX__grandfather_existing_users.sql`

**Script**:
```sql
-- Mark all existing users as verified (before feature launch)
UPDATE users
SET email_verified = true,
    email_verified_at = created_at
WHERE created_at < '2026-02-15 00:00:00'  -- Feature launch date
  AND email_verified IS FALSE;

-- Audit log
INSERT INTO audit_logs (event, details, created_at)
VALUES ('GRANDFATHER_EXISTING_USERS', '{"count": (SELECT COUNT(*) FROM users WHERE email_verified = true), "reason": "Email verification feature launch"}', NOW());
```

**Assigned To**: Database Administrator / Backend Developer
**Estimated Time**: 2 hours
**Priority**: High
**Depends On**: Task 0.3

---

## Summary

### Total Estimated Time

| Phase | Duration | Tasks |
|-------|----------|-------|
| Phase 1: Backend Foundation | 3 days | 6 tasks |
| Phase 2: Email Integration | 2 days | 4 tasks |
| Phase 3: GraphQL API Layer | 2 days | 6 tasks |
| Phase 4: Frontend Implementation | 3 days | 8 tasks |
| Phase 5: Authorization & Cleanup | 2 days | 6 tasks |

**Team**: 2-3 developers + 1 QA + 1 DevOps = 3 weeks calendar time

---

## Dependencies Graph

```
Phase 0 (Planning)
  ├─> Phase 1 (Backend) ──┬─> Phase 3 (GraphQL API) ──┬─> Phase 4 (Frontend)
  │                       │                            │
  └─> Phase 2 (Email) ────┴────────────────────────────┴─> Phase 5 (Auth & Cleanup)
                                                           │
                                                           └─> Phase 6 (Production)
```

---

## Related Documentation

- [Email Verification Design Document](email-verification-design.md)
- [Signup Feature File](signup.feature)
- [Feature Development Workflow](../../../08-workflows/feature-development.md)
- [Security Module Documentation](../../README.md)

---

**Version**: 1.0.0 (2026-02-02)
**Status**: Draft - Ready for Sprint Planning
**Next Step**: Review with team, estimate tasks, assign to sprints
