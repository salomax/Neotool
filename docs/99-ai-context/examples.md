---
title: AI Context - Reference Implementation Examples
type: ai-guide
category: examples
status: current
version: 1.0.0
date: 2026-01-04
tags: [ai, llm, examples, patterns, reference]
---

# Reference Implementation Examples

> **Purpose**: Point AI to the best examples of each pattern in the codebase. These are annotated pointers, not full code copies.

## Overview

When implementing a feature, AI should **learn from actual code**, not hallucinate patterns. This document points to the best examples of each pattern in the codebase.

**Key principle:** Code is the source of truth. These examples represent best practices to copy.

---

## Backend Examples

### Example 1: Complete CRUD with Audit Trail

**What it demonstrates:**
- Full CRUD operations (create, read, update, delete)
- Input validation
- Audit trail logging
- Authorization with `@RequirePermission`
- Transactional operations
- UUID v7 for IDs
- 90%+ test coverage

**Source code:**
```
/service/kotlin/asset/
├── entity/Asset.kt                    # Entity with UUID v7
├── entity/AssetAudit.kt              # Audit trail entity
├── repository/AssetRepository.kt      # JPA repository
├── repository/AssetAuditRepository.kt
├── service/AssetService.kt           # Business logic, validation
└── resolver/AssetResolver.kt         # GraphQL with permissions
```

**Key patterns to copy:**

1. **Entity with UUID v7:**
```kotlin
@Entity
@Table(name = "asset")
class Asset(
    @Id
    val id: UUID = UuidV7Generator.generate(),  // ← Use UUID v7
    // ... other fields
)
```

2. **Service with validation:**
```kotlin
@Singleton
class AssetService(
    private val repository: AssetRepository,
    private val auditRepository: AssetAuditRepository,
    private val principalProvider: RequestPrincipalProvider
) {
    @Transactional
    fun create(input: CreateAssetInput): Asset {
        validateInput(input)  // ← Validate first
        val userId = principalProvider.getCurrentUserId()  // ← Get current user
        val asset = Asset(...)
        return repository.save(asset)
    }
}
```

3. **Resolver with permissions:**
```kotlin
@Singleton
class AssetResolver(...) : GraphQLResolver {
    @RequirePermission("asset:create:own")  // ← Always require permission
    fun createAsset(input: CreateAssetInput): Asset {
        return assetService.create(input)
    }
}
```

**When to use as reference:**
- Any CRUD feature
- Features requiring audit trails
- Features with authorization

**Tests to reference:**
- `/service/kotlin/asset/src/test/.../AssetServiceTest.kt`
- `/service/kotlin/asset/src/test/.../AssetResolverTest.kt`

---

### Example 2: Authentication & Authorization

**What it demonstrates:**
- JWT token generation
- Refresh token rotation
- Permission checking
- Getting current user context
- OAuth integration

**Source code:**
```
/service/kotlin/security/
├── service/AuthenticationService.kt   # Login, token generation
├── service/RefreshTokenService.kt     # Token refresh
└── service/AuthorizationService.kt    # Permission checking
```

**Key patterns to copy:**

1. **Getting current user:**
```kotlin
class SomeService(
    private val principalProvider: RequestPrincipalProvider
) {
    fun doSomething() {
        val userId = principalProvider.getCurrentUserId()  // ← Always use this
        // ... use userId
    }
}
```

2. **Permission format:**
```kotlin
@RequirePermission("resource:action:scope")
// Examples:
// "asset:create:own"
// "user:read:any"
// "team:update:own"
```

**When to use as reference:**
- Any feature requiring authentication
- Authorization checks
- User context access

---

### Example 3: GraphQL Federation

**What it demonstrates:**
- Extending types from other services
- Federation directives
- Resolver delegation

**Source code:**
```
/service/kotlin/asset/src/main/resources/schema/asset.graphqls
/service/kotlin/asset/resolver/AssetResolver.kt
```

**Key patterns to copy:**

1. **Extending types:**
```graphql
extend type User @key(fields: "id") {
    assets: [Asset!]!
}
```

2. **Federation resolver:**
```kotlin
fun User.assets(dataFetchingEnvironment: DataFetchingEnvironment): List<Asset> {
    return assetService.findByUserId(this.id)
}
```

**When to use as reference:**
- GraphQL schema design
- Cross-service relationships

---

## Frontend Examples

### Example 4: Form with Validation

**What it demonstrates:**
- React Hook Form integration
- Zod schema validation
- GraphQL mutations
- Error handling
- Toast notifications
- Accessibility

**Source code:**
```
/client/src/features/settings/
├── components/SettingsForm.tsx        # Form component
├── hooks/useSettings.ts               # Custom hook with mutation
└── operations/settings.graphql        # GraphQL operations
```

**Key patterns to copy:**

1. **Form with validation:**
```typescript
const schema = z.object({
    displayName: z.string().min(1).max(100),
    email: z.string().email(),
});

export function SettingsForm() {
    const { register, handleSubmit, formState: { errors } } = useForm({
        resolver: zodResolver(schema),
    });

    return (
        <form onSubmit={handleSubmit(onSubmit)}>
            <Input {...register('displayName')} />
            {errors.displayName && <ErrorAlert message={errors.displayName.message} />}
        </form>
    );
}
```

2. **Custom hook with mutation:**
```typescript
export function useSettings() {
    const { showSuccess, showError } = useNotification();
    const [mutate, { loading }] = useMutation(UpdateSettingsDocument, {
        onCompleted: () => showSuccess('Settings updated'),
        onError: (error) => showError(error.message),
    });

    return { updateSettings: mutate, loading };
}
```

**When to use as reference:**
- Any form implementation
- Data mutations
- User feedback (toasts, errors)

---

### Example 5: Data Fetching & Display

**What it demonstrates:**
- GraphQL queries
- Loading states
- Error handling
- Data display components

**Source code:**
```
/client/src/features/settings/
├── hooks/useSettings.ts
├── components/SettingsDisplay.tsx
└── page.tsx
```

**Key patterns to copy:**

1. **Query hook:**
```typescript
export function useSettings() {
    const { data, loading, error, refetch } = useQuery(SettingsDocument);

    return {
        settings: data?.settings,
        loading,
        error,
        refetch,
    };
}
```

2. **Handling states:**
```typescript
export function SettingsPage() {
    const { settings, loading, error } = useSettings();

    if (loading) return <Spinner />;
    if (error) return <ErrorAlert message={error.message} />;
    if (!settings) return <EmptyState />;

    return <SettingsDisplay settings={settings} />;
}
```

**When to use as reference:**
- Data fetching
- Loading/error states
- Component composition

---

## Database Examples

### Example 6: Migration with Audit Table

**What it demonstrates:**
- Table creation
- Indexes
- Foreign keys
- Audit table pattern

**Source code:**
```
/service/kotlin/asset/src/main/resources/db/migration/V001__create_asset.sql
```

**Key patterns to copy:**

1. **Main table:**
```sql
CREATE TABLE asset (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_asset_owner_id ON asset(owner_id);
```

2. **Audit table:**
```sql
CREATE TABLE asset_audit (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES asset(id),
    field_name VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    changed_by UUID NOT NULL
);

CREATE INDEX idx_asset_audit_asset_id ON asset_audit(asset_id);
```

**When to use as reference:**
- Creating new tables
- Adding audit trails
- Indexing strategy

---

## Testing Examples

### Example 7: Service Unit Tests

**What it demonstrates:**
- Testing validation logic
- Testing business rules
- Mocking dependencies
- Testing edge cases
- 90%+ coverage

**Source code:**
```
/service/kotlin/asset/src/test/.../AssetServiceTest.kt
```

**Key patterns to copy:**

1. **Validation tests:**
```kotlin
@Test
fun `should reject invalid input`() {
    assertThrows<ValidationException> {
        service.create(CreateAssetInput(name = ""))  // Empty name
    }
}
```

2. **Business logic tests:**
```kotlin
@Test
fun `should create audit trail on update`() {
    val asset = service.create(validInput)
    service.update(asset.id, UpdateAssetInput(name = "New Name"))

    val audits = auditRepository.findByAssetId(asset.id)
    assertEquals(1, audits.size)
    assertEquals("name", audits[0].fieldName)
}
```

**When to use as reference:**
- Testing services
- Validation testing
- Business logic coverage

---

### Example 8: Integration Tests

**What it demonstrates:**
- Testing GraphQL resolvers
- Testing with database
- Testing permissions

**Source code:**
```
/service/kotlin/asset/src/test/.../AssetResolverTest.kt
```

**Key patterns to copy:**

1. **Permission tests:**
```kotlin
@Test
fun `should require permission to create asset`() {
    // Without permission
    assertThrows<UnauthorizedException> {
        resolver.createAsset(input)
    }

    // With permission
    withPermission("asset:create:own") {
        val result = resolver.createAsset(input)
        assertNotNull(result)
    }
}
```

**When to use as reference:**
- Testing resolvers
- Integration testing
- Permission testing

---

### Example 9: Frontend Component Tests

**What it demonstrates:**
- Testing component rendering
- Testing user interactions
- Testing form validation
- Testing GraphQL mocks

**Source code:**
```
/client/src/features/settings/components/__tests__/SettingsForm.test.tsx
```

**Key patterns to copy:**

1. **Component rendering test:**
```typescript
test('renders form fields', () => {
    render(<SettingsForm />);
    expect(screen.getByLabelText('Display Name')).toBeInTheDocument();
});
```

2. **Interaction test:**
```typescript
test('shows validation error on invalid input', async () => {
    render(<SettingsForm />);
    await userEvent.type(screen.getByLabelText('Display Name'), 'a'.repeat(101));
    await userEvent.click(screen.getByRole('button', { name: /save/i }));
    expect(screen.getByText(/cannot exceed 100 characters/i)).toBeInTheDocument();
});
```

**When to use as reference:**
- Testing components
- Testing user interactions
- Testing validation

---

### Example 10: E2E Tests

**What it demonstrates:**
- Full user flows
- Multiple steps
- Real browser testing

**Source code:**
```
/e2e/tests/settings.spec.ts
```

**Key patterns to copy:**

```typescript
test('user can update settings', async ({ page }) => {
    await page.goto('/settings');
    await page.fill('[name="displayName"]', 'New Name');
    await page.click('button:has-text("Save")');
    await expect(page.locator('text=Settings updated')).toBeVisible();
});
```

**When to use as reference:**
- Testing complete flows
- Integration testing across stack

---

## Common Patterns Summary

| Pattern | Example | File |
|---------|---------|------|
| CRUD with Audit | AssetService | asset/service/AssetService.kt |
| Authentication | AuthenticationService | security/service/AuthenticationService.kt |
| Authorization | Permission annotations | Any resolver |
| GraphQL Federation | Asset schema | asset/schema/asset.graphqls |
| Form Validation | SettingsForm | settings/components/SettingsForm.tsx |
| Data Fetching | useSettings | settings/hooks/useSettings.ts |
| Database Migration | Asset migration | asset/.../V001__create_asset.sql |
| Service Tests | AssetServiceTest | asset/.../AssetServiceTest.kt |
| Integration Tests | AssetResolverTest | asset/.../AssetResolverTest.kt |
| Component Tests | SettingsForm test | settings/.../SettingsForm.test.tsx |
| E2E Tests | Settings E2E | e2e/tests/settings.spec.ts |

---

## How to Use These Examples

### Step 1: Identify Pattern

Match your task to a pattern:
- Building CRUD? → AssetService
- Adding auth? → AuthenticationService
- Creating form? → SettingsForm
- etc.

### Step 2: Read the Example

Navigate to the source code and read:
- The implementation
- The tests
- The patterns used

### Step 3: Copy the Pattern

Apply the same structure to your feature:
- Same file organization
- Same naming conventions
- Same testing approach
- Same security patterns

### Step 4: Verify

Check that your implementation:
- Follows the same structure
- Uses the same libraries
- Has similar test coverage
- Passes guardrails

---

## Anti-Patterns to Avoid

### ❌ Don't Copy Bad Examples

Not all code is reference-quality. Copy from the examples listed here, not random files.

### ❌ Don't Hallucinate Patterns

If you don't see a pattern in these examples or the actual code, **ask the user** instead of inventing one.

### ❌ Don't Skip Tests

Every example has tests. Your implementation should too.

### ❌ Don't Ignore Updates

If these examples are updated, your new code should follow the new patterns, not old ones.

---

## Maintenance

**When to update this file:**
- Better examples are created
- Patterns evolve
- New pattern types emerge

**Who can update:**
- Any developer can suggest better examples
- Tech leads approve changes

---

**Version**: 1.0.0
**Date**: 2026-01-04
**Maintained by**: Engineering Team

*These are living examples. As the codebase improves, update this guide.*
