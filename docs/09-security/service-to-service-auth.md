# Service-to-Service Authentication

This guide explains how to implement service-to-service authentication using JWT tokens with service principals.

## Overview

Service-to-service authentication allows services to communicate securely using JWT tokens issued by the Security Service. Each service has its own identity (service principal) with assigned permissions, similar to user principals.

### Architecture

```mermaid
sequenceDiagram
    participant Admin as Admin/CLI
    participant Security as Security Service
    participant ServiceA as Service A
    participant ServiceB as Service B

    Admin->>Security: Register Service A
    Security-->>Admin: Return client_secret
    
    ServiceA->>Security: POST /oauth/token (client_credentials)
    Security-->>ServiceA: JWT service token
    
    ServiceA->>ServiceB: API call (with JWT token)
    ServiceB->>Security: Validate token (JWKS)
    ServiceB-->>ServiceA: API response
```

## Service Registration

### Using CLI

Register a service using the CLI command:

```bash
./neotool service register service-x \
  --permissions "assets:read,assets:write" \
  --output-env
```

This will:
1. Create a service principal in the database
2. Generate and hash a client secret
3. Assign the specified permissions
4. Return the one-time clear client secret

**Important**: Save the client secret immediately - it's only shown once at registration time.

### Using REST API

Register a service via REST API:

```bash
curl -X POST http://localhost:8080/api/internal/services/register \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "service-x",
    "permissions": ["assets:read", "assets:write"]
  }'
```

**Note**: The `clientSecret` is auto-generated server-side for security. It will be returned in the response.

**Response**:
```json
{
  "serviceId": "service-x",
  "principalId": "01234567-89ab-cdef-0123-456789abcdef",
  "clientSecret": "auto-generated-secret-here",
  "permissions": ["assets:read", "assets:write"]
}
```

**Important**: The `clientSecret` is auto-generated server-side and only returned once at registration time. Save it immediately!

## Obtaining Service Tokens

### OAuth2 Client Credentials Flow

Services obtain tokens using the OAuth2 client credentials flow:

```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "client_credentials",
    "client_id": "service-x",
    "client_secret": "your-secret-here",
    "audience": "target-service"
  }'
```

**Response**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### Using ServiceTokenClient (Kotlin)

For Kotlin services, use the `ServiceTokenClient` from the common module:

```kotlin
@Singleton
class MyService(
    private val serviceTokenClient: ServiceTokenClient,
) {
    suspend fun callOtherService() {
        // Get service token
        val token = serviceTokenClient.getServiceToken("target-service")
        
        // Use token in API call
        val response = httpClient.exchange(
            HttpRequest.GET<Any>("http://target-service/api/endpoint")
                .bearerAuth(token)
        )
    }
}
```

**Configuration** (application.yml):
```yaml
security:
  service:
    url: http://security-service:8080
    id: ${SERVICE_ID}
    secret: ${SERVICE_SECRET}
```

## Using Tokens in Service Calls

### Manual Token Usage

Include the token in the `Authorization` header:

```bash
curl -X GET http://target-service/api/endpoint \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Using GraphQLServiceClient (Kotlin)

For GraphQL calls through Apollo Router:

```kotlin
@Singleton
class MyService(
    private val graphQLClient: GraphQLServiceClient,
) {
    suspend fun queryAssets() {
        val response = graphQLClient.query(
            query = """
                query {
                    assets {
                        id
                        name
                    }
                }
            """,
            targetAudience = "apollo-router"
        )
        
        // Process response.data or response.errors
    }
}
```

**Configuration** (application.yml):
```yaml
graphql:
  router:
    url: http://apollo-router:4000/graphql
```

## Token Validation

Service tokens are validated the same way as user tokens:

1. **Extract token** from `Authorization: Bearer <token>` header
2. **Validate signature** using public key from JWKS endpoint
3. **Check expiration** and other standard JWT claims
4. **Extract claims**:
   - `type`: "service"
   - `sub`: Service principal ID (UUID)
   - `aud`: Target audience
   - `permissions`: List of permission names

The existing `JwtTokenValidator` and `RequestPrincipalProvider` already support service tokens - no additional code needed.

## Token Claims

### Service Token Structure

```json
{
  "sub": "01234567-89ab-cdef-0123-456789abcdef",
  "type": "service",
  "aud": "target-service",
  "permissions": ["assets:read", "assets:write"],
  "iat": 1234567890,
  "exp": 1234571490,
  "iss": "neotool-security-service"
}
```

## Authorization

Service principals are authorized using the same permission system as users:

- Permissions are assigned via `principal_permissions` table
- Authorization checks use `AuthorizationManager` (same as users)
- Service tokens include permissions in the `permissions` claim
- Authorization is enforced at GraphQL resolvers and REST endpoints

### Example: Protected Endpoint

```kotlin
@Controller("/api/assets")
class AssetController {
    @Get("/{id}")
    @RequiresAuthorization("assets:read")
    fun getAsset(id: UUID): HttpResponse<Asset> {
        // This endpoint requires "assets:read" permission
        // Works for both user tokens and service tokens
    }
}
```

## Security Best Practices

1. **Store secrets securely**: Never commit client secrets to version control
2. **Use environment variables**: Store `SERVICE_SECRET` in environment or secret manager
3. **Rotate credentials**: Regularly rotate service credentials
4. **Minimal permissions**: Assign only the permissions each service needs
5. **Token caching**: Use `ServiceTokenClient` which caches tokens with TTL
6. **HTTPS only**: Always use HTTPS in production
7. **Audit logging**: All token issuance and authorization decisions are logged

## Troubleshooting

### Invalid Credentials (401)

- Verify `client_id` and `client_secret` are correct
- Check that the service principal exists and is enabled
- Ensure credentials haven't been rotated

### Permission Denied (403)

- Verify the service has the required permissions assigned
- Check that permissions are correctly loaded in the token
- Review `principal_permissions` table

### Token Validation Failed

- Verify the Security Service JWKS endpoint is accessible
- Check that the token hasn't expired
- Ensure the token audience matches the target service

## API Reference

### POST /api/internal/services/register

Register a new service principal.

**Request**:
```json
{
  "serviceId": "string",
  "clientSecret": "string",
  "permissions": ["string"],
  "force": false
}
```

**Response**: `201 Created`
```json
{
  "serviceId": "string",
  "principalId": "uuid",
  "clientSecret": "string",
  "permissions": ["string"]
}
```

### POST /oauth/token

Obtain a service token (OAuth2 client credentials flow).

**Request**:
```json
{
  "grant_type": "client_credentials",
  "client_id": "string",
  "client_secret": "string",
  "audience": "string"
}
```

**Response**: `200 OK`
```json
{
  "access_token": "string",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

## See Also

- [Authentication Guide](./authentication.md) - User authentication
- [Authorization Guide](./authorization.md) - Permission-based authorization
- [ADR-0008](../92-adr/0008-interservice-security.md) - Interservice security architecture

