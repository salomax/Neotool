---
title: Testing Rules
type: rule
category: testing
status: current
version: 2.0.0
tags: [testing, rules, unit-tests, integration-tests, coverage]
ai_optimized: true
search_keywords: [testing, unit-tests, integration-tests, coverage, test-rules]
related:
  - 03-patterns/backend/testing-pattern.md
  - 01-rules/coding-standards.md
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

## Related Documentation

- [Testing Pattern](../03-patterns/backend/testing-pattern.md)
- [Coding Standards](./coding-standards.md)

