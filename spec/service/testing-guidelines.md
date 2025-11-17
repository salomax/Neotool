---
title: Testing Guidelines
type: guide
category: backend
status: current
version: 1.0.0
tags: [testing, unit-tests, integration-tests, best-practices]
related:
  - service/kotlin/jpa-entity.md
  - service/graphql-federation-architecture.md
  - ARCHITECTURE_OVERVIEW.md
---

# Testing Guidelines

This document provides comprehensive guidelines for testing features in the NeoTool platform, covering unit tests, integration tests, and best practices. These guidelines apply to all modules and features.

## Table of Contents

- [Overview](#overview)
- [Test Structure](#test-structure)
- [Unit Testing](#unit-testing)
- [Integration Testing](#integration-testing)
- [Test Data Builders](#test-data-builders)
- [Best Practices](#best-practices)
- [Test Coverage Requirements](#test-coverage-requirements)

## Overview

Testing is a critical aspect of software development that ensures code quality, reliability, and maintainability. The NeoTool platform requires comprehensive testing at multiple levels:

1. **Unit Tests**: Test individual service methods in isolation
2. **Integration Tests**: Test service layer with database interactions
3. **End-to-End Tests**: Test complete flows through API endpoints (covered in frontend testing)

### Testing Pyramid

```
        /\
       /E2E\          ← Frontend E2E tests (Playwright)
      /------\
     /Integration\    ← Service layer integration tests
    /------------\
   /   Unit Tests  \  ← Service layer unit tests
  /----------------\
```

## Test Structure

### Directory Organization

```
service/kotlin/
└── [module]/
    └── src/
        └── test/
            └── kotlin/
                └── [project]/[module]/
                    ├── service/
                    │   ├── [Service]Test.kt              # Unit tests
                    │   └── [Service]IntegrationTest.kt   # Integration tests
                    └── test/
                        └── [Module]TestDataBuilders.kt   # Test data builders
```

### Naming Conventions

- **Unit Test Classes**: `[ServiceName]Test.kt`
- **Integration Test Classes**: `[ServiceName]IntegrationTest.kt`
- **Test Data Builders**: `[Module]TestDataBuilders.kt`
- **Test Methods**: Use descriptive names with backticks: `` `should [expected behavior] when [condition]` ``

## Unit Testing

### Service Layer Unit Tests

Unit tests for service classes should test all methods in isolation using mocks.

#### Test Structure

```kotlin
@DisplayName("[ServiceName] Unit Tests")
class [ServiceName]Test {

    private lateinit var repository: [Repository]Interface
    private lateinit var service: [ServiceName]

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = [ServiceName](repository)
    }

    @Nested
    @DisplayName("[Feature Group]")
    inner class [FeatureGroup]Tests {
        // Related tests here
    }
}
```

#### Required Test Cases

For each service method, test:

**Success Cases:**
- ✅ Should perform operation successfully with valid input
- ✅ Should handle edge cases (empty strings, null values where allowed)
- ✅ Should return expected result format

**Failure Cases:**
- ✅ Should handle invalid input gracefully
- ✅ Should return null/empty when entity not found
- ✅ Should throw appropriate exceptions for invalid operations
- ✅ Should handle repository errors

**Business Logic:**
- ✅ Should enforce business rules
- ✅ Should validate constraints
- ✅ Should handle state transitions correctly

### Example Unit Test

```kotlin
@Nested
@DisplayName("Entity Operations")
inner class EntityOperationsTests {

    @Test
    fun `should create entity with valid data`() {
        // Arrange
        val input = TestDataBuilders.entityInput(
            name = "Test Entity",
            status = "ACTIVE"
        )
        val savedEntity = TestDataBuilders.entity(
            id = UUID.randomUUID(),
            name = "Test Entity",
            status = EntityStatus.ACTIVE
        )

        whenever(repository.save(any())).thenReturn(savedEntity)

        // Act
        val result = service.create(input)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result.name).isEqualTo("Test Entity")
        assertThat(result.status).isEqualTo(EntityStatus.ACTIVE)
        verify(repository).save(any())
    }

    @Test
    fun `should return null when entity not found`() {
        // Arrange
        val id = UUID.randomUUID()
        whenever(repository.findById(id)).thenReturn(Optional.empty())

        // Act
        val result = service.findById(id)

        // Assert
        assertThat(result).isNull()
        verify(repository).findById(id)
    }

    @Test
    fun `should throw exception for invalid input`() {
        // Arrange
        val invalidInput = TestDataBuilders.entityInput(
            name = "", // Invalid: empty name
            status = "INVALID" // Invalid status
        )

        // Act & Assert
        assertThrows<IllegalArgumentException> {
            service.create(invalidInput)
        }
    }
}
```

## Integration Testing

### Service Layer Integration Tests

Integration tests verify services with real database interactions, testing the complete service layer integration with the database.

#### Test Structure

```kotlin
@MicronautTest(startApplication = true)
@DisplayName("[ServiceName] Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("[module]")
@TestMethodOrder(MethodOrderer.Random::class)
class [ServiceName]IntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    @Inject
    lateinit var repository: [Repository]Interface

    @Inject
    lateinit var service: [ServiceName]

    @BeforeEach
    fun setUp() {
        // Clean up test data before each test
    }

    @AfterEach
    fun tearDown() {
        // Clean up test data after each test
        try {
            repository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
```

#### Required Test Cases

**Database Persistence:**
- ✅ Should persist entity to database
- ✅ Should retrieve entity from database
- ✅ Should update entity in database
- ✅ Should delete entity from database

**Query Operations:**
- ✅ Should find entity by ID
- ✅ Should find entities by criteria
- ✅ Should return empty list when no entities match
- ✅ Should handle pagination correctly

**Business Logic with Database:**
- ✅ Should enforce unique constraints
- ✅ Should handle foreign key relationships
- ✅ Should maintain data integrity
- ✅ Should handle concurrent operations

**Error Handling:**
- ✅ Should handle database constraint violations
- ✅ Should handle optimistic locking conflicts
- ✅ Should rollback transactions on errors

### Example Integration Test

```kotlin
@Nested
@DisplayName("Entity Persistence")
inner class EntityPersistenceTests {

    @Test
    fun `should create and retrieve entity from database`() {
        // Arrange
        val input = TestDataBuilders.entityInput(
            name = "Integration Test Entity",
            status = "ACTIVE"
        )

        // Act
        val created = service.create(input)
        val retrieved = service.findById(created.id)

        // Assert
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("Integration Test Entity")
        assertThat(retrieved?.status).isEqualTo(EntityStatus.ACTIVE)
    }

    @Test
    fun `should update entity in database`() {
        // Arrange
        val entity = TestDataBuilders.entity(
            name = "Original Name",
            status = EntityStatus.ACTIVE
        )
        val saved = repository.save(entity)

        // Act
        val updated = service.update(saved.id, mapOf("name" to "Updated Name"))

        // Assert
        assertThat(updated).isNotNull()
        assertThat(updated.name).isEqualTo("Updated Name")
        
        val retrieved = repository.findById(saved.id)
        assertThat(retrieved.isPresent).isTrue()
        assertThat(retrieved.get().name).isEqualTo("Updated Name")
    }

    @Test
    fun `should handle unique constraint violations`() {
        // Arrange
        val entity1 = TestDataBuilders.entity(
            name = "Unique Entity",
            code = "UNIQUE-001"
        )
        repository.save(entity1)

        val entity2 = TestDataBuilders.entity(
            name = "Another Entity",
            code = "UNIQUE-001" // Same code - should violate constraint
        )

        // Act & Assert
        assertThrows<DataIntegrityViolationException> {
            repository.save(entity2)
        }
    }
}
```

## Test Data Builders

### Test Data Builder Pattern

Create test data builders for consistent test data across all tests in a module.

#### Location

`service/kotlin/[module]/src/test/kotlin/io/github/salomax/procureflow/[module]/test/[Module]TestDataBuilders.kt`

#### Structure

```kotlin
object [Module]TestDataBuilders {

    /**
     * Create a test entity with optional parameters
     */
    fun entity(
        id: UUID? = null,
        name: String = "Test Entity",
        status: EntityStatus = EntityStatus.ACTIVE,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ): Entity = Entity(
        id = id ?: UUID.randomUUID(),
        name = name,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /**
     * Create entity input for service methods
     */
    fun entityInput(
        name: String = "Test Entity",
        status: String = "ACTIVE"
    ): Map<String, Any> = mapOf(
        "name" to name,
        "status" to status
    )

    /**
     * Generate unique identifier for testing
     */
    fun uniqueCode(prefix: String = "TEST"): String {
        return "$prefix-${System.currentTimeMillis()}-${Thread.currentThread().id}"
    }

    /**
     * Generate unique name for testing
     */
    fun uniqueName(prefix: String = "Test"): String {
        return "$prefix ${System.currentTimeMillis()}"
    }
}
```

#### Usage Examples

```kotlin
// Create a basic entity
val entity = TestDataBuilders.entity(
    name = "My Entity",
    status = EntityStatus.ACTIVE
)

// Create entity with specific ID
val entity = TestDataBuilders.entity(
    id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
    name = "Specific Entity"
)

// Create entity input for service
val input = TestDataBuilders.entityInput(
    name = "New Entity",
    status = "ACTIVE"
)

// Generate unique values
val code = TestDataBuilders.uniqueCode("PROD")
val name = TestDataBuilders.uniqueName("Product")
```

## Best Practices

### 1. Test Isolation

- Each test should be independent and not rely on other tests
- Use `@BeforeEach` and `@AfterEach` to set up and clean up test data
- Use unique identifiers (timestamps, UUIDs) to avoid conflicts
- Clean up test data after each test

### 2. Test Naming

- Use descriptive test names that explain what is being tested
- Follow the pattern: `should [expected behavior] when [condition]`
- Use backticks for test names to allow spaces

```kotlin
@Test
fun `should create entity with valid data`() { }

@Test
fun `should return null when entity not found`() { }

@Test
fun `should throw exception for invalid input`() { }
```

### 3. Test Method Signatures

**CRITICAL**: JUnit `@Test` methods must return `Unit` (void). Never use `= runBlocking { }` as the function body.

**❌ Incorrect** - This returns the result of `runBlocking`, violating JUnit requirements:

```kotlin
@Test
fun `should process async operation`() = runBlocking {
    // test code
}
```

**✅ Correct** - Wrap `runBlocking` inside the function body:

```kotlin
@Test
fun `should process async operation`() {
    runBlocking {
        // test code
    }
}
```

**Why**: JUnit requires test methods to return `void` (or `Unit` in Kotlin). Using `= runBlocking { }` makes the function return the result of the `runBlocking` block, which violates this requirement and causes compilation errors.

### 4. Arrange-Act-Assert Pattern

Structure tests using the AAA pattern:

```kotlin
@Test
fun `should perform operation`() {
    // Arrange - Set up test data
    val input = TestDataBuilders.entityInput()
    val expected = TestDataBuilders.entity()

    // Act - Execute the code under test
    val result = service.create(input)

    // Assert - Verify the results
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo(expected.name)
}
```

### 5. Mock External Dependencies

- Mock repositories in unit tests
- Use real database in integration tests
- Verify mock interactions when necessary

```kotlin
// Unit test - use mocks
whenever(repository.findById(id)).thenReturn(Optional.of(entity))
verify(repository).findById(id)

// Integration test - use real repository
repository.save(entity)
val result = repository.findById(entity.id)
```

### 6. Test Error Cases

Always test both success and failure scenarios:

- ✅ Valid inputs
- ✅ Invalid inputs
- ✅ Missing inputs
- ✅ Edge cases (empty strings, null values)
- ✅ Exception handling

### 7. Use Nested Test Classes

Organize related tests using `@Nested` classes:

```kotlin
@Nested
@DisplayName("Entity Creation")
inner class EntityCreationTests {
    // Related tests here
}

@Nested
@DisplayName("Entity Updates")
inner class EntityUpdateTests {
    // Related tests here
}
```

### 8. Test Data Cleanup

- Clean up test data after each test to avoid side effects
- Use transactions when possible for automatic rollback
- Consider using test containers with isolated databases

```kotlin
@AfterEach
fun tearDown() {
    try {
        repository.deleteAll()
    } catch (e: Exception) {
        // Ignore cleanup errors
    }
}
```

### 9. Use Descriptive Assertions

- Use AssertJ for readable assertions
- Provide descriptive messages for assertions
- Use `describedAs()` for better error messages

```kotlin
assertThat(result)
    .describedAs("Entity should be created successfully")
    .isNotNull()

assertThat(result.name)
    .describedAs("Entity name should match input")
    .isEqualTo(input.name)
```

### 10. Test One Thing at a Time

- Each test should verify one specific behavior
- Avoid testing multiple behaviors in a single test
- Use multiple assertions for related checks

```kotlin
// ✅ Good: Tests one behavior
@Test
fun `should return entity with correct name`() {
    val result = service.findById(id)
    assertThat(result?.name).isEqualTo("Expected Name")
}

// ❌ Bad: Tests multiple behaviors
@Test
fun `should return entity with correct name and status`() {
    val result = service.findById(id)
    assertThat(result?.name).isEqualTo("Expected Name")
    assertThat(result?.status).isEqualTo(EntityStatus.ACTIVE)
    assertThat(result?.createdAt).isNotNull()
}
```

### 11. Use Test Tags

Tag tests appropriately for selective execution:

```kotlin
@Tag("integration")
@Tag("database")
@Tag("slow")
class EntityIntegrationTest { }
```

## Test Coverage Requirements

### Minimum Coverage Targets

- **Unit Tests**: 90%+ coverage for service classes
- **Integration Tests**: All public service methods with database
- **Critical Paths**: 100% coverage for business-critical operations

### Coverage Areas

**Service Classes:**
- ✅ All public methods
- ✅ All error paths
- ✅ Edge cases (null, empty, invalid inputs)
- ✅ Business logic branches

**Repository Integration:**
- ✅ All CRUD operations
- ✅ All query methods
- ✅ Database constraint handling
- ✅ Transaction boundaries

## Running Tests

### Unit Tests

```bash
# Run all unit tests in a module
cd service/kotlin
./gradlew :[module]:test

# Run specific test class
./gradlew :[module]:test --tests [ServiceName]Test

# Run with coverage
./gradlew :[module]:test --tests [ServiceName]Test jacocoTestReport
```

### Integration Tests

```bash
# Run all integration tests in a module
cd service/kotlin
./gradlew :[module]:testIntegration

# Run only integration tests for a specific service
./gradlew :[module]:testIntegration --tests "*[ServiceName]IntegrationTest*"

# Run with specific tags
./gradlew :[module]:testIntegration --tests "*Integration*"
```

### All Tests

```bash
# Run all tests (unit + integration) in a module
cd service/kotlin
./gradlew :[module]:test :[module]:testIntegration

# Run all tests across all modules
./gradlew test testIntegration
```

## Module-Specific Considerations

### Adding Test Integration Task

Each module should have a `testIntegration` task in its `build.gradle.kts`:

```kotlin
// Task to run integration tests
tasks.register<Test>("testIntegration") {
    group = "verification"
    description = "Runs integration tests using Testcontainers"
    
    useJUnitPlatform {
        includeEngines("junit-jupiter")
        includeTags("integration")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    
    // Disable Ryuk to avoid container startup issues
    systemProperty("ryuk.disabled", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    
    // Ensure Docker is available
    doFirst {
        try {
            val process = ProcessBuilder("docker", "version").start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException("Docker is required for integration tests but not available")
            }
        } catch (e: Exception) {
            throw GradleException("Docker is required for integration tests but not available: ${e.message}")
        }
    }
}
```

## Future Enhancements

### Planned Test Improvements

1. **Performance Tests**: Test services under load
2. **Contract Tests**: Test API contracts between services
3. **Concurrent Access Tests**: Test services with concurrent requests
4. **Resilience Tests**: Test error handling and recovery

### Test Infrastructure Improvements

1. **Test Containers**: Use Testcontainers for consistent test environments
2. **Test Fixtures**: Create reusable test fixtures for common scenarios
3. **Test Utilities**: Create helper functions for common test operations
4. **Test Reports**: Generate detailed test reports with coverage

## Related Documentation

- [JPA Entity Testing](./kotlin/jpa-entity.md#testing) - Database testing patterns
- [GraphQL Federation Architecture](./graphql-federation-architecture.md) - API testing patterns
- [Architecture Overview](../ARCHITECTURE_OVERVIEW.md) - Overall system architecture

---

*This testing guide ensures comprehensive coverage of all features and provides a foundation for testing future modules and services.*

