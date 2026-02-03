# Template Registry & Template Engine Requirements

## Executive Summary

This document defines the requirements for a scalable, multi-channel template registry and rendering engine for the Neotool Comms module. The design incorporates best practices from industry leaders (Netflix RENO, Uber RAMEN) and proven design patterns (Strategy, Factory, Observer) to deliver a clean, robust, and extensible solution.

## Context

**Current State:**
- Comms module has email working with RAW content (plain text/HTML passed directly)
- No template abstraction or variable substitution
- No i18n support for content
- Template handling is tightly coupled to email channel

**Target State:**
- Unified template registry supporting all channels (email, chat, WhatsApp, push, in-app)
- Declarative template definitions with variable substitution
- i18n/l10n support with locale-aware template resolution
- Channel-specific rendering engines with shared core abstractions
- Clean separation between template storage, resolution, and rendering

## Goals

### Primary Goals
1. **Centralized Template Management**: Single source of truth for all communication templates across channels
2. **Channel Abstraction**: Unified template contracts with channel-specific rendering implementations
3. **i18n Support**: Locale-aware template resolution with fallback strategies
4. **Developer Experience**: Simple, declarative template authoring with type-safe variable substitution
5. **Operational Excellence**: Version control friendly, testable, and debuggable templates

### Non-Goals (Explicit)
- Template versioning or A/B testing infrastructure
- Advanced personalization or recommendation engines
- Marketing automation or campaign management
- Visual template editors or admin UIs
- Dynamic template compilation at runtime (templates are static resources)

## Architecture Overview

### Design Patterns

Based on industry research, we adopt the following proven patterns:

**Strategy Pattern** (Primary)
- Each channel (Email, WhatsApp, Push, In-App, Chat) implements a common `TemplateRenderer` interface
- Rendering logic is encapsulated per channel, allowing independent evolution
- Example: Email renderer handles HTML/CSS inlining, WhatsApp renderer enforces character limits

**Factory Pattern**
- `TemplateRendererFactory` dynamically dispatches to the correct renderer based on channel
- Adding new channels requires only factory registration, not business logic changes
- Supports provider-specific renderers (e.g., `WhatsAppBusinessRenderer`, `WhatsAppTwilioRenderer`)

**Template Registry Pattern**
- Central registry resolves templates by `(key, locale, channel)` tuple
- Supports multiple resolution strategies: file-based, database-backed, remote (future)
- Implements caching and reload mechanisms for operational efficiency

### Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Template Request                          │
│           (key, locale, channel, variables)                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                 TemplateService                              │
│  - Orchestrates lookup and rendering                        │
│  - Handles locale fallback                                  │
│  - Caches resolved templates                                │
└──────────────────────┬──────────────────────────────────────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
┌──────────────────┐       ┌──────────────────────┐
│ TemplateRegistry │       │ RendererFactory      │
│ - Lookup by key  │       │ - Channel dispatch   │
│ - Locale resolve │       │ - Provider selection │
│ - File/DB load   │       │                      │
└──────────────────┘       └──────┬───────────────┘
                                  │
                   ┌──────────────┼──────────────┐
                   ▼              ▼              ▼
         ┌──────────────┐ ┌─────────────┐ ┌──────────────┐
         │EmailRenderer │ │PushRenderer │ │WhatsAppRen..│
         │- HTML layout │ │- JSON notif │ │- Plain text  │
         │- CSS inline  │ │- Badge mgmt │ │- Link format │
         └──────────────┘ └─────────────┘ └──────────────┘
```

### Data Flow

1. **Template Definition**: Templates are defined as structured files (YAML/JSON) in `comms/src/main/resources/templates/`
2. **Template Loading**: On startup, `TemplateRegistry` scans and indexes all templates
3. **Template Resolution**: When a template is requested:
   - Lookup by `(key, channel)` → find template definition
   - Apply locale resolution: `pt-BR` → `pt` → `en` (default) → error
   - Return template metadata + content reference
4. **Variable Substitution**: `TemplateRenderer` receives variables and applies them to template
5. **Channel Rendering**: Channel-specific renderer produces final output (HTML, JSON, plain text)

## Functional Requirements

### 1. Template Definition Format

Templates are defined as structured YAML files with the following schema:

**Location:** `service/kotlin/comms/src/main/resources/templates/{channel}/{template-key}/`

**Structure:**
```yaml
# email/user-welcome/template.yml
key: user.welcome                    # Unique identifier
channel: EMAIL                       # EMAIL, PUSH, WHATSAPP, IN_APP, CHAT
metadata:
  name: "User Welcome Email"
  description: "Sent when user completes signup"
  owner: "authentication-team"

variables:                           # Required variables with types
  - name: userName
    type: STRING
    required: true
  - name: verificationUrl
    type: URL
    required: true
  - name: expiresInHours
    type: INTEGER
    required: false
    default: 24

locales:                             # Locale-specific content
  en:
    subject: "Welcome to Neotool, {{userName}}!"
    bodyPath: "body.en.html"         # Relative to template directory
  pt-BR:
    subject: "Bem-vindo ao Neotool, {{userName}}!"
    bodyPath: "body.pt-BR.html"
  pt:
    subject: "Bem-vindo ao Neotool, {{userName}}!"
    bodyPath: "body.pt.html"

defaultLocale: en

# Channel-specific configuration
channelConfig:
  email:
    format: HTML                     # HTML, TEXT, MULTIPART
    inlineCss: true
    trackOpens: false
```

**Example template body** (`body.en.html`):
```html
<!DOCTYPE html>
<html>
<head>
  <style>
    .button { background: #007bff; color: white; padding: 12px 24px; }
  </style>
</head>
<body>
  <h1>Welcome, {{userName}}!</h1>
  <p>Click the button below to verify your account:</p>
  <a href="{{verificationUrl}}" class="button">Verify Account</a>
  <p>This link expires in {{expiresInHours}} hours.</p>
</body>
</html>
```

### 2. Template Registry Interface

**Core Interface:**
```kotlin
interface TemplateRegistry {
    /**
     * Resolve template by key, locale, and channel.
     * Implements locale fallback: specific -> language -> default.
     */
    fun resolve(
        key: String,
        locale: Locale,
        channel: Channel
    ): TemplateDefinition?

    /**
     * List all registered templates for a channel.
     */
    fun listByChannel(channel: Channel): List<TemplateDefinition>

    /**
     * Reload templates from source (for hot-reload in dev).
     */
    fun reload()
}

data class TemplateDefinition(
    val key: String,
    val channel: Channel,
    val metadata: TemplateMetadata,
    val variables: List<VariableDefinition>,
    val content: TemplateContent,
    val defaultLocale: Locale
)

data class TemplateContent(
    val locale: Locale,
    val subject: String?,           // For email, push
    val body: String,                // Template content
    val channelConfig: Map<String, Any>
)
```

**Implementation Strategy:**
- **Phase 1**: File-based registry (YAML files in classpath)
- **Future**: Database-backed registry for dynamic updates (not in scope)

### 3. Variable Substitution Engine

**Requirements:**
- Support simple variable replacement: `{{variableName}}`
- Support nested properties: `{{user.profile.firstName}}`
- Support conditional sections: `{{#if hasDiscount}}...{{/if}}` (optional, evaluate later)
- Support iteration: `{{#each items}}...{{/each}}` (optional, evaluate later)
- Escape HTML by default for security (XSS prevention)
- Allow raw/unescaped variables: `{{{rawHtml}}}` (with explicit opt-in)

**Technology Options:**
1. **Mustache** (Recommended)
   - Logic-less, simple syntax
   - Wide adoption, battle-tested
   - Available in Java: `com.github.spullara.mustache.java`
   - Supports conditionals and iteration if needed later

2. **Handlebars** (Alternative)
   - Superset of Mustache with more features
   - May be overkill for current requirements

3. **Custom Regex-Based** (Not Recommended)
   - Reinventing the wheel, error-prone

**Recommendation:** Start with **Mustache** for simplicity and maturity.

### 4. Channel-Specific Rendering

Each channel implements the `TemplateRenderer` interface with channel-specific logic:

**Interface:**
```kotlin
interface TemplateRenderer {
    fun render(
        template: TemplateDefinition,
        variables: Map<String, Any>,
        locale: Locale
    ): RenderedTemplate

    fun validate(
        template: TemplateDefinition
    ): ValidationResult
}

data class RenderedTemplate(
    val channel: Channel,
    val subject: String?,
    val body: String,
    val metadata: Map<String, Any>
)
```

**Channel-Specific Requirements:**

#### Email Renderer
- **HTML Processing**: Inline CSS for email client compatibility
- **Multipart Support**: Generate both HTML and plain-text versions
- **Link Tracking**: Optionally wrap links for analytics (if `trackOpens: true`)
- **Image Embedding**: Support `cid:` references for embedded images
- **Validation**: Check for common email issues (missing alt text, broken links)

#### WhatsApp Renderer
- **Character Limits**: Enforce 4096 character limit per message
- **Formatting**: Support WhatsApp markdown (*bold*, _italic_, ~strikethrough~)
- **Link Formatting**: Ensure links are clickable (not obfuscated)
- **Emoji Support**: Preserve Unicode emojis
- **Validation**: Check for unsupported characters

#### Push Notification Renderer
- **Title/Body Structure**: Separate title and body fields
- **Character Limits**: Platform-specific (iOS: 178 chars, Android: ~240 chars)
- **Badge Management**: Support badge count variables
- **Deep Links**: Format app deep links correctly
- **Validation**: Ensure platform compatibility (iOS, Android, Web)

#### In-App Notification Renderer
- **Markdown Support**: Render simple markdown (bold, italic, links)
- **Action Buttons**: Support up to 3 action buttons with URLs
- **Icon/Image**: Support icon URL for rich notifications
- **Validation**: Check for XSS vulnerabilities

#### Chat Renderer
- **Plain Text**: Simple text rendering for internal chat
- **User Mentions**: Support @username syntax (future)
- **Emoji Support**: Preserve Unicode emojis
- **Validation**: Check for message length limits

### 5. i18n Locale Resolution

**Locale Resolution Strategy:**
1. Try exact match: `pt-BR` → look for `pt-BR` template
2. Try language fallback: `pt-BR` → look for `pt` template
3. Try default locale: `pt-BR` → look for `en` template (or template's `defaultLocale`)
4. Error: No template found for any locale

**Locale Format:**
- Use Java `Locale` class for consistency
- Support language + country: `en-US`, `pt-BR`, `es-MX`
- Support language only: `en`, `pt`, `es`

**Configuration:**
- Template-level default locale (defined in template YAML)
- System-level default locale (from application config): `COMMS_DEFAULT_LOCALE=en`

**Special Cases:**
- If user has no locale preference, use system default
- If template doesn't exist in any locale, throw `TemplateNotFoundException`

### 6. Template Validation

**Build-Time Validation:**
- Templates are validated on application startup
- Errors prevent application from starting (fail-fast)
- Validation checks:
  - YAML syntax correctness
  - Required fields present (`key`, `channel`, `locales`)
  - Variable definitions match usage in templates
  - File references (`bodyPath`) exist and are readable
  - No duplicate template keys within a channel

**Runtime Validation:**
- When rendering, validate that all required variables are provided
- Check variable types match definitions (optional, soft fail)
- Warn on unused variables (debugging aid)

**Test Utilities:**
- Provide `TemplateTestUtils` for unit testing
- Ability to render templates with mock variables
- Snapshot testing for regression prevention

### 7. Performance & Caching

**Caching Strategy:**
- **Template Definitions**: Loaded once at startup, cached in memory
- **Resolved Templates**: Cache `(key, locale, channel)` → `TemplateContent` mapping
- **Rendered Output**: Do NOT cache rendered output (variables change)

**Hot Reload (Dev Mode Only):**
- Watch template directory for changes
- Reload templates when files change
- Requires `COMMS_TEMPLATE_HOT_RELOAD=true` (disabled in production)

**Performance Targets:**
- Template resolution: < 1ms (in-memory lookup)
- Variable substitution: < 10ms (Mustache rendering)
- Channel rendering: < 50ms (includes CSS inlining for email)

## Non-Functional Requirements

### Security
- **XSS Prevention**: Escape variables by default in HTML contexts
- **Injection Prevention**: Validate variable content, reject script tags
- **File Access**: Templates can only reference files within their directory (no path traversal)
- **Audit Logging**: Log template renders for compliance (optional, configure per template)

### Observability
- **Metrics**: Track template render count, errors, and latency per `(key, channel)`
- **Logging**: Log template resolution failures, locale fallbacks, and validation errors
- **Tracing**: Integrate with distributed tracing (include template key in trace context)

### Testability
- All components mockable via interfaces
- Template validation testable without full application context
- Provide `InMemoryTemplateRegistry` for testing
- Support fixture templates in test resources

### Scalability
- Template registry scales horizontally (stateless, read-only)
- No database dependencies in critical path (files loaded at startup)
- Future: support remote template registry (S3, database) with local caching

### Maintainability
- Templates stored as plain text (YAML + HTML) → easy to version control
- Clear separation between template definition and rendering logic
- Channel renderers independently testable
- Minimal dependencies (Mustache library only)

## Technical Decisions

### Decision 1: File-Based vs Database-Backed Templates

**Decision:** Start with file-based templates stored in `src/main/resources/templates/`.

**Rationale:**
- Simpler to implement and test
- Version control friendly (Git tracks changes)
- No database dependency for core functionality
- Sufficient for current scale (< 100 templates expected)
- Can migrate to database later without API changes

**Trade-offs:**
- Cannot update templates without redeployment
- No dynamic template creation via API
- Acceptable for Phase 3 scope

### Decision 2: Mustache vs Handlebars vs Custom

**Decision:** Use Mustache for variable substitution.

**Rationale:**
- Logic-less templates prevent business logic leakage into templates
- Battle-tested library with wide adoption
- Sufficient for current requirements (simple variable replacement)
- Supports conditionals and iteration if needed later (via sections)

**Trade-offs:**
- Less powerful than Handlebars (no helpers)
- Acceptable for declarative templates

### Decision 3: Template Structure (Monolithic vs Per-Locale Files)

**Decision:** Use per-locale body files referenced from central YAML.

**Rationale:**
- Separates structure (YAML) from content (HTML/text)
- Easier for translators (can edit HTML files directly)
- Avoids massive YAML files with embedded HTML

**Trade-offs:**
- More files per template (1 YAML + N locale files)
- Acceptable for maintainability

### Decision 4: CSS Inlining for Email

**Decision:** Use a library (e.g., `jsoup` + inline CSS processor) for email HTML processing.

**Rationale:**
- Email clients require inline styles (many strip `<style>` tags)
- Manually inlining is error-prone and tedious
- Libraries handle edge cases (specificity, !important, media queries)

**Recommendation:** Use `org.jsoup:jsoup` + `com.helger:ph-css` for parsing and inlining.

### Decision 5: Template Validation Timing

**Decision:** Validate all templates at application startup (fail-fast).

**Rationale:**
- Catches errors early (before production)
- Prevents runtime surprises (missing files, syntax errors)
- Aligns with "fail-fast" principle

**Trade-offs:**
- Slower startup time (negligible for < 100 templates)
- Acceptable for reliability

## Integration with Existing Comms Module

### Current Email Flow
```
GraphQL Mutation
  → EmailSendService.requestSend()
  → Produce Kafka event (EMAIL_SEND_REQUESTED)
  → EmailConsumer receives event
  → EmailProvider sends email (SMTP)
```

### Enhanced Flow with Templates
```
GraphQL Mutation
  → EmailSendService.requestSend()
      - Input includes: templateKey, locale, variables
      - TemplateService.render(templateKey, locale, EMAIL, variables)
      - Produce Kafka event with rendered content
  → EmailConsumer receives event
  → EmailProvider sends email (SMTP)
```

### API Changes

**Before (Phase 1):**
```graphql
input EmailSendRequestInput {
  to: String!
  content: EmailContentInput!
}

input EmailContentInput {
  kind: EmailContentKind!    # RAW or TEMPLATE (not implemented)
  format: EmailFormat!       # TEXT or HTML
  subject: String!
  body: String!
  variables: JSON
}
```

**After (Phase 3):**
```graphql
input EmailSendRequestInput {
  to: String!
  content: EmailContentInput!
  locale: String             # e.g., "pt-BR", optional (defaults to user locale)
}

input EmailContentInput {
  kind: EmailContentKind!    # RAW or TEMPLATE

  # If kind = RAW (existing, unchanged)
  format: EmailFormat        # TEXT or HTML
  subject: String
  body: String

  # If kind = TEMPLATE (new)
  templateKey: String        # e.g., "user.welcome"
  variables: JSON!           # { "userName": "João", "verificationUrl": "..." }
}
```

**Backward Compatibility:**
- `kind: RAW` continues to work as before (no breaking changes)
- `kind: TEMPLATE` is new, opt-in

## Reference Implementations

### Industry Benchmarks

Based on research of Netflix, Uber, Spotify, and modern notification systems:

**Netflix RENO (Rapid Event Notification System):**
- Event-driven architecture with priority queues
- Template rendering decoupled from delivery
- Cassandra for high-scale template storage (future consideration)

**Uber RAMEN (Realtime Asynchronous Messaging Network):**
- Kafka streams for event orchestration
- Per-channel consumers with independent scaling
- Generative AI for dynamic content (out of scope, but interesting future direction)

**Common Patterns:**
- Message queues (Kafka, SQS) decouple producers and consumers
- Factory pattern for channel dispatch
- Strategy pattern for channel-specific rendering
- Template versioning for A/B testing (explicit non-goal for us)

**Modern Multi-Channel Best Practices (2026):**
- Template management with localization support
- Shared components across notification types
- User timezone awareness (future)
- Batching and throttling for delivery optimization (future)

### Open Source Inspirations

**Handlebars.java / Mustache.java:**
- Proven template engines with variable substitution
- Logic-less philosophy prevents complexity

**Spring MessageSource / i18n:**
- Locale resolution and fallback strategies
- Resource bundle management patterns

**Notification Services (SuprSend, MagicBell, Knock):**
- Workflow engines with template rendering
- Multi-channel abstractions
- Provider layer patterns

## Implementation Backlog

### Epic 1: Template Registry Foundation

**Goal:** Establish core template loading and resolution infrastructure.

**Tasks:**

#### Task 1.1: Define Template Schema
- [ ] Create YAML schema for template definitions
- [ ] Document template structure and conventions
- [ ] Create example templates for email, push, WhatsApp
- [ ] Define validation rules for templates

**Acceptance Criteria:**
- YAML schema documented in `docs/03-features/comms/template-schema.md`
- At least 3 example templates created
- Schema supports all required fields (key, channel, locales, variables)

#### Task 1.2: Implement TemplateRegistry Interface
- [ ] Create `TemplateRegistry` interface
- [ ] Create `TemplateDefinition`, `TemplateContent` data classes
- [ ] Implement `FileBasedTemplateRegistry` with YAML parsing
- [ ] Add template directory scanning and indexing

**Acceptance Criteria:**
- `TemplateRegistry` interface has `resolve()`, `listByChannel()`, `reload()` methods
- `FileBasedTemplateRegistry` loads templates from `resources/templates/`
- Unit tests for registry with fixture templates

#### Task 1.3: Locale Resolution Logic
- [ ] Implement locale fallback algorithm (exact → language → default)
- [ ] Add `LocaleResolver` utility class
- [ ] Support system default locale configuration (`COMMS_DEFAULT_LOCALE`)
- [ ] Handle edge cases (null locale, unsupported locale)

**Acceptance Criteria:**
- Locale resolution follows documented fallback strategy
- Unit tests cover all fallback scenarios
- Configuration property for default locale

#### Task 1.4: Template Validation at Startup
- [ ] Implement `TemplateValidator` with validation rules
- [ ] Validate YAML syntax, required fields, file references
- [ ] Check for duplicate template keys
- [ ] Fail application startup on validation errors

**Acceptance Criteria:**
- Application fails to start if templates are invalid
- Validation errors are clearly logged with file path and line number
- Unit tests for each validation rule

#### Task 1.5: Hot Reload for Development
- [ ] Implement file watcher for template directory (dev mode only)
- [ ] Add `COMMS_TEMPLATE_HOT_RELOAD` configuration
- [ ] Reload templates on file changes
- [ ] Log reload events

**Acceptance Criteria:**
- Hot reload works in dev mode when enabled
- Changes to templates are reflected without restart
- Production mode disables hot reload

---

### Epic 2: Variable Substitution Engine

**Goal:** Integrate Mustache for variable substitution with type safety.

**Tasks:**

#### Task 2.1: Integrate Mustache Library
- [ ] Add `com.github.spullara.mustache.java:compiler` dependency
- [ ] Create `VariableSubstitutor` wrapper around Mustache
- [ ] Configure Mustache settings (escaping, delimiters)
- [ ] Add error handling for rendering failures

**Acceptance Criteria:**
- Mustache dependency added to `build.gradle.kts`
- `VariableSubstitutor` class renders simple templates
- Unit tests for variable substitution

#### Task 2.2: Variable Definition and Type Checking
- [ ] Create `VariableDefinition` data class (name, type, required, default)
- [ ] Parse variable definitions from template YAML
- [ ] Implement runtime variable validation (required vars present)
- [ ] Add type coercion for common types (String, Int, Boolean, URL)

**Acceptance Criteria:**
- Template YAML includes `variables:` section
- Rendering fails if required variable is missing
- Warning logged for unused variables

#### Task 2.3: Security: XSS Prevention
- [ ] Enable HTML escaping by default in Mustache
- [ ] Document `{{{rawHtml}}}` syntax for unescaped content
- [ ] Add security warning in template authoring guide
- [ ] Test XSS prevention with malicious variables

**Acceptance Criteria:**
- Variables containing `<script>` tags are escaped in HTML output
- Raw syntax `{{{var}}}` preserves HTML (with documentation warning)
- Security test cases pass

#### Task 2.4: Advanced Features (Optional)
- [ ] Support nested properties: `{{user.profile.firstName}}`
- [ ] Support conditionals: `{{#if hasDiscount}}...{{/if}}`
- [ ] Support iteration: `{{#each items}}...{{/each}}`
- [ ] Document advanced syntax in template guide

**Acceptance Criteria:**
- Nested properties work in templates
- Conditionals and iteration work (if implemented)
- Examples in documentation

---

### Epic 3: Channel-Specific Renderers

**Goal:** Implement rendering logic for each communication channel.

**Tasks:**

#### Task 3.1: Define TemplateRenderer Interface
- [ ] Create `TemplateRenderer` interface with `render()` and `validate()` methods
- [ ] Create `RenderedTemplate` data class
- [ ] Define channel-specific configuration schema in template YAML

**Acceptance Criteria:**
- `TemplateRenderer` interface defined
- Interface supports all channels (email, push, WhatsApp, in-app, chat)

#### Task 3.2: Implement EmailRenderer
- [ ] Create `EmailRenderer` implementing `TemplateRenderer`
- [ ] Integrate CSS inlining library (`jsoup` + `ph-css`)
- [ ] Support HTML and plain-text formats
- [ ] Support multipart emails (HTML + plain text)
- [ ] Validate email-specific requirements (alt text, links)

**Acceptance Criteria:**
- `EmailRenderer` renders HTML emails with inlined CSS
- Multipart emails generated correctly
- Unit tests with example email templates

#### Task 3.3: Implement PushRenderer
- [ ] Create `PushRenderer` implementing `TemplateRenderer`
- [ ] Separate title and body fields
- [ ] Enforce character limits (iOS: 178, Android: 240)
- [ ] Support badge count variables
- [ ] Format deep links correctly

**Acceptance Criteria:**
- `PushRenderer` outputs JSON structure for push notifications
- Character limits enforced with truncation
- Unit tests for iOS and Android formats

#### Task 3.4: Implement WhatsAppRenderer
- [ ] Create `WhatsAppRenderer` implementing `TemplateRenderer`
- [ ] Enforce 4096 character limit
- [ ] Support WhatsApp markdown (*bold*, _italic_, ~strikethrough~)
- [ ] Format links for clickability
- [ ] Validate unsupported characters

**Acceptance Criteria:**
- `WhatsAppRenderer` outputs plain text with WhatsApp markdown
- Character limit enforced
- Unit tests with emoji and markdown

#### Task 3.5: Implement InAppRenderer
- [ ] Create `InAppRenderer` implementing `TemplateRenderer`
- [ ] Render simple markdown (bold, italic, links)
- [ ] Support action buttons (up to 3)
- [ ] Support icon/image URLs
- [ ] Validate for XSS vulnerabilities

**Acceptance Criteria:**
- `InAppRenderer` outputs structured notification data
- Markdown rendered to HTML safely
- Unit tests with action buttons

#### Task 3.6: Implement ChatRenderer
- [ ] Create `ChatRenderer` implementing `TemplateRenderer`
- [ ] Simple plain-text rendering
- [ ] Preserve Unicode emojis
- [ ] Enforce message length limits
- [ ] Prepare for @mentions syntax (future)

**Acceptance Criteria:**
- `ChatRenderer` outputs plain text
- Emojis preserved
- Unit tests with long messages

#### Task 3.7: Implement TemplateRendererFactory
- [ ] Create `TemplateRendererFactory` with channel dispatch logic
- [ ] Register all renderer implementations
- [ ] Support provider-specific renderers (future extensibility)
- [ ] Add metrics for renderer selection

**Acceptance Criteria:**
- Factory returns correct renderer for each channel
- Adding new renderer requires only factory registration
- Unit tests for factory dispatch

---

### Epic 4: Template Service Integration

**Goal:** Integrate template registry and renderers into a unified service.

**Tasks:**

#### Task 4.1: Create TemplateService
- [ ] Create `TemplateService` orchestrating registry and renderers
- [ ] Implement `renderTemplate(key, locale, channel, variables)` method
- [ ] Add caching for resolved templates
- [ ] Add error handling and fallbacks

**Acceptance Criteria:**
- `TemplateService` provides single entry point for template rendering
- Method signature: `renderTemplate(key, locale, channel, variables): RenderedTemplate`
- Unit tests with mocked registry and renderers

#### Task 4.2: Integrate with Email Send Flow
- [ ] Update `EmailSendRequestInput` GraphQL input (add `templateKey`, `locale`)
- [ ] Update `EmailContentInput` to support `kind: TEMPLATE`
- [ ] Modify `EmailSendService` to call `TemplateService` when `kind: TEMPLATE`
- [ ] Maintain backward compatibility with `kind: RAW`

**Acceptance Criteria:**
- GraphQL mutation accepts `templateKey` and `variables`
- Email consumer receives rendered content in Kafka event
- Existing `RAW` emails continue to work

#### Task 4.3: Add Template Lookup GraphQL Query
- [ ] Add `listTemplates(channel: Channel): [TemplateInfo!]!` query
- [ ] Add `getTemplate(key: String!, channel: Channel!): TemplateDetail` query
- [ ] Return template metadata (key, variables, locales)

**Acceptance Criteria:**
- Developers can query available templates via GraphQL
- Query returns template metadata (not content)
- Unit tests for GraphQL resolvers

#### Task 4.4: Testing Utilities
- [ ] Create `TemplateTestUtils` for unit testing
- [ ] Provide `InMemoryTemplateRegistry` for tests
- [ ] Create fixture templates for testing
- [ ] Document testing patterns in developer guide

**Acceptance Criteria:**
- Test utilities allow easy template mocking
- Fixture templates available in `test/resources/`
- Documentation with examples

---

### Epic 5: Observability & Operations

**Goal:** Add metrics, logging, and operational tooling for templates.

**Tasks:**

#### Task 5.1: Metrics
- [ ] Add counter: `comms.template.render.count{key, channel, status}`
- [ ] Add histogram: `comms.template.render.duration{key, channel}`
- [ ] Add counter: `comms.template.errors{key, channel, error_type}`
- [ ] Add gauge: `comms.template.registry.size{channel}`

**Acceptance Criteria:**
- Metrics exposed via Micrometer (Prometheus format)
- Dashboards can track template usage and errors
- Metrics tested in integration tests

#### Task 5.2: Structured Logging
- [ ] Log template resolution (key, locale, fallback path)
- [ ] Log rendering errors with context (variables, template)
- [ ] Log validation errors at startup (file, line, error)
- [ ] Use structured logging (JSON) for operational queries

**Acceptance Criteria:**
- Logs include trace ID for correlation
- Error logs include actionable context
- Logs are JSON formatted for log aggregation

#### Task 5.3: Distributed Tracing
- [ ] Add template key to trace span attributes
- [ ] Create child span for template rendering
- [ ] Include locale and channel in span tags
- [ ] Integrate with existing tracing infrastructure

**Acceptance Criteria:**
- Template rendering visible in trace visualization
- Span attributes include template metadata
- Integration tests validate tracing

#### Task 5.4: Operational Runbook
- [ ] Document common template errors and resolutions
- [ ] Document hot reload procedure (dev mode)
- [ ] Document template deployment process
- [ ] Create troubleshooting guide

**Acceptance Criteria:**
- Runbook added to `docs/03-features/comms/template-engine-runbook.md`
- Common errors documented with solutions
- Deployment process documented

---

### Epic 6: Documentation & Examples

**Goal:** Provide comprehensive documentation for template authoring and usage.

**Tasks:**

#### Task 6.1: Template Authoring Guide
- [ ] Document YAML schema with examples
- [ ] Document variable substitution syntax (Mustache)
- [ ] Document channel-specific requirements and best practices
- [ ] Document security considerations (XSS, injection)

**Acceptance Criteria:**
- Guide published in `docs/03-features/comms/template-authoring-guide.md`
- Examples for each channel type
- Security warnings prominently displayed

#### Task 6.2: Example Templates
- [ ] Create `user.welcome` email template (en, pt-BR)
- [ ] Create `order.confirmation` email template (en, pt-BR)
- [ ] Create `chat.notification` push template (en, pt-BR)
- [ ] Create `whatsapp.reminder` WhatsApp template (en, pt-BR)

**Acceptance Criteria:**
- At least 4 production-ready example templates
- Templates demonstrate best practices
- Templates include comments and documentation

#### Task 6.3: API Documentation
- [ ] Document `TemplateService` API
- [ ] Document GraphQL mutations and queries
- [ ] Provide code examples (Kotlin, GraphQL)
- [ ] Document error codes and handling

**Acceptance Criteria:**
- API documentation in `docs/03-features/comms/template-api.md`
- Examples for common use cases
- Error handling documented

#### Task 6.4: Migration Guide
- [ ] Document migration from `RAW` to `TEMPLATE` emails
- [ ] Provide migration checklist
- [ ] Document backward compatibility guarantees
- [ ] Provide migration examples

**Acceptance Criteria:**
- Migration guide in `docs/03-features/comms/template-migration-guide.md`
- Checklist for developers
- Examples of before/after code

---

### Epic 7: Testing & Quality Assurance

**Goal:** Ensure template system is robust, tested, and reliable.

**Tasks:**

#### Task 7.1: Unit Tests
- [ ] Unit tests for `TemplateRegistry` (90%+ coverage)
- [ ] Unit tests for each `TemplateRenderer` (90%+ coverage)
- [ ] Unit tests for `VariableSubstitutor` (90%+ coverage)
- [ ] Unit tests for `TemplateValidator` (90%+ coverage)

**Acceptance Criteria:**
- Unit test coverage > 90% for template engine code
- Tests are fast (< 1s total)
- Tests use fixture templates

#### Task 7.2: Integration Tests
- [ ] Integration test: render email template end-to-end
- [ ] Integration test: locale fallback behavior
- [ ] Integration test: GraphQL mutation with template
- [ ] Integration test: template validation at startup

**Acceptance Criteria:**
- Integration tests run in CI/CD pipeline
- Tests use real Kafka (testcontainers)
- Tests validate full request-response cycle

#### Task 7.3: Performance Tests
- [ ] Benchmark template resolution (target: < 1ms)
- [ ] Benchmark variable substitution (target: < 10ms)
- [ ] Benchmark channel rendering (target: < 50ms)
- [ ] Load test: 1000 concurrent template renders

**Acceptance Criteria:**
- Performance targets met
- Load test passes without errors
- Benchmarks automated in CI

#### Task 7.4: Security Tests
- [ ] XSS attack test (script injection in variables)
- [ ] Path traversal test (malicious bodyPath)
- [ ] Large variable payload test (DoS prevention)
- [ ] Special character test (Unicode, emoji, HTML entities)

**Acceptance Criteria:**
- Security tests pass
- XSS vulnerabilities prevented
- DoS protections in place

---

## Success Criteria

### Definition of Done

The Template Registry & Template Engine is considered complete when:

1. **Functional Completeness**
   - All 5 channel renderers implemented (Email, Push, WhatsApp, In-App, Chat)
   - Template registry supports key + locale + channel lookup
   - Variable substitution with Mustache working
   - i18n locale fallback implemented
   - GraphQL API supports `TEMPLATE` content kind

2. **Quality Assurance**
   - Unit test coverage > 90%
   - Integration tests pass
   - Performance benchmarks meet targets
   - Security tests pass

3. **Documentation**
   - Template authoring guide published
   - API documentation complete
   - Example templates created
   - Migration guide available

4. **Operational Readiness**
   - Metrics and logging integrated
   - Distributed tracing working
   - Runbook published
   - Templates validated at startup

5. **Backward Compatibility**
   - Existing `RAW` email flow unaffected
   - No breaking changes to GraphQL API

### Validation Scenarios

**Scenario 1: Send Welcome Email**
```kotlin
// Developer calls:
emailService.requestSend(
    to = "user@example.com",
    content = TemplateContent(
        templateKey = "user.welcome",
        variables = mapOf(
            "userName" to "João",
            "verificationUrl" to "https://app.neotool.io/verify?token=abc123"
        )
    ),
    locale = Locale.of("pt-BR")
)

// Expected:
// - Template resolved: user.welcome, pt-BR, EMAIL
// - Variables substituted: {{userName}} → João
// - Email rendered with inlined CSS
// - Kafka event produced
// - Email sent via SMTP
```

**Scenario 2: Send WhatsApp Reminder (Fallback Locale)**
```kotlin
// Developer calls:
whatsappService.requestSend(
    to = "+5511999999999",
    content = TemplateContent(
        templateKey = "appointment.reminder",
        variables = mapOf("appointmentTime" to "14:00", "doctorName" to "Dr. Silva")
    ),
    locale = Locale.of("es-MX")  // Template only exists in en, pt
)

// Expected:
// - Template resolved: appointment.reminder, es-MX → fallback to en
// - Variables substituted correctly
// - WhatsApp markdown applied
// - Character limit enforced
// - Message sent via WhatsApp Business API
```

**Scenario 3: List Available Templates**
```graphql
query {
  listTemplates(channel: EMAIL) {
    key
    name
    variables {
      name
      type
      required
    }
    supportedLocales
  }
}

# Expected:
# - Returns all email templates
# - Includes metadata for each template
# - Developers can discover available templates
```

## Timeline Estimate

**Note:** Timeline estimates are provided for planning purposes only and should be validated against team capacity and priorities.

- **Epic 1 (Template Registry Foundation):** 2-3 weeks
- **Epic 2 (Variable Substitution Engine):** 1-2 weeks
- **Epic 3 (Channel-Specific Renderers):** 3-4 weeks
- **Epic 4 (Template Service Integration):** 2 weeks
- **Epic 5 (Observability & Operations):** 1 week
- **Epic 6 (Documentation & Examples):** 1-2 weeks
- **Epic 7 (Testing & Quality Assurance):** Ongoing (parallel with development)

**Total:** 10-14 weeks (2.5-3.5 months) with 1 full-time engineer, or 5-7 weeks with 2 engineers pairing/parallelizing.

## Dependencies

### Internal Dependencies
- Kafka infrastructure (already in place)
- GraphQL API framework (already in place)
- Existing email sending infrastructure (EmailSendService, EmailProvider)

### External Dependencies
- `com.github.spullara.mustache.java:compiler` (Mustache templating)
- `org.jsoup:jsoup` (HTML parsing for email)
- `com.helger:ph-css` (CSS inlining for email)
- `org.yaml:snakeyaml` (YAML parsing, likely already a dependency)

### Future Dependencies (Not in Scope)
- Database for dynamic template management (Phase N)
- Template versioning system (Phase N)
- A/B testing framework (Phase N)

## Risks & Mitigations

### Risk 1: Template Complexity Creep
**Risk:** Developers add complex logic to templates, making them hard to maintain.
**Mitigation:** Use logic-less Mustache. Document anti-patterns. Code review template PRs.

### Risk 2: CSS Inlining Performance
**Risk:** CSS inlining for emails is slow, impacting throughput.
**Mitigation:** Benchmark early. Cache inlined templates if needed. Optimize email templates.

### Risk 3: Variable Injection Attacks
**Risk:** User-provided variables contain malicious content (XSS, injection).
**Mitigation:** Escape variables by default. Security testing. Input validation at API boundary.

### Risk 4: Locale Explosion
**Risk:** Supporting many locales increases template maintenance burden.
**Mitigation:** Start with 2-3 locales (en, pt-BR, pt). Add more only when needed. Use shared components.

### Risk 5: Template Deployment Friction
**Risk:** Updating templates requires full redeployment, slowing iteration.
**Mitigation:** Hot reload in dev mode. Plan for database-backed templates in future. Accept trade-off for Phase 3.

## Future Enhancements (Out of Scope)

### Phase N: Database-Backed Templates
- Store templates in database for dynamic updates
- Template versioning and rollback
- A/B testing infrastructure
- Template approval workflows

### Phase N: Advanced Personalization
- Dynamic content based on user segments
- Recommendation engine integration
- Real-time data fetching in templates

### Phase N: Visual Template Editor
- WYSIWYG editor for non-technical users
- Template preview with live variables
- Template marketplace/library

### Phase N: Analytics & Optimization
- Template engagement metrics (open rates, click rates)
- A/B testing results and statistical significance
- Automatic template optimization recommendations

### Phase N: Governance & Compliance
- Template approval workflows
- Audit logging for template changes
- Compliance checks (GDPR, CAN-SPAM)
- Cost control and quotas per template

## Conclusion

This document defines a comprehensive, production-ready Template Registry & Template Engine for the Neotool Comms module. The design is informed by industry best practices from Netflix, Uber, Spotify, and modern notification systems, adapted to our specific requirements and constraints.

The proposed solution is:
- **Clean:** Clear separation of concerns, logic-less templates, declarative definitions
- **Robust:** Type-safe variable substitution, comprehensive validation, security-first design
- **Scalable:** Stateless registry, channel-independent rendering, horizontal scaling ready
- **Maintainable:** Version control friendly, testable components, clear documentation
- **Extensible:** Easy to add new channels, locales, and rendering features

The implementation is broken down into 7 epics with clear acceptance criteria and success metrics. The backlog provides a concrete execution plan for delivering this capability incrementally.

## References

### Industry Research Sources

- [Comparing Push Notification Systems: Uber's RAMEN vs. Netflix's RENO](https://www.tothenew.com/blog/comparing-push-notification-systems-ubers-ramen-vs-netflixs-reno/)
- [What Netflix, Spotify, and Uber teach us about Cloud Architecture](https://www.dynamixcloud.com/blog/what-netflix-spotify-and-uber-teach-us-about-cloud-architecture/)
- [The Power of Event-Driven Architecture: How Netflix and Uber Handle Billions of Events Daily](https://developerport.medium.com/the-power-of-event-driven-architecture-how-netflix-and-uber-handle-billions-of-events-daily-0a2d09d7308c)
- [System Design: Lessons From Netflix's Notification Service Design](https://ravisystemdesign.substack.com/p/system-design-lessons-from-netflixs)
- [Multi-Channel Notification System Guide | 2026 | NotiGrid](https://notigrid.com/blog/how-to-build-multi-channel-notification-system)
- [How to Design a Notification System: A Complete Guide](https://www.systemdesignhandbook.com/guides/design-a-notification-system/)
- [Notification System Design: Architecture & Best Practices](https://www.magicbell.com/blog/notification-system-design)
- [Top 6 Design Patterns for Building Effective Notification Systems](https://www.suprsend.com/post/top-6-design-patterns-for-building-effective-notification-systems-for-developers)
- [Internationalization(i18n) and Localization(L10n)](https://www.bmpi.dev/en/dev/i18n-l10n/)
- [What is i18n? (The 2026 Edition) - Locize Blog](https://www.locize.com/blog/what-is-i18n/)
- [Going Global: a Deep Dive to Build an Internationalization Framework](https://www.infoq.com/articles/internationalization-framework/)
- [Localization Strategy: Building a Scalable, Global Content Operation](https://www.contentstack.com/cms-guides/localization-strategy-global-content-operation)

---

**Document Version:** 1.0
**Last Updated:** 2026-02-02
**Owner:** Comms Team
**Status:** Draft - Ready for Review
