---
title: Testing Rules
type: rule
category: testing
status: current
version: 2.1.0
tags: [testing, rules, unit-tests, integration-tests, coverage]
ai_optimized: true
search_keywords: [testing, unit-tests, integration-tests, coverage, test-rules]
related:
  - 04-patterns/backend-patterns/testing-pattern.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Testing Rules

> **Purpose**: Explicit rules for testing requirements and patterns.

## Test Method Rules

### Rule: Test Method Return Type

**Rule**: JUnit `@Test` methods must return `Unit` (void). Never use `= runBlocking { }` as the function body.

**Rationale**: JUnit requires test methods to return void.

**Example**:
```kotlin
// ✅ Correct
@Test
fun `should process async operation`() {
    runBlocking {
        // test code
    }
}

// ❌ Incorrect
@Test
fun `should process async operation`() = runBlocking {
    // test code
}
```

**Exception**: None.

## Coverage Rules

### Rule: Unit Test Coverage

**Rule**: Unit tests must achieve 90%+ line coverage and 85%+ branch coverage.

**Rationale**: Ensures comprehensive testing of business logic.

**Exception**: Security services require 100% coverage (lines and branches).

### Rule: Integration Test Coverage

**Rule**: Integration tests must achieve 80%+ line coverage and 75%+ branch coverage.

**Rationale**: Ensures database interactions are tested.

### Rule: Branch Coverage

**Rule**: All conditional branches (if/when/switch/guard) must be tested.

**Rationale**: Ensures all code paths are validated.

**Example**:
```kotlin
// Service method
fun process(data: String?): Result {
    if (data == null) {
        return Result.error("Data is required")
    }
    return Result.success(processData(data))
}

// ✅ Correct: Both branches tested
@Test
fun `should return error when data is null`() { ... }

@Test
fun `should process data when data is not null`() { ... }
```

### Rule: Coverage Exclusions

**Rule**: Certain components may be excluded from coverage requirements when they are:
1. **Thin wrappers around well-tested libraries**: Components that primarily wrap third-party libraries with minimal custom logic
2. **Pure presentational components**: Components with no business logic, state, or interactions
3. **Configuration/boilerplate files**: Next.js boilerplate, middleware, and configuration files better tested via E2E

**Rationale**: Focus testing effort on business logic and custom behavior rather than library integration or trivial presentational code.

**Examples of Exclusions**:

**Frontend (TypeScript/React)**:
- Thin wrapper components around well-tested libraries:
  - Chart components wrapping Recharts
  - DataTable components wrapping ag-grid-react
  - Error boundaries (React error boundary wrapper)
  - Lazy loading wrappers
- Pure presentational components:
  - Skeleton loaders with no logic
  - Simple layout components with no state
- Next.js boilerplate:
  - `layout.tsx`, `page.tsx`, `not-found.tsx`
  - `middleware.ts`
  - Route handlers (`route.ts`)

**Backend (Kotlin/Micronaut)**:
- Configuration classes with no logic
- Pure data transfer objects (DTOs) with no validation logic
- Auto-generated code

### Rule: DTO Testing Exclusion

**Rule**: Data Transfer Objects (DTOs) implemented as Kotlin `data class` do not require unit tests.

**Rationale**: 
- DTOs are pure data structures with no business logic
- Kotlin data classes automatically generate `equals()`, `hashCode()`, `toString()`, and `copy()` methods
- Testing data class instantiation and property access provides no value
- DTOs are validated through integration tests when used in resolvers/services
- Focus testing effort on business logic, not data structures

**Example**:
```kotlin
// ✅ DTO - No tests needed
@Introspected
@Serdeable
data class UserDTO(
    val id: String,
    val email: String,
    val displayName: String? = null,
)

// ❌ Do NOT create tests like this:
@Test
fun `should create UserDTO with all fields`() {
    val dto = UserDTO(id = "123", email = "test@example.com")
    assertThat(dto.id).isEqualTo("123")
    assertThat(dto.email).isEqualTo("test@example.com")
}
```

**Exception**: DTOs with custom validation logic, computed properties, or business methods should be tested. However, prefer moving such logic to service/validator classes rather than DTOs.

**Implementation**: DTO classes are excluded from coverage requirements via the `*DTO` pattern in Kover configuration.

**Exception**: Components with significant custom business logic, state management, or complex interactions should NOT be excluded, even if they wrap a library.

**Example**:
```typescript
// ✅ Can exclude: Thin wrapper around Recharts
export const Chart: React.FC<ChartProps> = ({ type, data, ... }) => {
  return (
    <ResponsiveContainer>
      {type === 'line' && <LineChart data={data} />}
      {type === 'bar' && <BarChart data={data} />}
    </ResponsiveContainer>
  );
};

// ❌ Should NOT exclude: Custom logic and state
export const Chart: React.FC<ChartProps> = ({ type, data, ... }) => {
  const [processedData, setProcessedData] = useState([]);
  const [error, setError] = useState(null);
  
  useEffect(() => {
    // Custom data transformation logic
    const transformed = transformData(data);
    setProcessedData(transformed);
  }, [data]);
  
  // Custom error handling, data processing, etc.
  return <ResponsiveContainer>...</ResponsiveContainer>;
};
```

**Implementation**: Exclude files in test coverage configuration (e.g., `vitest.config.ts` coverage exclude list).

## Test Structure Rules

### Rule: Test Naming

**Rule**: Use descriptive test names with backticks: `` `should [expected behavior] when [condition]` ``

**Rationale**: Clear test intent.

**Example**:
```kotlin
// ✅ Correct
@Test
fun `should create entity with valid data`() { ... }

@Test
fun `should return null when entity not found`() { ... }

// ❌ Incorrect
@Test
fun testCreate() { ... }

@Test
fun test1() { ... }
```

### Rule: Arrange-Act-Assert Pattern

**Rule**: Structure tests using AAA pattern (Arrange, Act, Assert).

**Rationale**: Clear test structure.

**Example**:
```kotlin
// ✅ Correct
@Test
fun `should perform operation`() {
    // Arrange
    val input = TestDataBuilders.entityInput()
    
    // Act
    val result = service.create(input)
    
    // Assert
    assertThat(result).isNotNull()
}
```

### Rule: Test Isolation

**Rule**: Each test must be independent and not rely on other tests.

**Rationale**: Tests can run in any order.

**Example**:
```kotlin
// ✅ Correct
@BeforeEach
fun setUp() {
    repository.deleteAll()
}

@Test
fun `should create entity`() {
    val entity = service.create(input)
    assertThat(entity).isNotNull()
}

// ❌ Incorrect
@Test
fun `should create entity`() {
    // Assumes previous test created data
    val entities = service.findAll()
    assertThat(entities).hasSize(1)
}
```

## Test Data Rules

### Rule: Test Data Builders

**Rule**: Use test data builders for consistent test data.

**Rationale**: Reusable, maintainable test data.

**Example**:
```kotlin
// ✅ Correct
val entity = TestDataBuilders.entity(
    name = "Test Entity",
    status = EntityStatus.ACTIVE
)

// ❌ Incorrect
val entity = CustomerEntity(
    id = UUID.randomUUID(),
    name = "Test Entity",
    ...
)
```

### Rule: Unique Test Data

**Rule**: Use unique identifiers (timestamps, UUIDs) to avoid conflicts.

**Rationale**: Prevents test interference.

**Example**:
```kotlin
// ✅ Correct
val code = TestDataBuilders.uniqueCode("PROD")
val name = TestDataBuilders.uniqueName("Product")

// ❌ Incorrect
val code = "TEST-001" // May conflict with other tests
```

### Rule: Deterministic UUID Usage

**Rule**: Default to fixed UUIDs or builder-provided defaults for tests; use `UUID.randomUUID()` only when the value is not asserted and uniqueness alone matters.

**Rationale**:
- Keeps unit tests deterministic and repeatable
- Simplifies debugging by avoiding run-to-run data drift
- Aligns with builder pattern for maintainable test data

**Guidelines**:
- Use fixed UUIDs when asserting values or modeling relationships:
  ```kotlin
  val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
  val role = SecurityTestDataBuilders.role(id = id, name = "admin")
  ```
- Use builder defaults (no explicit ID) when the value is irrelevant:
  ```kotlin
  val role = SecurityTestDataBuilders.role(name = "admin") // builder assigns UUID
  ```
- Use `UUID.randomUUID()` only for "non-existent" IDs or uniqueness checks where the value is never asserted:
  ```kotlin
  val missingId = UUID.randomUUID()
  assertThat(service.findById(missingId)).isNull()
  ```

### Rule: Transaction Handling in Integration Tests

**Rule**: When setting up test data that needs to be visible to services running in separate transactions, use `EntityManager.runTransaction` instead of `entityManager.flush()`.

**Rationale**: 
- `flush()` only sends SQL to the database but does not commit the transaction
- Data must be committed to be visible in subsequent transactions
- Services typically run in their own transaction context and won't see uncommitted data
- `runTransaction` commits the transaction, making data visible across transaction boundaries

**When to Use**:
- Setting up test data before calling services that run in separate transactions
- Preparing data for GraphQL/HTTP requests that execute in different transaction contexts
- Ensuring test data is committed before service layer operations

**Example**:
```kotlin
// ✅ Correct: Data committed and visible to service
@Test
fun `should authorize user with role`() {
    entityManager.runTransaction {
        val user = createTestUser()
        val role = roleRepository.save(createRole())
        val assignment = roleAssignmentRepository.save(
            createRoleAssignment(userId = user.id, roleId = role.id)
        )
        entityManager.flush()
    }
    
    // Service runs in separate transaction and can see committed data
    val result = authorizationService.checkPermission(userId, "permission:read")
    assertThat(result.allowed).isTrue()
}

// ❌ Incorrect: Data not committed, service won't see it
@Test
fun `should authorize user with role`() {
    val user = createTestUser()
    val role = roleRepository.save(createRole())
    val assignment = roleAssignmentRepository.save(
        createRoleAssignment(userId = user.id, roleId = role.id)
    )
    entityManager.flush() // Only flushes, doesn't commit
    
    // Service runs in separate transaction and CANNOT see uncommitted data
    val result = authorizationService.checkPermission(userId, "permission:read")
    // This will fail because the service can't see the role assignment
    assertThat(result.allowed).isTrue()
}
```

**Implementation**:
- Import: `import io.github.salomax.neotool.common.test.transaction.runTransaction`
- Wrap data setup in `entityManager.runTransaction { ... }`
- The block commits automatically after execution
- Data is then visible to subsequent service calls

**Exception**: When test and service run in the same transaction context (rare in integration tests), `flush()` may be sufficient, but `runTransaction` is still recommended for consistency.

## Test Organization Rules

### Rule: Nested Test Classes

**Rule**: Use `@Nested` classes to organize related tests.

**Rationale**: Better test organization.

**Example**:
```kotlin
// ✅ Correct
@Nested
@DisplayName("Entity Creation")
inner class EntityCreationTests {
    @Test
    fun `should create entity`() { ... }
}

// ❌ Incorrect
// All tests at top level
```

### Rule: Test Tags

**Rule**: Tag tests appropriately (`@Tag("integration")`, `@Tag("unit")`).

**Rationale**: Enables selective test execution.

**Example**:
```kotlin
// ✅ Correct
@Tag("integration")
@Tag("database")
class EntityIntegrationTest { ... }
```

### Rule: Repository Testing Strategy

**Rule**: Repositories and custom repository implementations must be tested **only** through integration tests. Do not write unit tests for repositories.

**Rationale**: 
- Repositories interact directly with the database and JPA/Hibernate
- Unit tests with mocks don't accurately test database queries, transactions, and persistence behavior
- Integration tests provide real database interactions and validate actual SQL generation
- Repository logic (Criteria API, JPQL, native queries) is best validated against a real database
- Reduces maintenance burden of complex mock setups for JPA components

**What This Applies To**:
- Repository interfaces (`*Repository`)
- Custom repository implementations (`*RepositoryImpl`, `*RepositoryCustom`)
- Repository helper classes used by repositories (e.g., `SortingHelpers`, `SortingConfigs`)

**Example**:
```kotlin
// ✅ Correct: Integration test for repository
@Tag("integration")
class UserRepositoryIntegrationTest {
    @Test
    fun `should search users by name or email`() {
        // Test with real database
        val result = userRepository.searchByNameOrEmail("test", 10, null, orderBy)
        assertThat(result).hasSize(5)
    }
}

// ❌ Incorrect: Unit test with mocks
class UserRepositoryImplTest {
    @Test
    fun `should search users by name or email`() {
        // Mocking EntityManager, CriteriaBuilder, etc.
        whenever(criteriaBuilder.coalesce(...)).thenReturn(...)
        // This doesn't test actual database behavior
    }
}
```

**Exception**: None. All repository testing must be done via integration tests.

## Error Testing Rules

### Rule: Test Error Cases

**Rule**: Always test both success and failure scenarios.

**Rationale**: Ensures error handling works.

**Example**:
```kotlin
// ✅ Correct
@Test
fun `should create entity with valid data`() { ... }

@Test
fun `should throw exception for invalid input`() { ... }

// ❌ Incorrect
// Only testing success cases
```

### Rule: Behavior-Focused Assertions

**Rule**: Assert on behavior and outcomes, not implementation details.

**Rationale**: Tests remain valid when implementation changes.

**Example**:
```kotlin
// ✅ Correct
assertThat(result.status).isEqualTo(HttpStatus.CONFLICT)

// ❌ Incorrect
assertThat(result.exceptionType).isEqualTo("StaleObjectStateException")
```

## Python/Pytest Configuration Rules

### Rule: Warning Suppression Location

**Rule**: All warning suppression for pytest tests MUST be configured in `tests/conftest.py` using `pytest_configure()` hook. Do NOT add warning filters to `pytest.ini`.

**Rationale**: 
- Centralizes all test configuration in one place (`conftest.py`)
- Keeps warning suppression logic together with other test setup
- Easier to maintain and understand
- Allows for programmatic filtering logic when needed
- `pytest.ini` should only contain basic pytest configuration (paths, markers, etc.)

**Example**:
```python
# ✅ Correct: Warning suppression in conftest.py
# tests/conftest.py
import warnings
import logging

def pytest_configure():
    """Configure pytest to suppress known warnings from third-party libraries."""
    # Suppress testcontainers deprecation warnings
    warnings.filterwarnings(
        "ignore",
        message="The @wait_container_is_ready decorator is deprecated.*",
        category=DeprecationWarning,
    )
    # Suppress coolname library deprecation warnings
    warnings.filterwarnings(
        "ignore",
        message="codecs.open\\(\\) is deprecated.*",
        category=DeprecationWarning,
    )
    # Suppress Prefect logging errors during teardown
    prefect_logger = logging.getLogger("prefect.logging.handlers")
    prefect_logger.setLevel(logging.CRITICAL)
    prefect_logger.propagate = False
```

```ini
# ✅ Correct: pytest.ini only contains basic configuration
# pytest.ini
[pytest]
pythonpath = .
testpaths = tests
markers =
    integration: marks tests as integration tests (requires Docker)
```

```ini
# ❌ Incorrect: Warning suppression in pytest.ini
# pytest.ini
[pytest]
pythonpath = .
testpaths = tests
filterwarnings =  # ❌ Do NOT add warning filters here
    ignore::DeprecationWarning:coolname.loader
```

**What to Suppress**:
- **Third-party library deprecation warnings**: Warnings from dependencies (e.g., `coolname`, `testcontainers`) that we cannot fix
- **Known library issues**: Logging errors from libraries during teardown (e.g., Prefect logging handlers)
- **Framework warnings**: Deprecation warnings from testing frameworks when upgrading

**What NOT to Suppress**:
- **Our own code warnings**: Never suppress warnings from our own codebase
- **Critical warnings**: Never suppress warnings that indicate actual problems
- **Type warnings**: Never suppress type checking warnings (use proper type hints instead)

**Implementation**:
1. Add warning filters in `tests/conftest.py` using `warnings.filterwarnings()` in `pytest_configure()`
2. Use regex patterns for message matching when needed
3. Document why each warning is suppressed with comments
4. Group related suppressions together
5. Keep `pytest.ini` minimal (only paths, markers, basic config)

**Exception**: None. All warning suppression must be in `conftest.py`.

## Related Documentation

- [Testing Pattern](../../04-patterns/backend-patterns/testing-pattern.md)
- [Kotlin Standards](../coding-standards/kotlin-standards.md)

