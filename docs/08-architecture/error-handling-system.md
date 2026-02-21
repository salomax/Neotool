---
title: Error Handling System - Industry Best Practice Implementation
type: architecture
category: error-handling
status: current
version: 1.0.0
tags: [error-handling, i18n, graphql, validation, best-practices]
ai_optimized: true
search_keywords: [error codes, validation, GraphQL errors, internationalization, error translation]
related:
  - 03-features/security/authorization/README.md
  - 07-frontend/patterns/i18n-pattern.md
---

# Error Handling System

> **Purpose**: Industry-standard error handling system with machine-readable error codes for i18n translation across the entire stack.

## Overview

This document describes Neotool's error handling architecture, which implements industry best practices used by companies like Stripe, GitHub, and AWS. The system provides:

- **Machine-readable error codes** for reliable error handling
- **Automatic i18n translation** of error messages
- **Type-safe error handling** with custom exception classes
- **Consistent error structure** from backend to frontend
- **Parameter interpolation** for dynamic error messages

## Architecture

### Error Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Backend (Kotlin)                                            │
├─────────────────────────────────────────────────────────────┤
│ 1. Validation fails                                         │
│    throw ValidationException(                               │
│      errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,   │
│      field = "accountName"                                  │
│    )                                                        │
│                                                             │
│ 2. GraphQL Exception Handler catches exception             │
│    Converts to GraphQL error with extensions:              │
│    {                                                        │
│      message: "Account name is required...",               │
│      extensions: {                                          │
│        code: "ACCOUNT_NAME_REQUIRED",                       │
│        parameters: {...}                                    │
│      }                                                      │
│    }                                                        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Frontend (TypeScript)                                       │
├─────────────────────────────────────────────────────────────┤
│ 3. extractErrorMessage() extracts error code               │
│    errorCode = "ACCOUNT_NAME_REQUIRED"                      │
│                                                             │
│ 4. i18n translates error code                              │
│    t("errors.ACCOUNT_NAME_REQUIRED")                        │
│                                                             │
│ 5. User sees translated message in their language          │
│    EN: "Account name is required and cannot be blank"       │
│    PT: "O nome da conta é obrigatório e não pode estar     │
│         em branco"                                          │
└─────────────────────────────────────────────────────────────┘
```

## Backend Implementation

### 1. Error Code Constants

Error codes are defined as enums implementing the `ErrorCode` interface:

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/error/ErrorCode.kt`

```kotlin
interface ErrorCode {
    val code: String                // Machine-readable code (e.g., "ACCOUNT_NAME_REQUIRED")
    val defaultMessage: String      // Human-readable fallback (for logs)
    val httpStatus: Int            // HTTP status code hint
}

enum class CommonErrorCode(
    override val code: String,
    override val defaultMessage: String,
    override val httpStatus: Int = 400,
) : ErrorCode {
    UNKNOWN_ERROR("UNKNOWN_ERROR", "An unexpected error occurred", 500),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed", 400),
    NOT_FOUND("NOT_FOUND", "Resource not found", 404),
    // ... more codes
}
```

**Domain-Specific Error Codes**:
- `SecurityErrorCode` - Authentication, authorization, account management
- `AssetErrorCode` - File storage, uploads, downloads
- `CommonErrorCode` - Generic errors used across domains

### 2. Domain Exceptions

Custom exception classes that carry error codes:

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/error/DomainException.kt`

```kotlin
abstract class DomainException(
    val errorCode: ErrorCode,
    val field: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(errorCode.defaultMessage, cause)

// Specific exception types
class ValidationException(errorCode: ErrorCode, field: String?, ...) : DomainException(...)
class NotFoundException(errorCode: ErrorCode, ...) : DomainException(...)
class AuthorizationException(errorCode: ErrorCode, ...) : DomainException(...)
class ConflictException(errorCode: ErrorCode, ...) : DomainException(...)
```

### 3. Validation with Error Codes

**Before** (hardcoded messages):
```kotlin
init {
    require(accountName.isNotBlank()) {
        "Account name is required and cannot be blank"
    }
}
```

**After** (error codes):
```kotlin
init {
    if (accountName.isBlank()) {
        throw ValidationException(
            errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,
            field = "accountName",
        )
    }
}
```

**With parameters** (for interpolation):
```kotlin
if (accountName.length > MAX_ACCOUNT_NAME_LENGTH) {
    throw ValidationException(
        errorCode = SecurityErrorCode.ACCOUNT_NAME_TOO_LONG,
        field = "accountName",
        parameters = mapOf(
            "maxLength" to MAX_ACCOUNT_NAME_LENGTH,
            "actualLength" to accountName.length,
        ),
    )
}
```

### 4. GraphQL Error Handling

The `GraphQLPayloadFactory` automatically converts exceptions to GraphQL errors:

**Location**: `service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/graphql/payload/GraphQLPayload.kt`

```kotlin
data class GraphQLError(
    val field: List<String>,
    val message: String,       // For logging/debugging
    val code: String,          // For frontend i18n translation (MANDATORY)
    val parameters: Map<String, Any>? = null,
)

object GraphQLPayloadFactory {
    fun <T> error(exception: Exception): GraphQLPayload<T> {
        when (exception) {
            is DomainException -> {
                GraphQLError(
                    field = exception.field?.split(".")?.toList() ?: listOf("general"),
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,  // ← Sent to frontend
                    parameters = exception.parameters,
                )
            }
            // ... other exception types
        }
    }
}
```

### 5. Exception Handlers

GraphQL exception handlers convert exceptions to error responses:

**Location**: `service/kotlin/assets/src/main/kotlin/io/github/salomax/neotool/assets/graphql/AssetGraphQLExceptionHandler.kt`

```kotlin
class AssetGraphQLExceptionHandler : DataFetcherExceptionHandler {
    override fun handleException(params: DataFetcherExceptionHandlerParameters) {
        when (val exception = params.exception) {
            is DomainException -> {
                buildErrorResponse(
                    message = exception.errorCode.defaultMessage,
                    code = exception.errorCode.code,
                    parameters = exception.parameters,
                )
            }
            // ... other exception types
        }
    }
}
```

## Frontend Implementation

### 1. Error Translation Files

Comprehensive error code translations in all supported languages:

**Location**: `web/src/shared/i18n/locales/en/common.json`

```json
{
  "errors": {
    "ACCOUNT_NAME_REQUIRED": "Account name is required and cannot be blank",
    "ACCOUNT_NAME_TOO_LONG": "Account name must be 100 characters or less",
    "AUTH_INVALID_CREDENTIALS": "Invalid email or password",
    "NOT_FOUND": "Resource not found",
    // ... 60+ error codes
  }
}
```

**Location**: `web/src/shared/i18n/locales/pt/common.json`

```json
{
  "errors": {
    "ACCOUNT_NAME_REQUIRED": "O nome da conta é obrigatório e não pode estar em branco",
    "ACCOUNT_NAME_TOO_LONG": "O nome da conta deve ter no máximo 100 caracteres",
    "AUTH_INVALID_CREDENTIALS": "E-mail ou senha inválidos",
    "NOT_FOUND": "Recurso não encontrado",
    // ... 60+ error codes in Portuguese
  }
}
```

### 2. Error Extraction Utilities

**Location**: `web/src/shared/utils/error.ts`

```typescript
/**
 * Extracts error code from GraphQL error extensions
 */
export function extractErrorCode(error: unknown): string | null {
  const graphQLError = error.graphQLErrors?.[0];
  return graphQLError?.extensions?.code || null;
}

/**
 * Extracts and translates error message
 *
 * Flow:
 * 1. Extract error code from GraphQL extensions
 * 2. Translate code using i18n
 * 3. Fall back to cleaned message if no code
 * 4. Detect and translate connection errors
 */
export function extractErrorMessage(
  error: unknown,
  defaultMessage: string = 'An error occurred'
): string {
  // Extract error code
  const errorCode = extractErrorCode(error);

  if (errorCode) {
    const parameters = extractErrorParameters(error);
    const fallback = cleanErrorMessage(extractRawMessage(error));

    // Translate using error code
    return getTranslatedError(errorCode, fallback, parameters);
  }

  // Fallback to message translation
  // ...
}
```

### 3. Usage in Components

```typescript
import { extractErrorMessage } from '@/shared/utils/error';

try {
  await createAccount({ accountName });
} catch (err) {
  // Automatically extracts code, translates, and shows in user's language
  const errorMessage = extractErrorMessage(err);
  showError(errorMessage);
}
```

## Error Codes Catalog

### Common Errors
| Code | Description |
|------|-------------|
| `UNKNOWN_ERROR` | An unexpected error occurred |
| `INTERNAL_ERROR` | Internal server error |
| `INVALID_INPUT` | Invalid input provided |
| `VALIDATION_ERROR` | Validation failed |
| `NOT_FOUND` | Resource not found |
| `CONFLICT` | Resource conflict |
| `INVALID_STATE` | Operation not valid in current state |
| `OPTIMISTIC_LOCK_ERROR` | Resource modified by another user |
| `RATE_LIMIT_EXCEEDED` | Too many requests |
| `SERVICE_UNAVAILABLE` | Service temporarily unavailable |

### Authentication/Authorization Errors
| Code | Description |
|------|-------------|
| `AUTH_AUTHENTICATION_REQUIRED` | Authentication required |
| `AUTH_INVALID_CREDENTIALS` | Invalid email or password |
| `AUTH_TOKEN_EXPIRED` | Session expired |
| `AUTH_AUTHORIZATION_DENIED` | Access denied |
| `AUTH_INSUFFICIENT_PERMISSIONS` | Insufficient permissions |

### Account Management Errors
| Code | Description |
|------|-------------|
| `ACCOUNT_NAME_REQUIRED` | Account name is required |
| `ACCOUNT_NAME_TOO_LONG` | Account name exceeds max length |
| `ACCOUNT_TYPE_INVALID` | Invalid account type |
| `ACCOUNT_TYPE_MUST_BE_FAMILY_OR_BUSINESS` | Account type must be FAMILY or BUSINESS |
| `ACCOUNT_NOT_FOUND` | Account not found |
| `ACCOUNT_ALREADY_EXISTS` | Account already exists |
| `MUST_HAVE_ONE_ACTIVE_ACCOUNT` | User must have at least one active account |

### Asset/Storage Errors
| Code | Description |
|------|-------------|
| `ASSET_STORAGE_UNAVAILABLE` | Storage service unavailable |
| `ASSET_STORAGE_QUOTA_EXCEEDED` | Storage quota exceeded |
| `ASSET_NOT_FOUND` | File not found |
| `ASSET_SIZE_EXCEEDED` | File size exceeds maximum |
| `ASSET_FORMAT_UNSUPPORTED` | File format not supported |

## Best Practices

### ✅ DO

1. **Always use error codes** for domain exceptions
   ```kotlin
   throw ValidationException(
       errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,
       field = "accountName",
   )
   ```

2. **Add translations** for new error codes in ALL languages
   ```json
   // en/common.json
   "NEW_ERROR_CODE": "English message"

   // pt/common.json
   "NEW_ERROR_CODE": "Mensagem em português"
   ```

3. **Use parameters** for dynamic messages
   ```kotlin
   throw ValidationException(
       errorCode = SecurityErrorCode.ACCOUNT_NAME_TOO_LONG,
       field = "accountName",
       parameters = mapOf("maxLength" to 100, "actualLength" to 150),
   )
   ```

4. **Extract error messages** using the utility
   ```typescript
   const errorMessage = extractErrorMessage(error);
   ```

### ❌ DON'T

1. **Don't hardcode error messages**
   ```kotlin
   // ❌ Bad
   throw IllegalArgumentException("Account name is required")

   // ✅ Good
   throw ValidationException(
       errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,
       field = "accountName",
   )
   ```

2. **Don't show raw backend messages** to users
   ```typescript
   // ❌ Bad
   showError(error.message)

   // ✅ Good
   showError(extractErrorMessage(error))
   ```

3. **Don't create error codes without translations**
   - Every error code must have translations in ALL supported languages

4. **Don't change error codes** once released
   - Error codes are part of the API contract
   - Frontend depends on them for translation

## Adding New Error Codes

1. **Define error code constant**:
   ```kotlin
   // In appropriate domain ErrorCode enum
   NEW_ERROR("NEW_ERROR_CODE", "Default message", 400)
   ```

2. **Add translations**:
   ```json
   // en/common.json
   "errors": {
     "NEW_ERROR_CODE": "English message"
   }

   // pt/common.json
   "errors": {
     "NEW_ERROR_CODE": "Mensagem em português"
   }
   ```

3. **Use in validation**:
   ```kotlin
   if (condition) {
       throw ValidationException(
           errorCode = DomainErrorCode.NEW_ERROR,
           field = "fieldName",
       )
   }
   ```

4. **Test end-to-end**:
   - Trigger error in backend
   - Verify error code in GraphQL response
   - Verify translation in frontend

## Testing

### Backend Tests

```kotlin
@Test
fun `should throw ValidationException with error code`() {
    val exception = assertThrows<ValidationException> {
        CreateAccountCommand(
            accountName = "",
            accountType = AccountType.FAMILY,
            ownerUserId = UUID.randomUUID(),
        )
    }

    assertEquals(SecurityErrorCode.ACCOUNT_NAME_REQUIRED, exception.errorCode)
    assertEquals("accountName", exception.field)
}
```

### Frontend Tests

```typescript
it('should extract and translate error code', () => {
  const error = {
    graphQLErrors: [{
      message: "Account name is required",
      extensions: { code: "ACCOUNT_NAME_REQUIRED" }
    }]
  };

  const message = extractErrorMessage(error);
  expect(message).toBe("Account name is required and cannot be blank");
});
```

## Migration from Old System

### Old Approach (Hardcoded Messages)
```kotlin
require(accountName.isNotBlank()) {
    "Account name is required and cannot be blank"
}
```

### New Approach (Error Codes)
```kotlin
if (accountName.isBlank()) {
    throw ValidationException(
        errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,
        field = "accountName",
    )
}
```

## Benefits

1. **✅ Internationalization**: Automatic translation to user's language
2. **✅ Type Safety**: Compile-time validation of error codes
3. **✅ Consistency**: Same error structure across entire application
4. **✅ Maintainability**: Easy to update error messages
5. **✅ Debugging**: Error codes make debugging easier
6. **✅ API Stability**: Error codes don't change, messages can
7. **✅ Analytics**: Track error frequencies by code
8. **✅ Testing**: Easier to test specific error conditions

## Real-World Examples

### Stripe API
```json
{
  "error": {
    "type": "card_error",
    "code": "card_declined",
    "message": "Your card was declined."
  }
}
```

### GitHub API
```json
{
  "message": "Validation Failed",
  "errors": [{
    "resource": "Issue",
    "code": "missing_field",
    "field": "title"
  }]
}
```

### AWS API
```json
{
  "Code": "InvalidParameterValue",
  "Message": "Value (abc) for parameter is invalid"
}
```

## Summary

Neotool's error handling system implements industry best practices by:

- Using **machine-readable error codes** instead of human-readable messages
- Providing **automatic i18n translation** for all error messages
- Maintaining **type safety** with custom exception classes
- Ensuring **consistent error structure** from backend to frontend
- Supporting **parameter interpolation** for dynamic messages
- Following patterns used by **Stripe, GitHub, and AWS**

This architecture ensures that users always see error messages in their preferred language, errors are easy to handle programmatically, and the system is maintainable and extensible.
