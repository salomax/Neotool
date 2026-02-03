# Password Reset Email - Template Engine Implementation Plan

## Overview

This document outlines the design and implementation plan for migrating the password reset email functionality from the legacy template approach to the new Comms template engine.

## Current State Analysis

### What You Have
- ✅ Fully implemented template engine (TemplateService, FileBasedTemplateRegistry, EmailRenderer, VariableSubstitutor)
- ✅ Mustache-based variable substitution with HTML escaping
- ✅ Locale resolution with fallback (pt-BR → pt → en)
- ✅ CSS inlining for email compatibility
- ✅ Example templates (user-welcome, order-confirmation)

### What Needs Refactoring
- ❌ `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/email/CommsEmailService.kt:27` - Uses old `loadEmailTemplate()` approach
- ❌ Manual template loading from `/emails/password-reset/{locale}.html`
- ❌ RAW content kind in GraphQL mutation
- ❌ Base `EmailService` class with template loading methods (legacy)

## Design: Password Reset Template

### Template Structure
```
service/kotlin/comms/src/main/resources/templates/email/password-reset/
├── template.yml           # Template definition
├── body.en.html          # English HTML template
├── body.pt.html          # Portuguese template
└── body.pt-BR.html       # Brazilian Portuguese template
```

### Template Variables
```yaml
variables:
  - name: resetUrl
    type: URL
    required: true
    description: "Password reset link with token"

  - name: expiresInMinutes
    type: INTEGER
    required: false
    default: 60
    description: "Link expiration time in minutes"
```

### HTML Email Design Principles
Following modern email design best practices:

1. **Responsive Layout**: Max-width 600px container
2. **Security-First**: Clear indication this is a security action
3. **Accessibility**: High contrast, clear CTA button
4. **Branding**: Consistent with Neotool visual identity
5. **Multi-client Compatibility**: Inline CSS, table-based layout for Outlook
6. **Clear Expiration**: Prominent expiration warning
7. **Fallback Link**: Text link in addition to button

## Detailed Task Breakdown

### Task 1: Design Password Reset Email Template Structure
**Goal:** Define template contract and variable requirements

**Actions:**
1. Document template key: `auth.password-reset`
2. Define required variables:
   - `resetUrl` (URL) - Password reset link with token
   - `expiresInMinutes` (INTEGER, default: 60) - Link expiration time
3. Define supported locales: `en`, `pt`, `pt-BR`
4. Define email subjects per locale

**Acceptance Criteria:**
- Template key follows naming convention (`auth.password-reset`)
- Variables match security module's requirements
- Design document created

---

### Task 2: Create template.yml
**Goal:** Create template definition file

**File:** `service/kotlin/comms/src/main/resources/templates/email/password-reset/template.yml`

**Content:**
```yaml
key: auth.password-reset
channel: EMAIL
metadata:
  name: "Password Reset Email"
  description: "Sent when user requests password reset"
  owner: "security-team"

variables:
  - name: resetUrl
    type: URL
    required: true
  - name: expiresInMinutes
    type: INTEGER
    required: false
    default: 60

locales:
  en:
    subject: "Reset your Neotool password"
    bodyPath: "body.en.html"
  pt:
    subject: "Redefina sua senha do Neotool"
    bodyPath: "body.pt.html"
  pt-BR:
    subject: "Redefina sua senha do Neotool"
    bodyPath: "body.pt-BR.html"

defaultLocale: en

channelConfig:
  email:
    format: HTML
    inlineCss: true
    trackOpens: false
```

**Acceptance Criteria:**
- YAML validates successfully
- All required fields present
- Follows template-authoring-guide.md conventions

---

### Task 3: Design and Create body.en.html
**Goal:** Create production-ready English HTML email template

**Design Features:**
- Modern, clean design with Neotool branding
- Clear security messaging
- Prominent CTA button ("Reset Password")
- Expiration warning
- Fallback text link
- Security notice (ignore if not requested)

**HTML Structure:**
```html
<!DOCTYPE html>
<html>
<head>
  <style>
    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; background-color: #f5f5f5; }
    .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; }
    .header { background-color: #2563eb; padding: 32px 24px; text-align: center; }
    .logo { color: #ffffff; font-size: 24px; font-weight: 700; }
    .content { padding: 40px 24px; }
    .title { font-size: 24px; font-weight: 600; color: #1f2937; margin: 0 0 16px 0; }
    .text { font-size: 16px; line-height: 1.6; color: #4b5563; margin: 0 0 24px 0; }
    .button-container { text-align: center; margin: 32px 0; }
    .button { background-color: #2563eb; color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 600; }
    .button:hover { background-color: #1d4ed8; }
    .link-text { font-size: 14px; color: #6b7280; word-break: break-all; }
    .warning { background-color: #fef3c7; border-left: 4px solid #f59e0b; padding: 16px; margin: 24px 0; }
    .warning-text { font-size: 14px; color: #92400e; margin: 0; }
    .footer { background-color: #f9fafb; padding: 24px; text-align: center; font-size: 12px; color: #6b7280; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <div class="logo">Neotool</div>
    </div>
    <div class="content">
      <h1 class="title">Reset Your Password</h1>
      <p class="text">You requested to reset your password for your Neotool account. Click the button below to create a new password:</p>

      <div class="button-container">
        <a href="{{resetUrl}}" class="button">Reset Password</a>
      </div>

      <p class="text">Or copy and paste this link into your browser:</p>
      <p class="link-text">{{resetUrl}}</p>

      <div class="warning">
        <p class="warning-text"><strong>⏱ This link expires in {{expiresInMinutes}} minutes.</strong></p>
      </div>

      <p class="text" style="font-size: 14px;">If you didn't request this password reset, please ignore this email. Your password will remain unchanged.</p>
    </div>
    <div class="footer">
      <p>© 2026 Neotool. All rights reserved.</p>
      <p>This is an automated email. Please do not reply.</p>
    </div>
  </div>
</body>
</html>
```

**Acceptance Criteria:**
- Renders correctly in major email clients (Gmail, Outlook, Apple Mail)
- CSS inlines properly via EmailRenderer
- All variables ({{resetUrl}}, {{expiresInMinutes}}) present
- Mobile-responsive design
- Clear security messaging

---

### Task 4: Create body.pt.html
**Goal:** Create Portuguese localized version

**Portuguese Translations:**
- Title: "Redefina sua Senha"
- Main text: "Você solicitou a redefinição de senha da sua conta Neotool. Clique no botão abaixo para criar uma nova senha:"
- Button: "Redefinir Senha"
- Copy link text: "Ou copie e cole este link no seu navegador:"
- Warning: "⏱ Este link expira em {{expiresInMinutes}} minutos."
- Security notice: "Se você não solicitou esta redefinição de senha, ignore este e-mail. Sua senha permanecerá inalterada."
- Footer: "© 2026 Neotool. Todos os direitos reservados." / "Este é um e-mail automatizado. Por favor, não responda."

**Acceptance Criteria:**
- Translations accurate and natural
- Same HTML structure as English version
- All variables present and correctly formatted

---

### Task 5: Create body.pt-BR.html
**Goal:** Create Brazilian Portuguese localized version

**Note:** Can be identical to `body.pt.html` or include Brazilian-specific variations if needed.

**Acceptance Criteria:**
- Translations accurate and natural (Brazilian Portuguese)
- Same HTML structure as English version
- All variables present and correctly formatted

---

### Task 6: Refactor CommsEmailService.sendPasswordResetEmail()
**Goal:** Replace old template approach with TemplateService

**Current Implementation (lines 21-87):**
```kotlin
// OLD APPROACH - TO BE REPLACED
val template = loadEmailTemplate(locale)
val resetUrl = buildResetUrl(token)
val htmlContent = template.replace("{{RESET_URL}}", resetUrl)
val subject = getSubject(locale)

val variables = mapOf(
    "input" to mapOf(
        "to" to email,
        "content" to mapOf(
            "kind" to "RAW",  // ❌ Old approach
            "format" to "HTML",
            "subject" to subject,
            "body" to htmlContent,
            ...
        )
    )
)
```

**New Implementation:**
```kotlin
@Singleton
class CommsEmailService(
    emailConfig: EmailConfig,
    private val graphQLServiceClient: GraphQLServiceClient,
    private val templateService: TemplateService,  // ✅ Inject TemplateService
) : EmailService(emailConfig) {

    override fun sendPasswordResetEmail(
        email: String,
        token: String,
        locale: String,
    ) {
        try {
            // Build reset URL
            val resetUrl = buildResetUrl(token)

            // Parse locale
            val localeObj = parseLocale(locale)  // "pt-BR" -> Locale

            // Render template using TemplateService
            val renderedTemplate = templateService.renderTemplate(
                templateKey = "auth.password-reset",
                locale = localeObj,
                channel = Channel.EMAIL,
                variables = mapOf(
                    "resetUrl" to resetUrl,
                    "expiresInMinutes" to 60
                )
            )

            // Send via GraphQL mutation
            val mutation = """
                mutation RequestEmailSend(${'$'}input: EmailSendRequestInput!) {
                  requestEmailSend(input: ${'$'}input) {
                    requestId
                    status
                  }
                }
            """.trimIndent()

            val variables = mapOf(
                "input" to mapOf(
                    "to" to email,
                    "content" to mapOf(
                        "kind" to "RAW",  // Keep RAW for now, or change to TEMPLATE if GraphQL schema supports it
                        "format" to "HTML",
                        "subject" to renderedTemplate.subject,
                        "body" to renderedTemplate.body,
                        "variables" to emptyMap<String, Any?>()
                    )
                )
            )

            val response = runBlocking {
                graphQLServiceClient.mutation(
                    mutation = mutation,
                    variables = variables,
                    targetAudience = "apollo-router",
                )
            }

            // Handle response (unchanged)
            if (!response.errors.isNullOrEmpty()) {
                logger.warn {
                    "requestEmailSend returned GraphQL errors for $email: ${response.errors}"
                }
                return
            }

            val data = response.data?.get("requestEmailSend") as? Map<*, *>
            if (data != null) {
                logger.info {
                    "Password reset email sent via Comms for $email: " +
                        "requestId=${data["requestId"]}, status=${data["status"]}"
                }
            } else {
                logger.warn { "requestEmailSend returned no data for $email" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send password reset email via Comms to: $email" }
            // Do not throw - same as AuthenticationService: return success for security
        }
    }

    // Helper to parse locale string to Locale object
    private fun parseLocale(locale: String): Locale {
        val parts = locale.split("-", "_")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            else -> Locale.ENGLISH
        }
    }
}
```

**Changes:**
1. ✅ Inject `TemplateService` dependency
2. ✅ Remove `loadEmailTemplate()` call
3. ✅ Remove `getSubject()` call (now in template.yml)
4. ✅ Remove manual `{{RESET_URL}}` replacement
5. ✅ Use `templateService.renderTemplate()` for rendering
6. ✅ Use rendered subject and body from template
7. ✅ Add locale parsing helper
8. ✅ Keep using RAW kind (rendered template content) or migrate to TEMPLATE kind if GraphQL schema is updated

**Note:** The implementation above keeps using `kind: RAW` but with content rendered by the template engine. If the GraphQL schema supports `kind: TEMPLATE`, you can pass the template key and variables directly instead of rendering server-side.

**Acceptance Criteria:**
- TemplateService injected successfully
- Template resolution works with locale fallback
- Variables passed correctly
- Rendered email matches template design
- Error handling preserved

---

### Task 7: Remove Old Template Loading Methods from EmailService Base Class
**Goal:** Clean up legacy code from EmailService base class

**Files to Update:**
- `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/email/EmailService.kt`

**Methods to Remove/Deprecate:**
- `loadEmailTemplate(locale: String)` (lines 38-52)
- `getSubject(locale: String)` (lines 57-61)
- `getDefaultTemplate()` (lines 74-97)

**Keep:**
- `buildResetUrl(token: String)` - Still needed for URL construction

**Alternative Approach (if other implementations still use these):**
- Mark methods as `@Deprecated` with migration message
- Document migration path in KDoc

**Acceptance Criteria:**
- Legacy methods removed or deprecated
- No compilation errors
- buildResetUrl() retained (still useful)

---

### Task 8: Add Unit Tests for Password Reset Template
**Goal:** Ensure template validation and rendering work correctly

**Test File:** `service/kotlin/comms/src/test/kotlin/io/github/salomax/neotool/comms/template/integration/PasswordResetTemplateTest.kt`

**Test Cases:**
```kotlin
@MicronautTest
class PasswordResetTemplateTest {

    @Inject
    lateinit var templateRegistry: TemplateRegistry

    @Inject
    lateinit var templateService: TemplateService

    @Test
    fun `password reset template resolves for en locale`() {
        val template = templateRegistry.resolve(
            "auth.password-reset",
            Locale.ENGLISH,
            Channel.EMAIL
        )
        assertNotNull(template)
        assertEquals("auth.password-reset", template.key)
    }

    @Test
    fun `password reset template renders with all variables`() {
        val rendered = templateService.renderTemplate(
            templateKey = "auth.password-reset",
            locale = Locale.ENGLISH,
            channel = Channel.EMAIL,
            variables = mapOf(
                "resetUrl" to "https://app.neotool.io/reset?token=abc123",
                "expiresInMinutes" to 60
            )
        )

        assertNotNull(rendered.subject)
        assertTrue(rendered.body.contains("https://app.neotool.io/reset?token=abc123"))
        assertTrue(rendered.body.contains("60"))
    }

    @Test
    fun `password reset template falls back to pt for pt-BR locale`() {
        val rendered = templateService.renderTemplate(
            templateKey = "auth.password-reset",
            locale = Locale("pt", "BR"),
            channel = Channel.EMAIL,
            variables = mapOf(
                "resetUrl" to "https://app.neotool.io/reset?token=abc123"
            )
        )

        assertTrue(rendered.subject?.contains("Redefina") ?: false)
    }

    @Test
    fun `password reset template fails when resetUrl missing`() {
        assertThrows<MissingVariableException> {
            templateService.renderTemplate(
                templateKey = "auth.password-reset",
                locale = Locale.ENGLISH,
                channel = Channel.EMAIL,
                variables = emptyMap()  // Missing required resetUrl
            )
        }
    }

    @Test
    fun `password reset template uses default expiresInMinutes`() {
        val rendered = templateService.renderTemplate(
            templateKey = "auth.password-reset",
            locale = Locale.ENGLISH,
            channel = Channel.EMAIL,
            variables = mapOf(
                "resetUrl" to "https://app.neotool.io/reset?token=abc123"
                // expiresInMinutes not provided, should use default 60
            )
        )

        assertTrue(rendered.body.contains("60"))
    }

    @Test
    fun `password reset email has inlined CSS`() {
        val rendered = templateService.renderTemplate(
            templateKey = "auth.password-reset",
            locale = Locale.ENGLISH,
            channel = Channel.EMAIL,
            variables = mapOf(
                "resetUrl" to "https://app.neotool.io/reset?token=abc123"
            )
        )

        // CSS should be inlined by EmailRenderer
        assertTrue(rendered.body.contains("style="))
    }
}
```

**Acceptance Criteria:**
- All test cases pass
- Code coverage > 90% for template path
- Tests validate locale fallback
- Tests validate variable substitution
- Tests validate CSS inlining

---

### Task 9: Add Integration Test for Password Reset Email Flow
**Goal:** Test end-to-end password reset flow

**Test File:** `service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/integration/PasswordResetEmailIntegrationTest.kt`

**Test Case:**
```kotlin
@MicronautTest
class PasswordResetEmailIntegrationTest {

    @Inject
    lateinit var commsEmailService: CommsEmailService

    @MockBean(GraphQLServiceClient::class)
    fun graphQLServiceClient(): GraphQLServiceClient = mockk()

    @Test
    fun `send password reset email with template engine`() {
        // Arrange
        val email = "user@example.com"
        val token = "test-reset-token-123"
        val locale = "pt-BR"

        val mockResponse = GraphQLResponse(
            data = mapOf(
                "requestEmailSend" to mapOf(
                    "requestId" to "req-123",
                    "status" to "QUEUED"
                )
            ),
            errors = null
        )

        coEvery {
            graphQLServiceClient().mutation(any(), any(), any())
        } returns mockResponse

        // Act
        commsEmailService.sendPasswordResetEmail(email, token, locale)

        // Assert
        coVerify {
            graphQLServiceClient().mutation(
                mutation = any(),
                variables = match { vars ->
                    val input = vars["input"] as? Map<*, *>
                    val content = input?.get("content") as? Map<*, *>
                    content?.get("subject")?.toString()?.contains("Redefina") == true
                },
                targetAudience = "apollo-router"
            )
        }
    }
}
```

**Acceptance Criteria:**
- Integration test passes
- Email sent successfully via GraphQL mutation
- Template rendered with correct locale
- Mocked GraphQL client verifies correct parameters

---

### Task 10: Update Documentation
**Goal:** Document the new password reset implementation

**Files to Update:**

1. **Template Authoring Guide** (`docs/03-features/comms/template-authoring-guide.md`)
   - Add password reset template example to "Example Templates" section
   - Document `auth.password-reset` variables

2. **Security Module README** (create if needed: `service/kotlin/security/README.md`)
   - Document email service usage
   - Update examples showing new template approach

3. **GraphQL Schema Comments** (if applicable)
   - Update `EmailContentInput` documentation
   - Add example for template-based emails

**Content to Add to Template Authoring Guide:**
```markdown
## Example Templates

### Password Reset Email

Located in `email/password-reset/`, this template is used for password reset requests.

**Template Key:** `auth.password-reset`

**Variables:**
- `resetUrl` (URL, required) - Password reset link with token
- `expiresInMinutes` (INTEGER, optional, default: 60) - Link expiration time

**Supported Locales:** `en`, `pt`, `pt-BR`

**Usage Example:**
```kotlin
templateService.renderTemplate(
    templateKey = "auth.password-reset",
    locale = Locale("pt", "BR"),
    channel = Channel.EMAIL,
    variables = mapOf(
        "resetUrl" to "https://app.neotool.io/reset?token=abc123",
        "expiresInMinutes" to 60
    )
)
```
```

**Acceptance Criteria:**
- Documentation updated with examples
- Password reset template documented
- Usage examples provided

---

## Summary of Changes

| Component | Change | Impact |
|-----------|--------|--------|
| **Comms Module** | Add `password-reset` template | ✅ New template files |
| **Security Module** | Refactor `CommsEmailService` | ⚠️ Breaking change (internal) |
| **EmailService Base** | Remove/deprecate legacy methods | ⚠️ Deprecation |
| **GraphQL Schema** | No changes needed (uses RAW with rendered content) | ✅ No breaking changes |
| **Tests** | Add unit + integration tests | ✅ Improved coverage |

## Migration Benefits

1. **Consistency**: Uses same template engine as other emails (welcome, order confirmation)
2. **Maintainability**: Templates in version control, not embedded in code
3. **i18n**: Proper locale resolution with fallback
4. **Security**: HTML escaping by default via Mustache
5. **Testability**: Can test templates independently
6. **Scalability**: Easy to add new locales or modify design
7. **Best Practices**: Follows template-engine-requirements.md spec
8. **Separation of Concerns**: Design changes don't require code changes

## Risk Mitigation

1. **Backward Compatibility**: No breaking changes to public APIs
2. **Rollback Plan**: Git revert + redeploy if issues arise
3. **Testing**: Comprehensive unit + integration tests before merge
4. **Monitoring**: Track email send metrics (success/failure rates)
5. **Gradual Migration**: Can keep old methods as fallback initially
6. **Template Validation**: Templates validated at startup (fail-fast)

## Technical Debt Addressed

1. ✅ Removes hardcoded HTML templates from code
2. ✅ Removes manual string replacement (`{{RESET_URL}}`)
3. ✅ Removes locale-specific logic scattered in code
4. ✅ Consolidates email rendering logic in one place
5. ✅ Enables proper template testing

## Success Metrics

- ✅ Password reset emails render correctly in all major email clients
- ✅ All unit tests pass with >90% coverage
- ✅ Integration test validates end-to-end flow
- ✅ No increase in email send failures
- ✅ Template changes deployable without code changes
- ✅ Locale fallback works correctly (pt-BR → pt → en)

## Future Enhancements

1. **Template Preview**: Add admin UI to preview templates
2. **A/B Testing**: Test different email designs
3. **Analytics**: Track click rates on reset button
4. **Dynamic Expiration**: Make expiration time configurable per request
5. **Branding Customization**: Support white-label email templates

---

**Document Version:** 1.0
**Last Updated:** 2026-02-02
**Owner:** Security Team + Comms Team
**Status:** Ready for Implementation
