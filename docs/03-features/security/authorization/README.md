# Authorization Feature - Comprehensive Documentation

## Table of Contents

1. [Overview](#overview)
2. [Functional Requirements](#functional-requirements)
3. [Architecture](#architecture)
4. [Implementation Details](#implementation-details)
5. [Testing Guide](#testing-guide)
6. [Future Improvements](#future-improvements)

---

## Overview

The Authorization feature implements a **hybrid RBAC (Role-Based Access Control) + ABAC (Attribute-Based Access Control)** model to provide fine-grained, enterprise-grade authorization across all modules of the application.

### Key Concepts

- **RBAC**: Defines *who* can perform certain *actions* through roles and permissions
- **ABAC**: Defines *under which conditions* an action can be performed *on a specific resource* using attribute-based policies
- **Hybrid Model**: RBAC is evaluated first; if allowed, ABAC policies are then evaluated. Explicit deny from ABAC overrides allow.

### Design Principles

- **Deny-by-default**: Access is denied unless explicitly granted
- **Explicit deny overrides allow**: ABAC deny policies take precedence
- **Short-circuit evaluation**: If RBAC denies, ABAC is not evaluated
- **Comprehensive audit logging**: All authorization decisions are logged
- **Performance optimized**: Caching and batch operations for scalability

---

## Functional Requirements

### 1. RBAC (Role-Based Access Control)

#### 1.1 Core Entities

- **User**: Individual users in the system
- **Role**: Collection of permissions (e.g., "editor", "viewer")
- **Permission**: Granular action (e.g., "transaction:read", "user:manage")
- **Group**: Collection of users that can have roles assigned

#### 1.2 Role Assignment

- Users can have roles assigned **directly**
- Users can inherit roles through **group membership**
- Users can have both direct roles and inherited roles
- Role bindings support **temporary access** with `valid_from` and `valid_until` dates

#### 1.3 Permission Inheritance

- Users inherit all permissions from their direct roles
- Users inherit all permissions from roles assigned to their groups
- Permissions are aggregated from all sources (direct + inherited)

#### 1.4 Permission Format

- Permissions follow the pattern: `resource:action`
- Examples: `transaction:read`, `user:manage`, `security:group:save`
- Format validation: `^[a-z0-9_-]+:[a-z0-9_-]+$`
- Maximum length: 255 characters

### 2. ABAC (Attribute-Based Access Control)

#### 2.1 Attribute Sources

- **Subject Attributes**: User ID, groups, roles, permissions, custom attributes
- **Resource Attributes**: Resource type, ID, owner, creator, status, custom attributes
- **Context Attributes**: IP address, request time, environment, custom context

#### 2.2 Policy Structure

- **Effect**: `ALLOW` or `DENY`
- **Condition**: JSON-based expression with logical operators
- **Active Status**: Policies can be enabled/disabled
- **Priority**: Explicit deny overrides allow

#### 2.3 Condition Operators

Supported operators in ABAC conditions:

- **Logical**: `and`, `or`, `not`
- **Comparison**: `eq` (equals), `ne` (not equals)
- **Numeric**: `gt` (greater than), `gte` (greater than or equal), `lt` (less than), `lte` (less than or equal)
- **Membership**: `in` (check if value is in array)

#### 2.4 Condition Format

Conditions are JSON expressions:

```json
{
  "and": [
    {"eq": {"subject.userId": "123"}},
    {"or": [
      {"eq": {"resource.ownerId": "123"}},
      {"in": {"subject.groups": ["group1", "group2"]}}
    ]}
  ]
}
```

### 3. Hybrid Evaluation Flow

1. **RBAC Check**: Evaluate if user has required permission through roles
   - If denied → Short-circuit, return denied
   - If allowed → Continue to ABAC evaluation

2. **ABAC Evaluation**: Evaluate all active policies
   - Build subject, resource, and context attributes
   - Match policies against conditions
   - Determine final decision (explicit deny overrides allow)

3. **Audit Logging**: Log decision with RBAC result, ABAC result, and final decision

### 4. Resource Ownership Patterns

#### 4.1 Read-Global, Write-Limited

- Users can **read** all resources they have permission for
- Users can **write** only:
  - Resources they created
  - Resources created by their groups
  - Resources in specific statuses (e.g., "draft")

#### 4.2 Ownership-Based Access

- Resources track `created_by_user_id` and `created_by_group_id`
- ABAC policies can check ownership:
  ```json
  {
    "or": [
      {"eq": {"resource.created_by_user_id": "subject.userId"}},
      {"in": {"resource.created_by_group_id": "subject.groups"}}
    ]
  }
  ```

### 5. Temporary Access

- Role bindings support `valid_from` and `valid_until` dates
- Access is automatically denied outside the valid date range
- Useful for:
  - Incident response
  - Operational support
  - Time-limited projects

### 6. Audit Logging

Every authorization decision generates an audit log entry with:

- `userId`: User making the request
- `groups`: User's groups
- `roles`: User's roles
- `requestedAction`: Permission being checked
- `resourceType`: Type of resource (if applicable)
- `resourceId`: Resource ID (if applicable)
- `rbacResult`: RBAC evaluation result (ALLOWED/DENIED)
- `abacResult`: ABAC evaluation result (ALLOWED/DENIED/null)
- `finalDecision`: Final authorization decision
- `metadata`: Additional context (reasons, matched policies, etc.)
- `timestamp`: When the decision was made

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │PermissionGate│  │Authorization │  │ GraphQL      │     │
│  │  Component   │  │  Provider    │  │  Queries     │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  GraphQL API Layer                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         AuthorizationResolver                        │  │
│  │  - checkPermission(userId, permission, resourceId)   │  │
│  │  - getUserPermissions(userId)                        │  │
│  │  - getUserRoles(userId)                               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              HTTP Interceptor Layer                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │      AuthorizationInterceptor                        │  │
│  │  - @RequiresAuthorization annotation                │  │
│  │  - Automatic permission checking                     │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Service Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │Authorization│  │   ABAC       │  │   Audit      │     │
│  │   Manager   │  │  Evaluation  │  │   Service    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│         │                  │                  │           │
│         └──────────────────┼──────────────────┘           │
│                            │                               │
│                  ┌─────────▼─────────┐                    │
│                  │ Authorization     │                    │
│                  │ Service           │                    │
│                  └───────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Repository Layer                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │  User    │ │  Role    │ │Permission│ │  Group   │      │
│  │Repository│ │Repository│ │Repository│ │Repository│      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
│  ┌──────────┐ ┌──────────┐                                 │
│  │  ABAC    │ │  Audit   │                                 │
│  │  Policy  │ │  Log     │                                 │
│  │Repository│ │Repository│                                │
│  └──────────┘ └──────────┘                                 │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Request Arrives**: User makes request (GraphQL query/mutation or HTTP endpoint)
2. **Authentication**: Token is validated, principal is extracted
3. **Authorization Check**: 
   - HTTP: `AuthorizationInterceptor` checks `@RequiresAuthorization` annotation
   - GraphQL: Resolver calls `AuthorizationManager.require()`
4. **RBAC Evaluation**: `AuthorizationService` checks if user has permission through roles
5. **ABAC Evaluation**: If RBAC allows, `AbacEvaluationService` evaluates policies
6. **Decision**: Final decision is made (explicit deny overrides allow)
7. **Audit Logging**: Decision is logged to audit service
8. **Response**: Access granted or `AuthorizationDeniedException` thrown

---

## Implementation Details

### Backend Implementation

#### 1. AuthorizationService

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AuthorizationService.kt`

**Key Methods**:

- `checkPermission(userId, permission)`: Simple RBAC-only check
- `checkPermission(userId, permission, resourceType, resourceId, ...)`: Full hybrid RBAC+ABAC check
- `getUserPermissions(userId)`: Get all permissions for a user
- `getUserRoles(userId)`: Get all roles for a user

**Optimizations**:

- Batch loading to avoid N+1 queries
- Lightweight permission checks using `existsPermissionForRoles()`
- Single user context fetch for both RBAC and ABAC
- Caching of role IDs and permissions

#### 2. AbacEvaluationService

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AbacEvaluationService.kt`

**Key Features**:

- JSON-based condition evaluation
- Support for nested AND/OR/NOT operators
- Attribute path resolution (e.g., `subject.userId`, `resource.ownerId`)
- Safety limits: Max condition size (10KB), max recursion depth (10)
- Error handling: Invalid policies are logged but don't break evaluation

**Condition Evaluation**:

```kotlin
// Example condition
{
  "and": [
    {"eq": {"subject.department": "finance"}},
    {"or": [
      {"eq": {"resource.ownerId": "subject.userId"}},
      {"in": {"subject.groups": ["finance-team"]}}
    ]}
  ]
}
```

#### 3. AuthorizationManager

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/AuthorizationManager.kt`

**Purpose**: High-level interface for request-level authorization

**Key Features**:

- Enriches subject attributes with permissions from token
- Throws `AuthorizationDeniedException` if denied
- Logs authorization decisions for observability

#### 4. HTTP Interceptor

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/AuthorizationInterceptor.kt`

**Usage**:

```kotlin
@RequiresAuthorization(permission = "security:user:view")
fun getUser(id: String): User {
    // Method implementation
}
```

**How it works**:

1. Intercepts method calls with `@RequiresAuthorization` annotation
2. Extracts token from HTTP request
3. Validates token and creates principal
4. Calls `AuthorizationManager.require()`
5. Proceeds if allowed, throws exception if denied

#### 5. GraphQL Resolver

**Location**: `service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/graphql/resolver/AuthorizationResolver.kt`

**Queries**:

- `checkPermission(userId: ID!, permission: String!, resourceId: ID)`: Check if user has permission
- `getUserPermissions(userId: ID!)`: Get all permissions for user
- `getUserRoles(userId: ID!)`: Get all roles for user

**Validation**:

- Permission format validation (resource:action pattern)
- UUID validation for user and resource IDs
- Input sanitization and length limits
- Error handling with sanitized error messages

### Frontend Implementation

#### 1. PermissionGate Component

**Location**: `web/src/shared/components/authorization/PermissionGate.tsx`

**Usage**:

```tsx
<PermissionGate require="security:user:read">
  <UserList />
</PermissionGate>
```

**Features**:

- Checks permissions from `AuthorizationProvider`
- Shows/hides content based on permissions
- Handles loading states
- Supports multiple permissions (all must match)

#### 2. AuthorizationProvider

**Location**: `web/src/shared/providers/AuthorizationProvider.tsx`

**Purpose**: Provides authorization context to React components

**Features**:

- Fetches user permissions from GraphQL
- Caches permissions in memory
- Provides `hasPermission()` utility function
- Handles loading and error states

#### 3. GraphQL Queries

**Location**: `web/src/lib/graphql/operations/authorization/queries.ts`

**Queries**:

- `CheckPermission`: Check if user has specific permission
- Permissions are also included in `currentUser` query

### Database Schema

#### Core Tables

- `users`: User accounts
- `roles`: Role definitions
- `permissions`: Permission definitions
- `groups`: Group definitions
- `role_permissions`: Many-to-many: roles ↔ permissions
- `user_roles`: Many-to-many: users ↔ roles (with valid_from/valid_until)
- `group_roles`: Many-to-many: groups ↔ roles
- `group_memberships`: Many-to-many: users ↔ groups (with valid_from/valid_until)
- `abac_policies`: ABAC policy definitions
- `authorization_audit_logs`: Audit log entries

#### Key Relationships

```
User ──┬──> UserRole ──> Role ──> RolePermission ──> Permission
       │
       └──> GroupMembership ──> Group ──> GroupRole ──> Role ──> RolePermission ──> Permission
```

---

## Testing Guide

### Backend Testing

#### 1. Unit Tests

**Location**: `service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/`

**Test Files**:

- `service/unit/AuthorizationServiceTest.kt`: RBAC permission checking
- `service/unit/AbacEvaluationServiceTest.kt`: ABAC policy evaluation
- `service/unit/AuthorizationManagerTest.kt`: Authorization manager
- `service/unit/AuthorizationAuditServiceTest.kt`: Audit logging
- `graphql/AuthorizationResolverTest.kt`: GraphQL resolver
- `http/AuthorizationInterceptorIntegrationTest.kt`: HTTP interceptor

**Running Tests**:

```bash
# Run all authorization tests
./gradlew :service:kotlin:security:test --tests "*authorization*"

# Run specific test class
./gradlew :service:kotlin:security:test --tests "AuthorizationServiceTest"

# Run with coverage
./gradlew :service:kotlin:security:test --tests "*authorization*" jacocoTestReport
```

#### 2. Integration Tests

**Location**: `service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/integration/`

**Test Files**:

- `AuthorizationIntegrationTest.kt`: Full flow from service to database

**Running Integration Tests**:

```bash
# Requires PostgreSQL running
./gradlew :service:kotlin:security:test --tests "*integration*" --tests "*AuthorizationIntegrationTest"
```

#### 3. Test Scenarios

**RBAC Tests**:

- Direct role assignment
- Group inheritance
- Multiple roles
- Expired role bindings
- Users with no roles

**ABAC Tests**:

- Subject attribute matching
- Resource attribute matching
- Context attribute matching
- AND/OR/NOT operators
- Explicit deny policies
- No matching policies

**Hybrid Tests**:

- RBAC allows + ABAC allows
- RBAC allows + ABAC denies
- RBAC denies (short-circuit)
- Explicit deny override

### Frontend Testing

#### 1. Component Tests

**Location**: `web/src/shared/components/authorization/__tests__/`

**Test Files**:

- `PermissionGate.test.tsx`: PermissionGate component behavior

**Running Tests**:

```bash
cd web
npm test -- PermissionGate
```

#### 2. Provider Tests

**Location**: `web/src/shared/providers/__tests__/`

**Test Files**:

- `AuthorizationProvider.test.tsx`: Authorization context provider

**Running Tests**:

```bash
cd web
npm test -- AuthorizationProvider
```

#### 3. E2E Tests

**Location**: `web/tests/e2e/`

**Test Files**:

- `authorization.e2e.spec.ts`: End-to-end authorization scenarios

**Running E2E Tests**:

```bash
cd web
npm run test:e2e -- authorization
```

**E2E Test Scenarios**:

- Navigation visibility based on permissions
- User management authorization
- Group management authorization
- Role management authorization
- Permission-based UI rendering

### Manual Testing

#### 1. GraphQL Playground

**Testing Authorization Queries**:

```graphql
# Check permission
query {
  checkPermission(
    userId: "user-id-here"
    permission: "transaction:read"
    resourceId: "resource-id-here"
  ) {
    allowed
    reason
  }
}

# Get user permissions
query {
  getUserPermissions(userId: "user-id-here") {
    id
    name
  }
}

# Get user roles
query {
  getUserRoles(userId: "user-id-here") {
    id
    name
  }
}
```

#### 2. HTTP Endpoint Testing

**Using curl**:

```bash
# Test endpoint with authorization
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

#### 3. Frontend Testing

**Testing PermissionGate**:

1. Sign in as different users with different permissions
2. Navigate to pages with `PermissionGate` components
3. Verify content visibility matches permissions
4. Check browser console for authorization errors

**Testing Settings Page**:

1. Sign in as admin user
2. Navigate to `/settings`
3. Test Users, Groups, and Roles tabs
4. Verify "New" buttons are visible with correct permissions
5. Test editing with and without save permissions

### Performance Testing

#### 1. Load Testing

**Tools**: Apache JMeter, k6, or similar

**Scenarios**:

- Multiple concurrent permission checks
- Batch permission queries
- Authorization checks under load

**Metrics to Monitor**:

- Response time (target: <5ms per check)
- Throughput (requests per second)
- Cache hit rate
- Database query count

#### 2. Cache Testing

**Verify**:

- First permission check is slower (cache miss)
- Subsequent checks are faster (cache hit)
- Cache invalidation on role changes
- Cache invalidation on group membership changes

---

## Future Improvements

### High Priority

#### 1. Policy Management UI

**Current State**: ABAC policies are managed via database/API only

**Improvement**: Create a UI for managing ABAC policies

- Policy creation/editing interface
- Condition builder with visual editor
- Policy testing/simulation tool
- Policy versioning and rollback

**Benefits**:

- Easier policy management for non-technical users
- Reduced errors in policy creation
- Better visibility into active policies

#### 2. Policy Simulation/Testing

**Current State**: Policies must be created and tested in production

**Improvement**: Policy playground/simulation mode

- Test policies against sample scenarios
- Preview who would gain/lose access
- Validate policy conditions before activation
- Compare policy versions

**Benefits**:

- Prevent breaking changes
- Faster policy development
- Better understanding of policy impact

#### 3. Advanced ABAC Operators

**Current State**: Basic operators (eq, ne, in, gt, lt, etc.)

**Improvement**: Add more operators

- String operations: `contains`, `startsWith`, `endsWith`, `regex`
- Date operations: `before`, `after`, `between`
- Array operations: `containsAll`, `containsAny`, `size`
- Custom functions: User-defined evaluation functions

**Benefits**:

- More flexible policy conditions
- Support for complex business rules
- Better resource matching

#### 4. Performance Optimizations

**Current State**: Basic caching implemented

**Improvements**:

- Redis-based distributed caching
- Permission pre-computation and caching
- Batch permission checks API
- Query optimization for role/permission lookups
- Policy evaluation caching

**Benefits**:

- Better scalability
- Lower latency
- Reduced database load

### Medium Priority

#### 5. Delegation/Impersonation

**Current State**: Not implemented

**Improvement**: Support for user impersonation

- `user:impersonate` permission
- Impersonation session management
- Audit logging for impersonation actions
- Time-limited impersonation

**Benefits**:

- Support scenarios
- Debugging user issues
- Controlled access elevation

#### 6. Break-Glass Emergency Access

**Current State**: Not implemented

**Improvement**: Emergency admin role

- Special "break-glass" role with broad permissions
- Highly audited access
- Time-limited access
- Approval workflow

**Benefits**:

- Emergency incident response
- Controlled emergency access
- Compliance with audit requirements

#### 7. Resource-Level ACLs

**Current State**: Resource ownership via ABAC policies

**Improvement**: Per-resource access control lists

- Share individual resources with users/groups
- Granular permissions per resource (read, comment, update)
- Resource sharing UI
- Share expiration dates

**Benefits**:

- Fine-grained resource sharing
- Better collaboration
- More flexible access patterns

#### 8. Service Accounts / API Tokens

**Current State**: Users authenticate via tokens

**Improvement**: Dedicated service accounts

- Service account entity type
- API token management
- Scope-limited permissions
- Token rotation

**Benefits**:

- Better API security
- Separate service account management
- Token lifecycle management

### Low Priority / Future Considerations

#### 9. Policy Versioning

**Current State**: Policies can be enabled/disabled

**Improvement**: Full versioning system

- Policy version history
- Rollback to previous versions
- Policy diff visualization
- Scheduled policy changes

#### 10. Multi-Tenant Support

**Current State**: Single-tenant system

**Improvement**: Multi-tenant authorization

- Tenant isolation
- Tenant-specific roles and permissions
- Cross-tenant access policies
- Tenant hierarchy support

#### 11. Attribute Providers

**Current State**: Attributes are built manually

**Improvement**: Pluggable attribute providers

- External attribute sources (LDAP, Active Directory)
- Custom attribute providers
- Attribute caching and refresh
- Attribute transformation

#### 12. Authorization Analytics

**Current State**: Basic audit logging

**Improvement**: Analytics and reporting

- Permission usage analytics
- Access pattern analysis
- Policy effectiveness metrics
- Security risk indicators
- Dashboard for authorization insights

#### 13. Policy Templates

**Current State**: Policies created from scratch

**Improvement**: Policy template library

- Common policy templates
- Industry-specific templates
- Template customization
- Template sharing

#### 14. Just-In-Time (JIT) Access

**Current State**: Roles assigned manually

**Improvement**: Automatic role assignment

- Event-driven role assignment
- Time-based role activation
- Conditional role assignment
- Automatic role revocation

#### 15. Authorization Testing Framework

**Current State**: Manual test writing

**Improvement**: Testing utilities and framework

- Authorization test builders
- Policy test fixtures
- Scenario generators
- Test coverage analysis

### Technical Debt

#### 1. Code Organization

- Consolidate authorization-related code
- Extract common authorization patterns
- Improve code documentation
- Add more inline comments

#### 2. Error Handling

- Standardize error messages
- Improve error context
- Better error recovery
- User-friendly error messages

#### 3. Monitoring and Observability

- Add metrics for authorization checks
- Track policy evaluation performance
- Monitor cache hit rates
- Alert on authorization failures

#### 4. Documentation

- API documentation improvements
- Policy condition examples
- Integration guides
- Troubleshooting guides

---

## Related Documentation

- [Authorization Management Feature](./authorization-management.feature) - User, Group, and Role management
- [Authorization Feature](./authorization.feature) - RBAC and ABAC access checks
- [Access Management Feature](./access-management.feature) - Access control patterns
- [Architecture Decision Records](../../../09-adr/) - Technical decisions
- [Frontend Authorization Pattern](../../../04-patterns/frontend-patterns/) - Frontend implementation patterns

---

## Contributing

When contributing to the authorization feature:

1. **Follow the feature files**: Ensure implementation matches the `.feature` files
2. **Write tests**: Add unit, integration, and E2E tests
3. **Update documentation**: Keep this README and related docs up to date
4. **Consider performance**: Authorization checks are hot paths
5. **Maintain audit logging**: All authorization decisions must be logged
6. **Follow security best practices**: Input validation, error handling, etc.

---

## Support

For questions or issues:

1. Check the feature files for expected behavior
2. Review test files for usage examples
3. Consult the architecture decision records
4. Open an issue with detailed information

---

**Last Updated**: 2024
**Version**: 1.0
**Status**: Active Development

