---
id: service-kotlin-virtual-threads
title: Service — Virtual Threads Configuration (Kotlin + Micronaut + Java 21)
area: service
version: 1.0
tags: [kotlin, micronaut, java21, virtual-threads, performance, concurrency, loom]
last_reviewed: 2025-01-15
---

# Virtual Threads Configuration in Kotlin Service

This guide covers the configuration and best practices for using virtual threads in our Kotlin service with Micronaut and Java 21.

## Overview

Virtual threads (Project Loom) were introduced in Java 19 as a preview feature and became stable in Java 21. They provide lightweight, high-throughput concurrency for I/O-bound operations, allowing millions of virtual threads to run on a small number of platform threads.

## Requirements

### Java Version
- **Minimum**: Java 21 (virtual threads are stable)
- **JRE**: Must be Java 21 or later (e.g., `eclipse-temurin:21-jre`)

### Micronaut Version
- **Minimum**: Micronaut 4.0.0+ (virtual thread support)
- **Recommended**: Micronaut 4.9.0+ (improved virtual thread integration)

## Configuration

### Application Configuration

All services **MUST** include the following configuration in `application.yml`:

```yaml
micronaut:
  executors:
    blocking:
      type: fixed
      virtual: true
  server:
    thread-selection: blocking
```

**Key Configuration Points:**
- `executors.blocking.virtual: true` - Enables virtual threads for the `BLOCKING` executor
- `executors.blocking.type: fixed` - Uses a fixed thread pool executor type
- `server.thread-selection: blocking` - Configures server to handle blocking operations appropriately

### Complete Configuration Example

```yaml
micronaut:
  application:
    name: my-service
  executors:
    blocking:
      type: fixed
      virtual: true
  server:
    port: 8080
    thread-selection: blocking
    cors:
      enabled: true
      # ... other server config
```

## Annotation Placement

### ✅ REQUIRED: Controllers (Entry Points)

All HTTP controller methods that handle requests **MUST** be annotated with `@ExecuteOn(TaskExecutors.BLOCKING)`:

```kotlin
@Controller("/graphql")
class GraphQLController {
    @Post(consumes = [APPLICATION_JSON], produces = [APPLICATION_JSON])
    @ExecuteOn(TaskExecutors.BLOCKING)  // ✅ REQUIRED
    fun post(
        @Body request: GraphQLRequest,
        @Header("Authorization") authorization: String?
    ): Map<String, Any?> {
        // Controller logic
    }
}
```

**Why**: Ensures the entire request handling runs on virtual threads from the entry point.

### ✅ REQUIRED: GraphQL Resolvers

All GraphQL resolver methods **MUST** be annotated with `@ExecuteOn(TaskExecutors.BLOCKING)`:

```kotlin
@Singleton
class ProductResolver(
    private val productService: ProductService
) {
    @ExecuteOn(TaskExecutors.BLOCKING)  // ✅ REQUIRED
    fun searchProducts(query: String): List<Product> {
        return productService.search(query)
    }
    
    @ExecuteOn(TaskExecutors.BLOCKING)  // ✅ REQUIRED
    fun product(id: String): Product? {
        return productService.get(UUID.fromString(id))
    }
    
    @ExecuteOn(TaskExecutors.BLOCKING)  // ✅ REQUIRED
    fun saveProduct(input: Map<String, Any?>): Product {
        // Mutation logic
    }
}
```

**Why**: GraphQL execution may switch threads internally. Resolver annotations ensure virtual threads are used even if GraphQL switches threads.

### ❌ NOT REQUIRED: Service Layer

Service methods **SHOULD NOT** have `@ExecuteOn` annotations:

```kotlin
@Singleton
class ProductService(
    private val repo: ProductRepository
) {
    // ❌ NO @ExecuteOn annotation needed
    fun search(query: String): List<Product> {
        return repo.findAll().map { it.toDomain() }
    }
    
    // ❌ NO @ExecuteOn annotation needed
    fun get(id: UUID): Product? {
        return repo.findById(id).orElse(null)?.toDomain()
    }
}
```

**Why**: 
- Services inherit virtual thread context from resolvers/controllers
- Services should remain framework-agnostic
- Threading is an infrastructure concern, not a business logic concern

## Thread Context Flow

The virtual thread context flows through the call chain:

```
HTTP Request
  → Controller [@ExecuteOn] (Virtual Thread A)
    → GraphQL.execute()
      → DataFetcher
        → Resolver [@ExecuteOn] (Virtual Thread A or B)
          → Service (inherits Virtual Thread)
            → Repository (inherits Virtual Thread)
```

## Best Practices

### 1. Annotation Strategy

**✅ DO:**
- Annotate all controller methods
- Annotate all resolver methods
- Keep services annotation-free

**❌ DON'T:**
- Add annotations to service methods
- Add annotations to repository methods
- Mix virtual thread and platform thread executors unnecessarily

### 2. I/O-Bound Operations

Virtual threads are ideal for:
- ✅ Database operations (JPA/Hibernate)
- ✅ HTTP client calls
- ✅ File I/O operations
- ✅ Network operations

Virtual threads are **NOT** ideal for:
- ❌ CPU-intensive computations
- ❌ Long-running calculations
- ❌ Blocking operations that hold locks

### 3. Logging Thread Information

All logback configurations **MUST** include thread information in the log pattern:

**Development (`logback.xml`):**
```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] [%X{correlationId:-}] %-5level %logger{36} - %msg%n</pattern>
```

**Production (`logback-production.xml`):**
```xml
<!-- JSON encoder -->
<pattern>
  <pattern>
    {
      "thread": "%thread",
      "service": "my-service",
      ...
    }
  </pattern>
</pattern>

<!-- Loki message -->
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId:-}] %-5level %logger{36} - %msg%n</pattern>
```

**Why**: Enables verification that virtual threads are being used (thread names like `ForkJoinPool-1-worker-1` with `isVirtual: true`).

## Verification

### Checking Virtual Thread Usage

1. **Check Logs**: Look for thread names in logs
   - Virtual threads: `ForkJoinPool-1-worker-1`, `VirtualThread-*`
   - Platform threads: `default-nioEventLoopGroup-*-*`, `pool-*-thread-*`

2. **Verify Configuration**: Ensure `application.yml` has the required settings

3. **Test Execution**: Run a request (e.g., GraphQL query, REST endpoint) and check logs for thread information

### Expected Behavior

- ✅ Controller methods run on virtual threads
- ✅ Resolver methods run on virtual threads
- ✅ Service methods inherit virtual threads
- ✅ Database operations run on virtual threads

## Common Issues

### Issue: Still Seeing Platform Threads

**Symptoms**: Logs show `default-nioEventLoopGroup-*-*` threads

**Solutions**:
1. Verify `application.yml` configuration is correct
2. Ensure `@ExecuteOn(TaskExecutors.BLOCKING)` is on controllers and resolvers
3. Restart the application after configuration changes
4. Verify Java 21 is being used: `java -version`

### Issue: Configuration Not Applied

**Symptoms**: `ExecutorType.virtual` error or configuration not recognized

**Solutions**:
1. Use `type: fixed` with `virtual: true` (not `type: virtual`)
2. Ensure Micronaut version is 4.0.0+
3. Check for typos in `application.yml`

## Migration Guide

### From Platform Threads to Virtual Threads

1. **Update Configuration**: Add virtual thread configuration to `application.yml`
2. **Add Annotations**: Add `@ExecuteOn(TaskExecutors.BLOCKING)` to controllers and resolvers
3. **Update Logging**: Add thread information to logback patterns
4. **Test**: Verify virtual threads are being used
5. **Monitor**: Check performance and resource usage

## Performance Considerations

### Benefits

- **High Concurrency**: Support millions of concurrent operations
- **Low Overhead**: Minimal memory footprint per virtual thread
- **Better Scalability**: Improved throughput for I/O-bound workloads
- **Simpler Code**: No need for complex async/await patterns

### Limitations

- **CPU-Bound Work**: Not suitable for CPU-intensive operations
- **Pin Operations**: Some operations may pin virtual threads to platform threads
- **Thread-Local Storage**: Use with caution (prefer scoped values in Java 21+)

## Dependencies

### Required Dependencies

No additional dependencies are required. Virtual threads are part of Java 21 and Micronaut 4.0.0+.

### Configuration Dependencies

Ensure your build configuration targets Java 21:

```kotlin
// build.gradle.kts
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
```

## References

- [Micronaut Virtual Threads Documentation](https://docs.micronaut.io/latest/guide/index.html#virtualThreads)
- [Java Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
- [Project Loom](https://openjdk.org/projects/loom/)

## Summary

**Key Rules:**
1. ✅ Configure `executors.blocking.virtual: true` in `application.yml`
2. ✅ Add `@ExecuteOn(TaskExecutors.BLOCKING)` to all controller methods
3. ✅ Add `@ExecuteOn(TaskExecutors.BLOCKING)` to all resolver methods
4. ❌ Do NOT add annotations to service methods
5. ✅ Include thread information in logback patterns
6. ✅ Use Java 21+ for stable virtual thread support

This ensures consistent virtual thread usage across all services and enables optimal performance for I/O-bound operations.

