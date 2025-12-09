---
title: Mapper Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [mapper, graphql, dto, mapping, pattern]
ai_optimized: true
search_keywords: [mapper, mapping, dto, input, graphql, convert, transform]
related:
  - 04-patterns/backend-patterns/resolver-pattern.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 05-standards/api-standards/graphql-standards.md
  - 05-standards/coding-standards/kotlin-standards.md
---

# Mapper Pattern

> **Purpose**: Standard pattern for creating GraphQL mappers that convert between GraphQL input maps, DTOs, domain commands, and domain objects.

## Overview

Mappers handle the conversion between different layers of the application:
- **GraphQL Input Map** → **InputDTO** (from GraphQL resolver)
- **InputDTO** → **Domain Command** (to service layer)
- **Domain Object** → **DTO** (from service to GraphQL response)

Mappers separate mapping concerns from resolver logic, improving testability and maintainability.

## Core Requirements

### Required Annotations

Every mapper MUST have:

1. `@Singleton` - Marks the class as a Micronaut singleton bean

### Required Structure

```kotlin
@Singleton
class {EntityName}Mapper {
    // GraphQL Input Map → InputDTO
    fun mapTo{Create|Update}InputDTO(input: Map<String, Any?>): {Create|Update}InputDTO
    
    // InputDTO → Domain Command
    fun to{Create|Update}Command(input: {Create|Update}InputDTO): {Create|Update}Command
    
    // Domain Object → DTO
    fun to{EntityName}DTO(domain: {DomainName}): {EntityName}DTO
}
```

## Mapping Responsibilities

### 1. GraphQL Input Map → InputDTO

Converts the raw GraphQL input map to a typed DTO with validation.

```kotlin
fun mapToUpdateGroupInputDTO(input: Map<String, Any?>): UpdateGroupInputDTO {
    return UpdateGroupInputDTO(
        name = extractField<String>(input, "name"),
        description = extractField<String?>(input, "description", null),
        userIds = extractListField(input, "userIds"),
    )
}
```

### 2. InputDTO → Domain Command

Converts the validated DTO to a domain command for the service layer.

```kotlin
fun toUpdateGroupCommand(
    groupId: String,
    input: UpdateGroupInputDTO,
): GroupManagement.UpdateGroupCommand {
    val groupIdUuid = UUID.fromString(groupId.trim())
    
    return GroupManagement.UpdateGroupCommand(
        groupId = groupIdUuid,
        name = input.name,
        description = input.description,
        userIds = convertUserIds(input.userIds),
    )
}
```

### 3. Domain Object → DTO

Converts domain objects to DTOs for GraphQL responses.

```kotlin
fun toGroupDTO(group: Group): GroupDTO {
    return GroupDTO(
        id = group.id?.toString() ?: throw IllegalArgumentException("Group must have an ID"),
        name = group.name,
        description = group.description,
    )
}
```

## Critical Pattern: List Handling in Update Operations

### The Problem

When handling optional list fields in **update operations**, you must distinguish between three semantic states:

1. **`null`** = Don't change the existing list
2. **`[]` (empty array)** = Remove all items from the list
3. **`[id1, id2, ...]`** = Synchronize the list to match these items

### Common Pitfall

Using `.takeIf { it.isNotEmpty() }` incorrectly converts empty arrays to `null`, losing the semantic distinction:

```kotlin
// ❌ INCORRECT - Loses distinction between null and empty array
val userIds = input.userIds
    ?.filter { it.isNotBlank() }
    ?.map { UUID.fromString(it) }
    ?.takeIf { it.isNotEmpty() }  // Empty array becomes null!
```

**Problem**: When `userIds = []`, this returns `null`, so the service can't distinguish between "don't change" and "remove all".

### Correct Pattern

Always check for empty lists **before** processing, and preserve empty arrays:

```kotlin
// ✅ CORRECT - Preserves empty arrays for update operations
fun mapToUpdateGroupInputDTO(input: Map<String, Any?>): UpdateGroupInputDTO {
    val userIds: List<String>? = when (val userIdsValue = input["userIds"]) {
        null -> null  // null means don't change memberships
        is List<*> -> {
            // Check if it's an empty list first
            if (userIdsValue.isEmpty()) {
                emptyList()  // empty array means remove all users
            } else {
                // Non-empty list: filter and process
                val extracted = userIdsValue
                    .mapNotNull { it?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                // Only return null if all were blank/invalid, otherwise return the processed list
                extracted.takeIf { it.isNotEmpty() }
            }
        }
        else -> null
    }

    return UpdateGroupInputDTO(
        name = extractField<String>(input, "name"),
        description = extractField<String?>(input, "description", null),
        userIds = userIds,
    )
}
```

### Command Conversion Pattern

When converting DTO to Command, preserve the same distinction:

```kotlin
// ✅ CORRECT - Preserves empty arrays in command conversion
fun toUpdateGroupCommand(
    groupId: String,
    input: UpdateGroupInputDTO,
): GroupManagement.UpdateGroupCommand {
    val groupIdUuid = UUID.fromString(groupId.trim())
    
    // Handle userIds: preserve empty arrays (remove all) vs null (don't change)
    val inputUserIds = input.userIds
    val userIds: List<UUID>? = when {
        inputUserIds == null -> null  // null means don't change memberships
        inputUserIds.isEmpty() -> emptyList()  // empty array means remove all users
        else -> {
            // Non-empty array: filter, map, and validate
            val processed = inputUserIds
                .filter { it.isNotBlank() }
                .map { userIdString ->
                    UUID.fromString(userIdString.trim())
                }
            // Only return null if all were blank/invalid, otherwise return the processed list
            processed.takeIf { it.isNotEmpty() }
        }
    }

    return GroupManagement.UpdateGroupCommand(
        groupId = groupIdUuid,
        name = input.name,
        description = input.description,
        userIds = userIds,
    )
}
```

### Service Layer Handling

The service layer should handle all three states:

```kotlin
@Transactional
open fun updateGroup(command: GroupManagement.UpdateGroupCommand): Group {
    // ... update entity fields ...
    
    // Handle user membership synchronization if userIds is provided
    if (command.userIds != null) {
        if (command.userIds.isEmpty()) {
            // Empty list means remove all memberships
            removeAllGroupMemberships(saved.id)
        } else {
            // Non-empty list means synchronize memberships
            synchronizeGroupMemberships(saved.id, command.userIds)
        }
    }
    // If userIds is null, no change to memberships
    
    return saved.toDomain()
}
```

## When to Apply This Pattern

### Apply This Pattern When:

- ✅ **Update operations** with optional list fields that need synchronization
- ✅ List fields where empty array has semantic meaning (e.g., "remove all")
- ✅ Fields that represent relationships (e.g., group members, role permissions)

### Don't Apply When:

- ❌ **Create operations** - null and empty are typically equivalent (no existing data to preserve)
- ❌ List fields where empty array has no special meaning
- ❌ Required list fields (must always be provided)

## Best Practices

### 1. Use `when` Expressions for Clear Logic

```kotlin
val userIds: List<String>? = when (val userIdsValue = input["userIds"]) {
    null -> null
    is List<*> -> {
        if (userIdsValue.isEmpty()) {
            emptyList()
        } else {
            // process non-empty list
        }
    }
    else -> null
}
```

### 2. Extract to Local Variables for Smart Casting

```kotlin
// Store in local variable to enable smart casting
val inputUserIds = input.userIds
val userIds: List<UUID>? = when {
    inputUserIds == null -> null
    inputUserIds.isEmpty() -> emptyList()
    else -> { /* process */ }
}
```

### 3. Validate and Transform in Separate Steps

```kotlin
// Step 1: Extract and validate format
val extracted = userIdsValue
    .mapNotNull { it?.toString()?.trim() }
    .filter { it.isNotBlank() }

// Step 2: Transform to domain types
val processed = extracted.map { UUID.fromString(it) }

// Step 3: Handle empty result (all invalid)
processed.takeIf { it.isNotEmpty() }
```

### 4. Use Type-Safe Field Extraction

```kotlin
inline fun <reified T> extractField(
    input: Map<String, Any?>,
    name: String,
    defaultValue: T? = null,
): T {
    val value = input[name]
    if (value == null) {
        return defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
    }
    return if (value is T) {
        value
    } else {
        defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
    }
}
```

## Complete Example

### GroupManagementMapper

```kotlin
@Singleton
class GroupManagementMapper {
    /**
     * Map GraphQL input map to UpdateGroupInputDTO
     */
    fun mapToUpdateGroupInputDTO(input: Map<String, Any?>): UpdateGroupInputDTO {
        // Handle userIds: preserve empty arrays (remove all) vs null (don't change)
        val userIds: List<String>? = when (val userIdsValue = input["userIds"]) {
            null -> null  // null means don't change memberships
            is List<*> -> {
                // Check if it's an empty list first
                if (userIdsValue.isEmpty()) {
                    emptyList()  // empty array means remove all users
                } else {
                    // Non-empty list: filter and process
                    val extracted = userIdsValue
                        .mapNotNull { it?.toString()?.trim() }
                        .filter { it.isNotBlank() }
                    // Only return null if all were blank/invalid, otherwise return the processed list
                    extracted.takeIf { it.isNotEmpty() }
                }
            }
            else -> null
        }

        return UpdateGroupInputDTO(
            name = extractField<String>(input, "name"),
            description = extractField<String?>(input, "description", null),
            userIds = userIds,
        )
    }

    /**
     * Convert UpdateGroupInputDTO to UpdateGroupCommand
     */
    fun toUpdateGroupCommand(
        groupId: String,
        input: UpdateGroupInputDTO,
    ): GroupManagement.UpdateGroupCommand {
        val groupIdUuid = UUID.fromString(groupId.trim())
        
        // Handle userIds: preserve empty arrays (remove all) vs null (don't change)
        val inputUserIds = input.userIds
        val userIds: List<UUID>? = when {
            inputUserIds == null -> null
            inputUserIds.isEmpty() -> emptyList()
            else -> {
                val processed = inputUserIds
                    .filter { it.isNotBlank() }
                    .map { UUID.fromString(it.trim()) }
                processed.takeIf { it.isNotEmpty() }
            }
        }

        return GroupManagement.UpdateGroupCommand(
            groupId = groupIdUuid,
            name = input.name,
            description = input.description,
            userIds = userIds,
        )
    }

    /**
     * Convert Group domain object to GroupDTO
     */
    fun toGroupDTO(group: Group): GroupDTO {
        return GroupDTO(
            id = group.id?.toString() ?: throw IllegalArgumentException("Group must have an ID"),
            name = group.name,
            description = group.description,
        )
    }
}
```

## Related Patterns

- **Resolver Pattern**: Resolvers use mappers to convert GraphQL input to DTOs
- **Service Pattern**: Services receive domain commands from mappers
- **Domain-Entity Conversion**: Mappers may also handle domain-entity conversion

## See Also

- [Resolver Pattern](./resolver-pattern.md) - How resolvers use mappers
- [Service Pattern](./service-pattern.md) - How services handle commands
- [GraphQL Standards](../../05-standards/api-standards/graphql-standards.md) - GraphQL API conventions

