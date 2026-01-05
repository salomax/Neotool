# Feature: [Feature Name]

## Problem

[Describe the business problem or user need this feature addresses. Include context like current pain points, user requests, or business drivers.]

Example:
- Users currently cannot update their profile information after signup
- Customer support receives 50+ requests/month for manual profile changes
- This creates friction and support overhead

## Solution

[High-level description of what you're building to solve the problem.]

Example:
- Self-service profile management interface
- Real-time validation and updates
- Audit trail for compliance

## Scope

### In Scope
- [What will be included in this feature]
- [Specific capabilities or components]
- [User stories or use cases]

### Out of Scope
- [What won't be included (but might be confused with this feature)]
- [Future enhancements that are separate]
- [Related features that already exist]

## Success Criteria

[Measurable outcomes that define "done". These become your validation checklist.]

1. [Criterion 1 - specific and testable]
2. [Criterion 2 - specific and testable]
3. [Criterion 3 - specific and testable]

Example:
1. User can view their current profile data
2. User can update name, email, and avatar
3. Email changes validate uniqueness before saving
4. All profile changes are logged to audit trail
5. Profile updates trigger analytics events

## Technical Constraints

[Non-obvious technical requirements or limitations that affect implementation.]

- [Integration requirements]
- [Performance requirements]
- [Security requirements]
- [Data constraints]

Example:
- Must use existing `AuthContext` for user identification
- Avatar files limited to 5MB, stored in existing bucket system
- Email changes require synchronous validation (no eventual consistency)
- Profile data must be encrypted at rest

## Integration Points

[Where this feature connects to existing code. This tells the LLM where to explore.]

**[System/Service Name]**
- Path: `/path/to/existing/code`
- Purpose: [What to learn from this code]
- Pattern: [What pattern to follow]

Example:

**Authentication**
- Path: `/service/kotlin/security/service/AuthenticationService`
- Purpose: User identification and context
- Pattern: Use `@RequirePermission` annotation

**Storage**
- Path: `/service/kotlin/asset/service/BucketService`
- Purpose: File upload handling
- Pattern: Follow bucket selection and validation logic

**Audit Trail**
- Path: `/service/kotlin/asset/service/AssetService`
- Purpose: Change logging pattern
- Pattern: Use `AuditService` for all mutations

## Data Model

[If this feature requires new entities or changes to existing ones, describe them here.]

### New Entities
```
UserProfile
├── id: UUID (v7)
├── userId: UUID (FK to User)
├── displayName: String
├── avatarUrl: String?
├── bio: String?
├── updatedAt: Timestamp
└── updatedBy: UUID
```

### Modified Entities
[List any changes to existing entities]

### Migrations
- [Required database changes]
- [Data migrations needed]

## Security Considerations

[Security requirements and considerations]

- [Authentication requirements]
- [Authorization rules]
- [Data privacy concerns]
- [Input validation needs]

Example:
- Only profile owner can edit their profile
- Admin role can view any profile (read-only)
- Email changes require current password confirmation
- Avatar uploads must be scanned for malware

## Open Decisions

[Questions that need answers before or during implementation]

- [ ] [Decision 1] → **Decision**: [Answer once decided]
- [ ] [Decision 2] → **Decision**: [Answer once decided]

Example:
- [ ] Email verification on change? → **Decision**: Yes, send verification email but allow immediate use
- [ ] Avatar resize: client or server? → **Decision**: Server-side resize to 200x200 and 800x800
- [ ] Rate limiting on profile updates? → **Decision**: Max 5 updates per hour per user

## References

**Similar Features**
- [Link to similar feature implementation]
- [Link to related patterns]

**Architecture Decisions**
- [ADR-XXX: Relevant decision]
- [ADR-YYY: Related decision]

**Domain Documentation** (for context, not LLM loading)
- [Link to relevant docs for human reference]

Example:

**Similar Features**
- Asset management: `/service/kotlin/asset/` (CRUD with audit trail)
- User settings: `/service/kotlin/security/user/` (user-owned resources)

**Architecture Decisions**
- ADR-023: Event-driven audit logging
- ADR-015: UUID v7 for all entities

**Domain Documentation**
- `docs/domain/user-model.md` (for understanding user concepts)
- `docs/patterns/crud-pattern.md` (for human reference on CRUD patterns)

---

## Context for AI

**Documentation to Load:**
- [docs/99-ai-context/guardrails.md](../../docs/99-ai-context/guardrails.md) - Security, testing rules (MANDATORY)
- [docs/02-architecture/backend-structure.md](../../docs/02-architecture/backend-structure.md) - Backend patterns
- [docs/02-architecture/frontend-structure.md](../../docs/02-architecture/frontend-structure.md) - Frontend patterns
- [Add other relevant docs based on feature type]

**Patterns to Follow:**
- [ ] Backend CRUD: `docs/99-ai-context/examples.md#example-1-complete-crud-with-audit-trail`
- [ ] GraphQL API: `docs/99-ai-context/examples.md#example-2-authentication--authorization`
- [ ] Frontend Form: `docs/99-ai-context/examples.md#example-4-form-with-validation`
- [ ] Add/remove based on feature type

**Code Integration Points (Explore These):**
- `/service/kotlin/asset/service/AssetService.kt` - CRUD + audit pattern
- `/service/kotlin/security/service/AuthenticationService.kt` - Auth integration
- `/client/src/features/settings/` - Similar UI patterns

**Verification:**
If documentation and code conflict, **code is the source of truth**. Verify unclear patterns against actual source code.

---

## For LLM Implementation

**Implementation Steps:**
1. ✅ Read this spec completely
2. ✅ Load context docs from "Context for AI" section above
3. ✅ **ALWAYS load guardrails** (`docs/99-ai-context/guardrails.md`) - MANDATORY
4. ✅ Explore code integration points listed above
5. ✅ Understand patterns from existing code
6. ✅ Implement following discovered patterns + guardrails
7. ✅ Validate against success criteria
8. ✅ Ensure guardrails compliance (security, testing, architecture)

**Priority Order:**
```
Guardrails > This Spec > Pattern Docs > Code Examples
```

If implementation violates guardrails → STOP and ask for clarification.
