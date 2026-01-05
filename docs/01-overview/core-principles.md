---
title: Core Principles
type: overview
category: philosophy
status: current
version: 3.0.0
tags: [principles, philosophy, guidelines, design, sdd]
ai_optimized: true
search_keywords: [principles, philosophy, guidelines, design, architecture, values]
related:
  - 01-overview/README.md
  - 01-overview/specification-driven-development.md
  - 01-overview/architecture-at-a-glance.md
  - 05-backend/standards/layer-rules.md
last_updated: 2026-01-02
---

# Core Principles

> **Purpose**: The design philosophy and guiding principles that drive all decisions in NeoTool.

## Philosophy

NeoTool is built on the belief that **great software emerges from great specifications**. We don't just document what we build, we specify what we intend to build, then implement it consistently using proven patterns.

This philosophy manifests in 10 core principles that guide every architectural decision, code pattern, and development practice.

---

## Principle 1: Specification-Driven Development (SDD)

### Statement
**Specification drives implementation. Code follows patterns. Quality is built-in.**

### What It Means
- ✅ Write specifications **before** code
- ✅ Update specifications **with** code changes
- ✅ Review specifications **in** pull requests
- ✅ Validate code **against** specifications

### Why It Matters
- **Consistency**: Same patterns applied everywhere
- **Clarity**: No ambiguity about "how to do X"
- **Onboarding**: New developers have complete guide
- **AI-Assisted**: LLMs can generate compliant code

### How to Apply
1. **Before coding**: Read relevant patterns and standards
2. **During coding**: Reference templates and examples
3. **After coding**: Validate against checklists
4. **In reviews**: Check pattern compliance

### Examples
- Feature starts with spec in `docs/03-features/`
- Implementation follows patterns in `docs/05-backend/patterns/`
- Code validated with `docs/94-validation/feature-checklist.md`

**See**: [Specification-Driven Development Guide](./specification-driven-development.md)

---

## Principle 2: Type Safety End-to-End

### Statement
**Types are enforced from database to UI. Compile-time errors over runtime failures.**

### What It Means
- ✅ GraphQL schema as single source of truth
- ✅ Generated TypeScript types for frontend
- ✅ Kotlin types for backend
- ✅ JPA entities for database

### Why It Matters
- **Catch errors early**: At compile time, not production
- **Refactoring safety**: Type checker finds all usages
- **IDE support**: Auto-complete, inline docs
- **Self-documenting**: Types express intent

### How to Apply
1. **Define GraphQL schema** (source of truth)
2. **Generate TypeScript types** (`npm run codegen`)
3. **Implement Kotlin resolvers** (strong types)
4. **Map to JPA entities** (database types)

### Examples
```graphql
# GraphQL schema (source of truth)
type User {
  id: ID!
  email: String!
  roles: [Role!]!
}
```
↓
```typescript
// Auto-generated TypeScript
interface User {
  id: string;
  email: string;
  roles: Role[];
}
```
↓
```kotlin
// Kotlin entity
@Entity
data class User(
  @Id val id: UUID,
  val email: String,
  @ElementCollection val roles: List<Role>
)
```

**See**: [GraphQL Standards](../06-contracts/graphql-standards.md)

---

## Principle 3: Modularity & Clear Boundaries

### Statement
**Services, modules, and layers have well-defined boundaries and minimal coupling.**

### What It Means
- ✅ Monorepo organized by domain
- ✅ Clear module interfaces
- ✅ Independent service deployment
- ✅ Explicit dependencies (no circular)

### Why It Matters
- **Scalability**: Independent teams can own modules
- **Testability**: Mock dependencies easily
- **Maintainability**: Changes isolated to modules
- **Flexibility**: Swap implementations

### How to Apply
1. **Organize by domain**: Not by technology
2. **Define interfaces**: Between modules
3. **Minimize coupling**: Depend on abstractions
4. **Avoid circular dependencies**: Enforce with linting

### Examples
**Backend Modules**:
```
app → common (✅ allowed)
security → common (✅ allowed)
common → app (❌ circular dependency!)
```

**Frontend**:
```
app → shared → lib (✅ clear hierarchy)
lib → app (❌ wrong direction!)
```

**See**: [Architecture Standards](../05-backend/standards/layer-rules.md)

---

## Principle 4: Clean Architecture (Layered)

### Statement
**Dependencies point inward. Business logic is independent of frameworks.**

### What It Means
- ✅ Layers: API → Service → Repository → Entity
- ✅ Inner layers don't depend on outer layers
- ✅ Business logic in service layer (framework-agnostic)
- ✅ Infrastructure details in outer layers

### Why It Matters
- **Testability**: Business logic tested without infrastructure
- **Framework independence**: Swap frameworks without rewriting logic
- **Clarity**: Each layer has single responsibility

### How to Apply
```
GraphQL Resolver (API layer)
    ↓ calls
Service (business logic - no framework coupling!)
    ↓ calls
Repository (data access abstraction)
    ↓ uses
Entity (database mapping)
```

### Examples
**Good** (clean architecture):
```kotlin
// Service: No Micronaut coupling
class UserService(private val userRepository: UserRepository) {
  fun createUser(email: String): User {
    // Business logic here
  }
}

// Resolver: Micronaut-aware
@GraphQLResolver
class UserResolver(private val userService: UserService) {
  @GraphQLQuery
  fun user(id: UUID): User = userService.findById(id)
}
```

**Bad** (framework coupled):
```kotlin
// ❌ Service tightly coupled to Micronaut
@Singleton
class UserService(private val httpRequest: HttpRequest) {
  fun createUser(): User {
    val email = httpRequest.parameters.get("email") // ❌ HTTP in business logic!
  }
}
```

**See**: [Service Pattern](../05-backend/patterns/service-pattern.md)

---

## Principle 5: Developer Experience (DX) First

### Statement
**Fast feedback loops, clear patterns, excellent tooling.**

### What It Means
- ✅ Hot reload for instant feedback
- ✅ Clear error messages
- ✅ Comprehensive documentation
- ✅ Code generation reduces boilerplate

### Why It Matters
- **Productivity**: Developers spend more time building features
- **Satisfaction**: Enjoyable development experience
- **Quality**: Good DX = easier to do the right thing
- **Onboarding**: New developers productive quickly

### How to Apply
1. **Use hot reload** (Next.js dev server, Gradle continuous build)
2. **Generate code** (GraphQL types, repository methods)
3. **Provide templates** (code templates, feature templates)
4. **Write clear docs** (examples, not just API references)

### Examples
- GraphQL Code Generator: Auto-generate TypeScript hooks
- Micronaut Data: Auto-implement repository methods
- Templates: Start features from boilerplate
- CLI: Common tasks automated

**See**: [Feature Development Workflow](../08-workflows/feature-development.md)

---

## Principle 6: Cloud-Native & Observable

### Statement
**Containerized, scalable, and observable from day one.**

### What It Means
- ✅ Docker containers for all services
- ✅ Kubernetes-ready deployments
- ✅ Metrics, logging, tracing built-in
- ✅ Stateless services (horizontal scaling)

### Why It Matters
- **Production-ready**: Not an afterthought
- **Debuggability**: Understand system behavior
- **Scalability**: Handle traffic spikes
- **Portability**: Run anywhere

### How to Apply
1. **Containerize everything**: Dockerfile for each service
2. **Instrument code**: Metrics + structured logging
3. **Use Kubernetes**: Declarative deployment configs
4. **Monitor proactively**: Dashboards + alerts

### Examples
```kotlin
// Metrics built into services
@Timed("user.creation")
fun createUser(email: String): User {
  logger.info("Creating user", mapOf("email" to email))
  // ...
}
```

**See**: [Observability Overview](../10-observability/observability-overview.md)

---

## Principle 7: Vendor Neutrality

### Statement
**Portable across clouds. No lock-in to proprietary services.**

### What It Means
- ✅ Standard technologies (Kubernetes, PostgreSQL)
- ✅ Open-source tools (Prometheus, Grafana)
- ✅ Avoid cloud-specific APIs (use abstractions)
- ✅ Self-hostable

### Why It Matters
- **Flexibility**: Deploy to any cloud or on-prem
- **Cost control**: Avoid vendor lock-in pricing
- **Reliability**: Not dependent on single vendor
- **Compliance**: Data sovereignty requirements

### How to Apply
1. **Use Kubernetes** (not AWS ECS, GKE-specific features)
2. **Use PostgreSQL** (not RDS-specific features)
3. **Use standard Docker** (not proprietary container formats)
4. **Abstract cloud services** (S3 API compatible, not AWS S3 only)

### Examples
**Good**:
- Kubernetes manifests (works on GKE, EKS, AKS, on-prem)
- PostgreSQL (works on RDS, Cloud SQL, self-hosted)

**Bad**:
- AWS Lambda-specific code
- GCP-only APIs
- Azure-specific services

**See**: [ADR-0002: Containerized Architecture](../92-adr/0002-containerized-architecture.md)

---

## Principle 8: Domain-Driven Design (DDD)

### Statement
**Code reflects business domain. Entities and services model real-world concepts.**

### What It Means
- ✅ Rich domain models (not anemic entities)
- ✅ Ubiquitous language (terms match business)
- ✅ Bounded contexts (clear domain boundaries)
- ✅ Repository pattern (data access abstraction)

### Why It Matters
- **Business alignment**: Code matches how stakeholders think
- **Maintainability**: Domain logic in one place
- **Collaboration**: Developers and business speak same language
- **Scalability**: Clear boundaries enable independent teams

### How to Apply
1. **Model the domain**: Identify entities, value objects, aggregates
2. **Use ubiquitous language**: Same terms in code and docs
3. **Define bounded contexts**: Service boundaries match domain boundaries
4. **Encapsulate logic**: Business rules in domain models

### Examples
```kotlin
// Rich domain model (not anemic!)
@Entity
class User(
  @Id val id: UUID,
  var email: String,
  private var passwordHash: String
) {
  // Business logic in domain model
  fun changePassword(oldPassword: String, newPassword: String) {
    require(verifyPassword(oldPassword)) { "Invalid old password" }
    require(newPassword.length >= 12) { "Password too short" }
    this.passwordHash = hashPassword(newPassword)
  }

  private fun verifyPassword(password: String): Boolean = /* ... */
  private fun hashPassword(password: String): String = /* ... */
}
```

**See**: [Domain Model](../04-domain/domain-model.md)

---

## Principle 9: Component-Driven Development

### Statement
**Build UIs from reusable, composable components.**

### What It Means
- ✅ Shared component library
- ✅ Design system for consistency
- ✅ Composition over inheritance

### Why It Matters
- **Reusability**: Build once, use everywhere
- **Consistency**: Same components = same UX
- **Maintainability**: Fix once, fixed everywhere
- **Scalability**: Teams can contribute components

### How to Apply
1. **Define design system**: Colors, typography, spacing
2. **Build atomic components**: Button, Input, etc.
3. **Compose molecules**: Form Field = Label + Input + Error
4. **Create organisms**: Login Form = multiple Form Fields + Button

### Examples
```tsx
// Atomic design in practice
<LoginForm>  {/* Organism */}
  <FormField   {/* Molecule */}
    label="Email"
    input={<Input type="email" />}  {/* Atom */}
    error={error}
  />
  <Button type="submit">Login</Button>  {/* Atom */}
</LoginForm>
```

**See**: [Frontend Patterns](../07-frontend/patterns/)

---

## Principle 10: Testing as First-Class Citizen

### Statement
**Tests are not afterthoughts. They're documentation and safety nets.**

### What It Means
- ✅ Unit tests for business logic
- ✅ Integration tests for infrastructure
- ✅ E2E tests for critical flows
- ✅ Code coverage tracked

### Why It Matters
- **Confidence**: Safe to refactor
- **Documentation**: Tests show how code is used
- **Regression prevention**: Catch bugs before production
- **Design feedback**: Hard to test = bad design

### How to Apply
1. **Write tests with code**: Not after
2. **Test behavior**: Not implementation details
3. **Use test containers**: Real dependencies in tests
4. **Maintain coverage**: 80%+ for critical code

### Examples
```kotlin
// Unit test (isolated)
@Test
fun `should create user with valid email`() {
  val userService = UserService(mockRepository)
  val user = userService.createUser("test@example.com")
  assertEquals("test@example.com", user.email)
}

// Integration test (real DB via Testcontainers)
@MicronautTest
@Testcontainers
class UserRepositoryTest {
  @Inject lateinit var userRepository: UserRepository

  @Test
  fun `should persist user to database`() {
    val user = User(email = "test@example.com")
    userRepository.save(user)
    assertNotNull(userRepository.findById(user.id))
  }
}
```

**See**: [Testing Standards](../05-backend/kotlin/testing-standards.md)

---

## Applying Principles

### When Making Decisions

Use this decision framework:

1. **Check principles**: Does this decision align with our principles?
2. **Identify trade-offs**: What are we gaining/losing?
3. **Document decision**: Create ADR if significant
4. **Update patterns**: If new approach, document it

**Example**:
```
Decision: Should we use REST or GraphQL?

Check principles:
- Principle 2 (Type Safety): GraphQL ✅ (typed schema)
- Principle 5 (DX): GraphQL ✅ (better tooling)
- Principle 3 (Modularity): GraphQL ✅ (federation)

Trade-offs:
+ Type safety
+ Better DX
- Learning curve
- More complex

Decision: GraphQL (aligns with 3+ principles)
Document: ADR-0003 created
```

### When Reviewing Code

Check these questions:

1. **SDD**: Does code follow documented patterns?
2. **Type Safety**: Are types used correctly?
3. **Modularity**: Are boundaries respected?
4. **Clean Architecture**: Do dependencies point inward?
5. **DX**: Is code easy to understand and modify?
6. **Observable**: Are metrics/logs added?
7. **Testable**: Are tests included?

### When Breaking Rules

**Sometimes you need to break principles.** That's okay if:

1. **You have a good reason** (performance, external constraint, etc.)
2. **You document the exception** (code comment + ADR if significant)
3. **You limit the scope** (isolated to one module)
4. **You plan to revisit** (add TODO to align with principles later)

**Example**:
```kotlin
/**
 * NOTE: This violates Principle 4 (Clean Architecture) by putting
 * HTTP logic in the service layer. This is a temporary workaround
 * for legacy API integration. See TODO-123 for migration plan.
 */
@Singleton
class LegacyService(private val httpClient: HttpClient) {
  // Exception to the rule, documented
}
```

---

## Principles in Practice

### Scenario 1: Adding a New Feature

**Steps**:
1. **SDD**: Create feature spec in `docs/03-features/`
2. **Type Safety**: Define GraphQL schema
3. **Modularity**: Identify which module owns the feature
4. **Clean Architecture**: Implement in layers (Resolver → Service → Repository)
5. **DDD**: Model domain entities
6. **Testing**: Write unit + integration tests
7. **Observable**: Add metrics and logs

### Scenario 2: Refactoring Code

**Steps**:
1. **SDD**: Update patterns if refactoring changes approach
2. **Type Safety**: Leverage types to find all usages
3. **Testing**: Ensure tests pass after refactoring
4. **Clean Architecture**: Improve layer separation
5. **Document**: Update ADRs if architecture changes

### Scenario 3: Onboarding New Developer

**Steps**:
1. **SDD**: Read specification (this documentation)
2. **Architecture**: Understand system design
3. **Principles**: Learn these 10 principles
4. **Examples**: Study code examples
5. **First Feature**: Implement following patterns

---

## Measuring Principle Adherence

### Metrics

| Principle | Metric | Target |
|-----------|--------|--------|
| **SDD** | % PRs with doc updates | 80%+ |
| **Type Safety** | TypeScript strict mode | 100% |
| **Modularity** | Circular dependency count | 0 |
| **Clean Architecture** | Layer violation count | 0 |
| **DX** | Onboarding time | < 1 week |
| **Cloud-Native** | Container coverage | 100% |
| **Vendor Neutral** | Cloud-specific API usage | 0% |
| **DDD** | Domain terms in code | 90%+ |
| **Component-Driven** | Component reuse rate | 60%+ |
| **Testing** | Code coverage | 80%+ |

### Qualitative Indicators

✅ **Team says**: "The docs answered my question"
✅ **Code reviews**: Reference principles and patterns
✅ **AI assistants**: Generate compliant code first try
✅ **Onboarding**: New developers productive in days
✅ **Production**: Few incidents, fast debugging

---

## Related Documentation

### Core Docs
- [Overview](./README.md) - Project introduction
- [SDD Guide](./specification-driven-development.md) - SDD methodology
- [Architecture](./architecture-at-a-glance.md) - System design

### Patterns & Standards
- [Backend Patterns](../05-backend/patterns/)
- [Frontend Patterns](../07-frontend/patterns/)
- [Coding Standards](../05-backend/kotlin/coding-standards.md)
- [Architecture Standards](../05-backend/standards/layer-rules.md)

### Decision Records
- [All ADRs](../92-adr/) - Technology decisions and rationale

---

## FAQ

### Q: What if principles conflict?

**A**: Prioritize in this order:
1. **Type Safety** (prevents runtime errors)
2. **SDD** (ensures consistency)
3. **Modularity** (enables scalability)
4. **Others** (context-dependent)

### Q: Can we adapt these principles?

**A**: Yes! Principles should evolve:
1. Propose change via ADR
2. Discuss with team
3. Update this document
4. Communicate changes

### Q: How do we enforce principles?

**A**:
- Code reviews
- Automated linting
- Pattern compliance checks
- Documentation reviews

---

**Version**: 3.0.0 (2026-01-02)
**Principles**: 10 core principles guiding all decisions
**Philosophy**: Specification-driven, type-safe, modular, observable

*Build software guided by principles, not whims.*
