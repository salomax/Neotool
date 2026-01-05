---
title: AI Guardrails - Non-Negotiable Rules
type: ai-guide
category: guardrails
status: current
version: 1.0.0
date: 2026-01-04
tags: [ai, llm, guardrails, security, testing, mandatory]
---

# AI Guardrails - Non-Negotiable Rules

> **Purpose**: Define MANDATORY rules that AI must NEVER violate when implementing features.

## ⚠️ Critical Notice

**These rules are NON-NEGOTIABLE.**

- ✅ AI **MUST** follow these rules for ALL implementations
- ❌ AI **CANNOT** skip or bypass these rules
- 🛑 If implementation violates guardrails → **STOP and ask for clarification**
- 📋 If rule is unclear → **Ask user, don't guess**

**Priority**: Guardrails > Spec > Patterns > Code Examples

---

## 1. Security Guardrails

### 1.1 Authentication

#### Rule: JWT Token Requirements

**MUST use:**
- RS256 (RSA-SHA256) algorithm for JWT tokens
- Never HS256 in production (acceptable in development only)

**Token Expiration (MANDATORY):**
- Access tokens: **15 minutes maximum** (default: 900 seconds)
- Refresh tokens: **7 days maximum** (default: 604800 seconds)

**Key Management:**
- **Production**: ALL JWT signing keys **MUST** be stored in HashiCorp Vault
  - Path: `secret/jwt/keys/{keyId}`
  - Never hardcode keys in code or config files
- **Development**: File-based keys or environment variables allowed

**Implementation:**
```kotlin
// ✅ Correct
val tokenConfig = JwtConfig(
    accessTokenExpiry = 900,   // 15 minutes
    refreshTokenExpiry = 604800 // 7 days
)

// ❌ Wrong
val tokenConfig = JwtConfig(
    accessTokenExpiry = 86400 // 24 hours - TOO LONG
)
```

**If violated**: Application security compromised, tokens too long-lived

---

#### Rule: Token Claims Structure

**Access token MUST include:**
```json
{
  "sub": "user-uuid",           // ✅ Required
  "email": "user@example.com",  // ✅ Required
  "type": "access",             // ✅ Required
  "permissions": [],            // ✅ Required (even if empty)
  "iat": timestamp,             // ✅ Required
  "exp": timestamp,             // ✅ Required
  "iss": "neotool-security-service" // ✅ Required
}
```

**Refresh token MUST include:**
```json
{
  "sub": "user-uuid",    // ✅ Required
  "type": "refresh",     // ✅ Required
  "iat": timestamp,      // ✅ Required
  "exp": timestamp,      // ✅ Required
  "iss": "neotool-security-service" // ✅ Required
}
```

**If violated**: Token validation fails, authorization breaks

---

#### Rule: Principal Status Validation

**MUST check principal status BEFORE token issuance:**

```kotlin
// ✅ Correct
fun authenticate(email: String, password: String): AuthToken? {
    val principal = principalRepository.findByEmail(email) ?: return null

    // MUST check enabled status
    if (!principal.enabled) {
        return null // or throw DisabledException
    }

    // Continue with authentication
}

// ❌ Wrong - Missing status check
fun authenticate(email: String, password: String): AuthToken? {
    val principal = principalRepository.findByEmail(email) ?: return null
    // No enabled check - VIOLATION
}
```

**If violated**: Disabled users can authenticate and obtain tokens

---

### 1.2 Authorization

#### Rule: Deny-by-Default Model

**MANDATORY**: All endpoints and resources are protected unless explicitly made public.

```kotlin
// ✅ Correct - All endpoints have @RequiresAuthorization
@Controller("/users")
class UserController {

    @RequiresAuthorization("security:user:view")
    @Get("/{id}")
    fun getUser(id: UUID): User { ... }

    @RequiresAuthorization("security:user:save")
    @Post
    fun createUser(input: CreateUserInput): User { ... }
}

// ❌ Wrong - Missing @RequiresAuthorization
@Controller("/users")
class UserController {

    @Get("/{id}") // VIOLATION - No authorization check
    fun getUser(id: UUID): User { ... }
}
```

**If violated**: Unauthorized access to protected resources

---

#### Rule: Permission Format

**MUST follow format:** `{module}:{entity}:{action}`

**Valid examples:**
- `security:user:view`
- `security:user:edit`
- `asset:create:own`
- `team:update:own`

**Rules:**
- Lowercase only
- Max 255 characters
- Pattern: `^[a-z0-9_-]+:[a-z0-9_-]+:[a-z0-9_-]+$`

```kotlin
// ✅ Correct
@RequiresAuthorization("security:user:view")

// ❌ Wrong - Invalid format
@RequiresAuthorization("UserView")          // No colons
@RequiresAuthorization("SECURITY:USER:VIEW") // Uppercase
@RequiresAuthorization("security-user-view") // Dashes instead of colons
```

**If violated**: Authorization checks fail, permissions not validated

---

#### Rule: Frontend Authorization is UX Only

**CRITICAL**: Frontend permission checks **NEVER** enforce security.

```typescript
// ✅ Correct - Frontend for UX only
function ProfilePage() {
    const { hasPermission } = useAuthorization();

    return (
        <div>
            {hasPermission('profile:edit:own') && (
                <EditButton /> // Hidden if no permission (UX)
            )}
        </div>
    );
}
// Backend STILL enforces authorization regardless

// ❌ Wrong - Relying on frontend for security
function ProfilePage() {
    const { hasPermission } = useAuthorization();

    if (!hasPermission('profile:edit:own')) {
        return <div>Not authorized</div>; // VIOLATION - No backend check
    }

    // Allows unauthorized mutation if bypassed
    updateProfile(data);
}
```

**If violated**: Security bypass via frontend manipulation

**Solution**: ALWAYS enforce authorization on backend resolvers/controllers

---

### 1.3 Input Validation

#### Rule: Parameterized Queries Only

**NEVER use string concatenation for SQL queries.**

```kotlin
// ✅ Correct - Parameterized query
@Repository
interface UserRepository : JpaRepository<User, UUID> {
    @Query("SELECT u FROM User u WHERE u.email = :email")
    fun findByEmail(@Param("email") email: String): User?
}

// ❌ Wrong - String concatenation (SQL INJECTION RISK)
fun findByEmail(email: String): User? {
    val query = "SELECT * FROM users WHERE email = '$email'" // VIOLATION
    return entityManager.createNativeQuery(query).singleResult
}
```

**If violated**: **SQL injection vulnerability** - attackers can execute arbitrary SQL

---

#### Rule: Validate All API Inputs

**MUST validate at API boundary (GraphQL resolvers, REST controllers).**

```kotlin
// ✅ Correct - Input validation with annotations
data class CreateUserInput(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    val password: String
)

@Mutation
fun createUser(@Valid input: CreateUserInput): User {
    // Input already validated by framework
}

// ❌ Wrong - No validation
data class CreateUserInput(
    val email: String, // VIOLATION - No validation
    val password: String
)
```

**If violated**: Invalid data persisted, potential injection attacks

---

#### Rule: XSS Prevention

**Frontend (React):**
- React **automatically** escapes JSX content (built-in protection)
- **NEVER** use `dangerouslySetInnerHTML` without sanitization

```typescript
// ✅ Correct - Automatic escaping
function UserProfile({ bio }: { bio: string }) {
    return <div>{bio}</div>; // Safe - React escapes
}

// ❌ Wrong - XSS vulnerability
function UserProfile({ bio }: { bio: string }) {
    return <div dangerouslySetInnerHTML={{ __html: bio }} />; // VIOLATION
}

// ✅ Correct - If HTML needed, sanitize first
import DOMPurify from 'isomorphic-dompurify';

function UserProfile({ bio }: { bio: string }) {
    return <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(bio) }} />;
}
```

**Backend:**
- Use parameterized queries (prevents stored XSS)
- Validate and sanitize HTML input if storing user-generated HTML

**If violated**: **XSS attacks** - malicious scripts execute in user browsers

---

### 1.4 Resource Ownership

#### Rule: Every Resource Must Have an Owner

**MUST assign ownership immediately after resource creation.**

```kotlin
// ✅ Correct
@Transactional
fun createAsset(input: CreateAssetInput): Asset {
    val userId = principalProvider.getCurrentUserId()

    val asset = Asset(
        id = UuidV7Generator.generate(),
        name = input.name,
        ownerId = userId // ✅ Owner assigned
    )

    val saved = repository.save(asset)

    // Grant ownership
    resourceAccessControl.grantOwnership(
        resourceType = "asset",
        resourceId = saved.id,
        userId = userId
    )

    return saved
}

// ❌ Wrong - No owner
@Transactional
fun createAsset(input: CreateAssetInput): Asset {
    val asset = Asset(
        id = UuidV7Generator.generate(),
        name = input.name
        // VIOLATION - No ownerId
    )
    return repository.save(asset) // No ownership grant
}
```

**If violated**: Resources become inaccessible, broken authorization

---

### 1.5 Audit Logging

#### Rule: Never Log Sensitive Data

**NEVER log:**
- ❌ Passwords (plaintext or hashed)
- ❌ JWT tokens (access or refresh)
- ❌ API keys or secrets
- ❌ Credit card numbers
- ❌ Full PII unnecessarily

**DO log:**
- ✅ User IDs (UUIDs)
- ✅ Actions performed
- ✅ Timestamps
- ✅ IP addresses (if needed for security)

```kotlin
// ✅ Correct
logger.info("User ${userId} updated profile")
logger.info("Authentication failed for email: ${email}") // Email ok for failed login

// ❌ Wrong
logger.info("User ${userId} logged in with password: ${password}") // VIOLATION
logger.info("JWT token issued: ${token}") // VIOLATION
logger.debug("API key: ${apiKey}") // VIOLATION
```

**If violated**: **Security breach** - sensitive data exposed in logs

---

#### Rule: Audit Trail Immutability

**Audit logs MUST be:**
- ✅ Write-only (application writes, never updates/deletes)
- ✅ Immutable (no modifications after creation)
- ✅ Separate table (`*_audit` tables)

```kotlin
// ✅ Correct - Audit is append-only
fun updateProfile(id: UUID, input: UpdateProfileInput): Profile {
    val profile = repository.findById(id) ?: throw NotFoundException()

    // Create audit entries for changes
    val audits = profile.update(input, currentUserId)

    repository.update(profile)
    audits.forEach { auditRepository.save(it) } // ✅ Only insert

    return profile
}

// ❌ Wrong - Modifying audit records
fun deleteAuditLogs(profileId: UUID) {
    auditRepository.deleteByProfileId(profileId) // VIOLATION
}
```

**If violated**: Audit trail compromised, compliance violations

---

## 2. Testing Guardrails

### 2.1 Coverage Requirements

#### Rule: Minimum Coverage Targets

**MANDATORY coverage:**

| Type | Target | Enforcement |
|------|--------|-------------|
| **Security services** | **100% lines AND branches** | CI blocks merge if <100% |
| **Other services** | **90% lines, 85% branches** | CI warns if <90% |
| **Integration tests** | **80% lines, 75% branches** | CI warns if <80% |

```kotlin
// Security services require 100% coverage
class AuthenticationService { // MUST have 100% coverage
    fun authenticate(...): AuthToken? {
        // Every line and branch must be tested
    }
}

// Other services require 90%+
class AssetService { // MUST have 90%+ coverage
    fun create(...): Asset {
        // Most lines and branches must be tested
    }
}
```

**Check coverage:**
```bash
./gradlew koverVerify # Fails if below threshold
```

**If violated**: PR blocked by CI, must add tests before merge

---

#### Rule: Test All Branches

**MUST test BOTH success AND failure paths.**

```kotlin
// ✅ Correct - Both paths tested
@Test
fun `should create user with valid email`() {
    val user = service.create(validInput)
    assertNotNull(user)
}

@Test
fun `should reject user with invalid email`() {
    assertThrows<ValidationException> {
        service.create(invalidInput)
    }
}

// ❌ Wrong - Only success path tested
@Test
fun `should create user`() {
    val user = service.create(validInput)
    assertNotNull(user) // VIOLATION - No failure case
}
```

**If violated**: Edge cases untested, bugs in production

---

### 2.2 Test Structure

#### Rule: Repository Testing Strategy

**MANDATORY**: Repositories ONLY tested via integration tests.

```kotlin
// ✅ Correct - Integration test with real database
@MicronautTest
@Testcontainers
class UserRepositoryIntegrationTest {

    @Inject
    lateinit var repository: UserRepository

    @Test
    fun `should find user by email`() {
        val user = repository.save(User(email = "test@example.com"))
        val found = repository.findByEmail("test@example.com")
        assertEquals(user.id, found?.id)
    }
}

// ❌ Wrong - Unit test with mocked repository
@Test
fun `should find user by email`() {
    val mockRepo = mockk<UserRepository>()
    every { mockRepo.findByEmail(any()) } returns User(...) // VIOLATION
}
```

**Why**: Repositories need real database for JPA/Hibernate behavior

**If violated**: Tests don't catch real database issues

---

#### Rule: Test Method Return Type

**MUST return `Unit` (void).**

```kotlin
// ✅ Correct
@Test
fun `should create user`() {
    runBlocking {
        val user = service.create(input)
        assertNotNull(user)
    }
}

// ❌ Wrong
@Test
fun `should create user`() = runBlocking { // VIOLATION - Returns CoroutineScope
    val user = service.create(input)
    assertNotNull(user)
}
```

**If violated**: JUnit may not execute tests correctly

---

### 2.3 Test Data

#### Rule: Test Isolation

**MUST ensure each test is independent.**

```kotlin
// ✅ Correct - Reset state before each test
@BeforeEach
fun setUp() {
    repository.deleteAll() // Clean slate
    testUser = createTestUser()
}

@Test
fun `test 1`() { ... }

@Test
fun `test 2`() { ... } // Independent of test 1

// ❌ Wrong - Shared state
private var userId: UUID? = null // VIOLATION - Shared across tests

@Test
fun `test 1`() {
    userId = createUser().id // Affects test 2
}

@Test
fun `test 2`() {
    updateUser(userId!!) // Depends on test 1
}
```

**If violated**: Tests fail randomly, false positives/negatives

---

## 3. Architectural Guardrails

### 3.1 Service Boundaries

#### Rule: Services Own Their Data

**MANDATORY**: Each service owns its database. **NO cross-service database access.**

```kotlin
// ✅ Correct - Service queries its own database
@Service
class AssetService(
    private val assetRepository: AssetRepository // Own repository
) {
    fun getAssets(): List<Asset> {
        return assetRepository.findAll()
    }
}

// ❌ Wrong - Querying another service's database
@Service
class AssetService(
    private val assetRepository: AssetRepository,
    private val userRepository: UserRepository // VIOLATION - Security service's repo
) {
    fun getAssets(): List<Asset> {
        val users = userRepository.findAll() // VIOLATION
        return assetRepository.findAll()
    }
}
```

**Solution**: Use GraphQL Federation or service APIs to fetch cross-service data

**If violated**: Service coupling, breaks deployment independence

---

### 3.2 Clean Architecture Layers

#### Rule: Dependencies Point Inward

**MUST**: Dependencies point toward business logic (Service layer).

```
Allowed: Resolver → Service → Repository → Entity
Not Allowed: Service → Resolver (VIOLATION)
Not Allowed: Repository → Service (VIOLATION)
```

```kotlin
// ✅ Correct - Resolver depends on Service
@GraphQLResolver
class AssetResolver(
    private val assetService: AssetService // ✅ Allowed
) { ... }

// ❌ Wrong - Service depends on Resolver
@Service
class AssetService(
    private val assetResolver: AssetResolver // VIOLATION - Wrong direction
) { ... }
```

**If violated**: Circular dependencies, business logic leaks into API layer

---

### 3.3 Configuration

#### Rule: Never Hardcode Secrets

**MANDATORY**: All secrets MUST come from environment variables or Vault.

```kotlin
// ✅ Correct - From environment
@Property(name = "jwt.secret.key")
lateinit var jwtSecret: String

// ❌ Wrong - Hardcoded secret
val jwtSecret = "my-secret-key-12345" // VIOLATION
```

**If violated**: **Security breach** - secrets in version control

---

## 4. Logging Guardrails

### Rule: Log Levels

**MUST use appropriate log levels:**

- **ERROR**: Application errors requiring action (exceptions, failed operations)
- **WARN**: Potential issues, degraded functionality
- **INFO**: Important business events (login, user created, significant actions)
- **DEBUG**: Detailed diagnostic info (dev/staging only, NEVER in production)

```kotlin
// ✅ Correct - Appropriate levels
logger.info("User ${userId} logged in")
logger.warn("High memory usage detected")
logger.error("Failed to process payment", exception)

// ❌ Wrong - Inappropriate levels
logger.debug("User ${userId} logged in") // VIOLATION - Should be INFO
logger.error("User ${userId} logged in") // VIOLATION - Not an error
```

**If violated**: Log noise, missed critical issues

---

## 5. Enforcement

### How Guardrails are Enforced

| Guardrail | Enforcement Mechanism | Bypass Allowed? |
|-----------|----------------------|-----------------|
| JWT RS256 | Code implementation, cannot use HS256 in prod | ❌ No |
| Token expiration | Config validation, CI checks | ❌ No |
| @RequiresAuthorization | Interceptor blocks unauthorized requests | ❌ No |
| Parameterized queries | JPA/Spring Data enforced | ❌ No |
| Coverage (security 100%) | Kover + CI blocks merge | ❌ No |
| Coverage (services 90%) | Kover + CI warns | ⚠️ With approval |
| Repository integration tests | Code review | ❌ No |
| No cross-service DB access | Code review + documentation | ❌ No |
| Never log passwords/tokens | Code review | ❌ No |
| Audit immutability | Database constraints + code review | ⚠️ Audited |

---

## Summary: Critical Guardrails

**If AI violates ANY of these, STOP immediately:**

1. ❌ **Never** skip `@RequiresAuthorization` on endpoints
2. ❌ **Never** use string concatenation for SQL queries
3. ❌ **Never** log passwords, tokens, or secrets
4. ❌ **Never** rely on frontend for security enforcement
5. ❌ **Never** bypass 100% coverage for security services
6. ❌ **Never** query another service's database directly
7. ❌ **Never** hardcode secrets in code
8. ❌ **Never** modify audit logs after creation
9. ❌ **Never** skip input validation at API boundaries
10. ❌ **Never** use token expiration >15min (access) or >7days (refresh)

**When in doubt**: **ASK THE USER** instead of guessing or bypassing.

---

**Version**: 1.0.0
**Date**: 2026-01-04
**Maintained by**: Security Team & Tech Leads

*These guardrails protect security, quality, and architectural integrity. They are non-negotiable.*
