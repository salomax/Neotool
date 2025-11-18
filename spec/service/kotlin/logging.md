---
id: service-kotlin-logging
title: Service — Logging Best Practices (Kotlin + Micronaut)
area: service
version: 1.0
tags: [kotlin, micronaut, logging, slf4j, logback, best-practices, debugging]
last_reviewed: 2025-01-15
---

# Logging Best Practices in Kotlin Service

This guide covers logging best practices and conventions for Kotlin services using Micronaut, SLF4J, and Logback.

## Overview

Proper logging is essential for debugging, monitoring, and observability in production systems. This document establishes the logging standards and best practices for all Kotlin services in the NeoTool project.

## Critical Rule: No `println` Statements

**⚠️ FORBIDDEN**: **NEVER use `println()` statements in Kotlin source code.**

### Why `println` is Bad Practice

1. **No Log Level Control**: `println` always outputs, regardless of log level configuration
2. **No Structured Logging**: Cannot be filtered, searched, or aggregated by log management systems
3. **No Context**: Missing timestamps, thread information, correlation IDs, and other metadata
4. **Performance Impact**: Always executes, even when logging is disabled
5. **Production Issues**: Can flood logs in production, making debugging difficult
6. **Not Observable**: Cannot be integrated with observability tools (Loki, Prometheus, etc.)

### Exception: Build Scripts

`println` statements in `build.gradle.kts` files are acceptable for build-time output (e.g., test coverage reports, build status messages).

## Logging Framework

### Primary Framework: Kotlin Logging (mu.KotlinLogging)

**Preferred approach** for all new code:

```kotlin
import mu.KotlinLogging

@Singleton
class CustomerService {
    private val logger = KotlinLogging.logger {}
    
    fun getCustomer(id: UUID): Customer? {
        logger.debug { "Fetching customer with ID: $id" }
        // ... implementation
    }
}
```

### Alternative: SLF4J LoggerFactory

For cases where Kotlin Logging is not available or for consistency with existing code:

```kotlin
import org.slf4j.LoggerFactory

@Singleton
class CustomerService {
    private val logger = LoggerFactory.getLogger(CustomerService::class.java)
    
    fun getCustomer(id: UUID): Customer? {
        logger.debug("Fetching customer with ID: {}", id)
        // ... implementation
    }
}
```

## Log Levels

Use appropriate log levels based on the severity and purpose of the message:

### DEBUG
- **Purpose**: Detailed diagnostic information for debugging
- **When to use**: 
  - Method entry/exit points
  - Variable values during execution
  - Detailed flow information
- **Example**:
```kotlin
logger.debug { "CustomerService.get - entity version: ${entity?.version}, customer version: ${customer.version}" }
logger.debug { "Processing request with parameters: $params" }
```

### INFO
- **Purpose**: General informational messages about application flow
- **When to use**:
  - Successful operations (create, update, delete)
  - Important state changes
  - Business events
- **Example**:
```kotlin
logger.info { "Customer created successfully: ${result.name} (ID: ${result.id})" }
logger.info { "Application started on port ${serverPort}" }
```

### WARN
- **Purpose**: Warning messages for potentially problematic situations
- **When to use**:
  - Recoverable errors
  - Deprecated feature usage
  - Configuration issues
  - Missing optional data
- **Example**:
```kotlin
logger.warn { "Attempted to delete non-existent customer with ID: $id" }
logger.warn { "Using default configuration value: $defaultValue" }
```

### ERROR
- **Purpose**: Error messages for serious problems
- **When to use**:
  - Exceptions that are caught and handled
  - Failed operations that cannot be recovered
  - System errors
- **Example**:
```kotlin
logger.error(exception) { "Failed to process customer update: ${customer.id}" }
logger.error { "Database connection failed: ${exception.message}" }
```

## Best Practices

### 1. Use Lazy Evaluation with Kotlin Logging

Kotlin Logging uses lambda expressions for lazy evaluation, which means the string interpolation only happens if the log level is enabled:

```kotlin
// ✅ GOOD: Lazy evaluation - string only created if DEBUG is enabled
logger.debug { "Customer: $customer, version: ${customer.version}" }

// ❌ BAD: String always created, even if DEBUG is disabled
logger.debug("Customer: $customer, version: ${customer.version}")
```

### 2. Include Context Information

Always include relevant context in log messages:

```kotlin
// ✅ GOOD: Includes relevant context
logger.info { "Customer updated successfully: ${result.name} (ID: ${result.id}, Email: ${result.email})" }

// ❌ BAD: Missing context
logger.info { "Customer updated" }
```

### 3. Log Exceptions Properly

Always include the exception when logging errors:

```kotlin
// ✅ GOOD: Exception included for stack trace
logger.error(exception) { "Failed to save customer: ${customer.id}" }

// ❌ BAD: Exception message only, no stack trace
logger.error { "Failed to save customer: ${exception.message}" }
```

### 4. Use Structured Logging

Include structured data that can be parsed by log aggregation tools:

```kotlin
// ✅ GOOD: Structured information
logger.info { 
    "Customer created: name=${customer.name}, id=${customer.id}, email=${customer.email}" 
}

// ❌ BAD: Unstructured message
logger.info { "Customer created: $customer" }
```

### 5. Avoid Logging Sensitive Information

Never log sensitive data such as passwords, tokens, or PII:

```kotlin
// ❌ BAD: Logging sensitive information
logger.debug { "User login: username=$username, password=$password" }

// ✅ GOOD: Exclude sensitive information
logger.debug { "User login attempt: username=$username" }
```

### 6. Use Appropriate Log Levels

Match log levels to the severity and audience:

```kotlin
// ✅ GOOD: Appropriate levels
logger.debug { "Entering method with params: $params" }  // Debug info
logger.info { "Operation completed successfully" }        // Business event
logger.warn { "Using fallback value: $value" }            // Warning
logger.error(exception) { "Operation failed" }            // Error

// ❌ BAD: Wrong log levels
logger.info { "Debug variable value: $debugVar" }  // Should be DEBUG
logger.error { "Operation completed" }              // Should be INFO
```

## Logging Configuration

### Development Configuration

Development uses human-readable text format in `logback.xml`:

```xml
<logger name="io.github.salomax.neotool" level="DEBUG" additivity="false">
  <appender-ref ref="STDOUT" />
</logger>
```

### Production Configuration

Production uses structured JSON format optimized for Loki and OpenTelemetry in `logback-production.xml`:

```xml
<logger name="io.github.salomax.neotool" level="INFO" additivity="false">
  <appender-ref ref="STDOUT" />
  <appender-ref ref="LOKI" />
</logger>
```

## Migration Guide: Replacing `println`

### Step 1: Add Logger

If the class doesn't have a logger, add one:

```kotlin
import mu.KotlinLogging

class MyService {
    private val logger = KotlinLogging.logger {}
    // ... rest of class
}
```

### Step 2: Replace `println` with `logger.debug`

```kotlin
// ❌ BEFORE
println("DEBUG: Processing customer: $customer")

// ✅ AFTER
logger.debug { "Processing customer: $customer" }
```

### Step 3: Remove Debug Prefixes

Since log levels already indicate the purpose, remove "DEBUG:" prefixes:

```kotlin
// ❌ BEFORE
println("DEBUG: CustomerService.get - entity version: ${entity?.version}")

// ✅ AFTER
logger.debug { "CustomerService.get - entity version: ${entity?.version}" }
```

## Testing

### Test Files

For test files, you can:
1. **Remove debug println statements** (preferred for clean tests)
2. **Use logger if needed** (for complex test scenarios)

```kotlin
// ✅ GOOD: Clean test without println
@Test
fun `test customer creation`() {
    val customer = service.create(testCustomer)
    assertThat(customer).isNotNull()
}

// ✅ ALSO ACCEPTABLE: Using logger in tests if needed
class ComplexTest {
    private val logger = KotlinLogging.logger {}
    
    @Test
    fun `test complex scenario`() {
        logger.debug { "Starting complex test scenario" }
        // ... test implementation
    }
}
```

## Code Review Checklist

When reviewing code, check for:

- [ ] No `println` statements in source code (except build scripts)
- [ ] Appropriate log levels used (DEBUG, INFO, WARN, ERROR)
- [ ] Lazy evaluation with Kotlin Logging lambdas
- [ ] Exceptions included in error logs
- [ ] Context information included in log messages
- [ ] No sensitive information in logs
- [ ] Structured logging format for production

## Examples

### Service Class Example

```kotlin
@Singleton
open class CustomerService(
    private val repo: CustomerRepository
) {
    private val logger = KotlinLogging.logger {}

    fun get(id: UUID): Customer? {
        logger.debug { "Fetching customer with ID: $id" }
        val entity = repo.findById(id).orElse(null)
        val customer = entity?.toDomain()
        
        if (customer != null) {
            logger.debug { 
                "Customer found: ${customer.name} (Email: ${customer.email}, Version: ${customer.version})" 
            }
        } else {
            logger.debug { "Customer not found with ID: $id" }
        }
        return customer
    }

    @Transactional
    open fun create(customer: Customer): Customer {
        logger.debug { "Creating customer: ${customer.name}, email: ${customer.email}" }
        try {
            val entity = customer.toEntity()
            val saved = repo.save(entity)
            val result = saved.toDomain()
            logger.info { "Customer created successfully: ${result.name} (ID: ${result.id})" }
            return result
        } catch (e: Exception) {
            logger.error(e) { "Failed to create customer: ${customer.name}" }
            throw e
        }
    }
}
```

### Resolver Class Example

```kotlin
@Singleton
class CustomerResolver(
    customerService: CustomerService,
    override val validator: Validator
) : GenericCrudResolver<Customer, CustomerInputDTO, UUID>() {

    private val logger = KotlinLogging.logger {}
    
    override fun mapToEntity(dto: CustomerInputDTO, id: UUID?): Customer {
        logger.debug { "Mapping DTO to entity: id=$id, name=${dto.name}" }
        
        val existingEntity = if (id != null) {
            service.getById(id)
        } else {
            null
        }
        
        logger.debug { 
            "mapToEntity - id: $id, existingEntity: $existingEntity, version: ${existingEntity?.version}" 
        }
        
        return Customer(
            id = id,
            name = dto.name,
            email = dto.email,
            version = existingEntity?.version ?: 0
        )
    }
}
```

## Related Documentation

- [JPA Entity Patterns](./jpa-entity.md) - Entity logging patterns
- [Testing Guidelines](../testing-guidelines.md) - Test logging practices
- [Architecture Overview](../../ARCHITECTURE_OVERVIEW.md) - System architecture and observability

## Summary

- **Never use `println`** in Kotlin source code (except build scripts)
- **Use `mu.KotlinLogging`** for new code
- **Use lazy evaluation** with lambda expressions
- **Choose appropriate log levels** (DEBUG, INFO, WARN, ERROR)
- **Include context** and structured information
- **Log exceptions properly** with stack traces
- **Avoid sensitive information** in logs

Following these practices ensures consistent, observable, and maintainable logging across all Kotlin services.

