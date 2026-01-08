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
  --permissions "assets:asset:view,assets:asset:upload,assets:asset:delete" \
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
    "permissions": [assets:asset:view", "assets:asset:upload", "assets:asset:delete"]
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

### Storing Client Credentials in Production

After registering a service and receiving the `client_secret`, you **must** store it securely. The `client_secret` is only shown once during registration and cannot be retrieved later from the database (it's hashed).

**Quick Setup Summary**: Once you have `SERVICE_ID` and `SERVICE_SECRET` as environment variables, the service will automatically:
1. Use `ServiceTokenClient` to obtain JWT tokens from the Security Service
2. Cache tokens with TTL to minimize API calls
3. Include tokens in service-to-service API calls

That's it! The service handles everything else automatically.

#### Why Store in Vault?

Storing credentials in Vault (or your secret manager) is critical for production because:

✅ **Security**: Secrets are encrypted at rest and in transit  
✅ **Audit Trail**: All secret access is logged for compliance  
✅ **Centralized Management**: One place to manage all service credentials  
✅ **Rotation**: Easy credential rotation without code changes  
✅ **Multi-Pod Support**: All pods read from the same source  
✅ **RBAC**: Fine-grained access control via Vault policies  
✅ **Disaster Recovery**: Secrets can be backed up and restored  

**Without Vault**: You'd need to manually manage secrets in multiple places, increasing risk of exposure and making rotation difficult.

#### Step 1: Store Credentials in Vault

Store the `client_secret` in Vault using the KV v2 secrets engine. The recommended path structure is:

```
secret/services/{service-id}/credentials
```

**Using Vault CLI**:

```bash
# Set Vault address and authenticate
export VAULT_ADDR=http://vault.neotool-security.svc.cluster.local:8200
export VAULT_TOKEN=$(kubectl get secret vault-token -n neotool-security -o jsonpath='{.data.token}' | base64 -d)

# Store the client secret (replace with your actual values from registration)
vault kv put secret/services/service-x/credentials \
  client_id="service-x" \
  client_secret="your-auto-generated-secret-here"
```

**Verify the secret was stored**:
```bash
vault kv get secret/services/service-x/credentials
```

**Using Terraform** (Recommended for Infrastructure as Code):

Create or update your Terraform configuration:

```hcl
# Store service credentials in Vault
resource "vault_kv_secret_v2" "service_x_credentials" {
  mount = "secret"
  name  = "services/service-x/credentials"
  
  data_json = jsonencode({
    client_id     = "service-x"
    client_secret = var.service_x_client_secret  # Pass via terraform.tfvars or environment
  })
  
  # Optional: Add metadata
  custom_metadata = {
    service_name = "service-x"
    created_by   = "terraform"
    environment  = "production"
  }
}

# Variable definition (in variables.tf)
variable "service_x_client_secret" {
  description = "Client secret for service-x (obtained from service registration)"
  type        = string
  sensitive   = true  # Marks as sensitive in Terraform
}
```

**Why Terraform?**
- **Version Control**: Track secret paths and structure in git (not the secrets themselves)
- **Reproducibility**: Same infrastructure across environments
- **State Management**: Terraform tracks what secrets exist
- **CI/CD Integration**: Automate secret provisioning in pipelines
- **Multi-Environment**: Use workspaces or variables for dev/staging/prod

Apply with:
```bash
terraform apply -var="service_x_client_secret=your-secret-here"
```

#### Step 2: Sync to Kubernetes (Optional but Recommended)

For Kubernetes deployments, use External Secrets Operator to automatically sync from Vault to Kubernetes Secrets:

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: service-x-credentials
  namespace: neotool-app
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-neotool
    kind: ClusterSecretStore
  target:
    name: service-x-credentials
    creationPolicy: Owner
  data:
    - secretKey: SERVICE_ID
      remoteRef:
        key: secret/services/service-x/credentials
        property: client_id
    - secretKey: SERVICE_SECRET
      remoteRef:
        key: secret/services/service-x/credentials
        property: client_secret
```

Then reference in your deployment:
```yaml
env:
  - name: SERVICE_ID
    valueFrom:
      secretKeyRef:
        name: service-x-credentials
        key: SERVICE_ID
  - name: SERVICE_SECRET
    valueFrom:
      secretKeyRef:
        name: service-x-credentials
        key: SERVICE_SECRET
```

#### Step 3: Recovery and Retrieval

If you need to retrieve credentials later (e.g., for a new environment or after rotation):

**From Vault CLI**:
```bash
# Get the full secret
vault kv get secret/services/service-x/credentials

# Get specific field
vault kv get -field=client_secret secret/services/service-x/credentials
```

**From Vault API**:
```bash
curl -H "X-Vault-Token: $VAULT_TOKEN" \
  $VAULT_ADDR/v1/secret/data/services/service-x/credentials | jq '.data.data'
```

**From Kubernetes Secret** (if using External Secrets):
```bash
# Decode the secret
kubectl get secret service-x-credentials -n neotool-app \
  -o jsonpath='{.data.SERVICE_SECRET}' | base64 -d
```

**Important**: If you lose the `client_secret` and it wasn't stored in Vault, you'll need to:
1. Register a new service principal (or rotate credentials if supported)
2. Update all environments with the new secret
3. This is why storing in Vault immediately after registration is critical!

#### Credential Rotation

When rotating credentials:

1. **Register new credentials**:
   ```bash
   ./neotool service register service-x --force
   ```

2. **Update Vault**:
   ```bash
   vault kv put secret/services/service-x/credentials \
     client_id="service-x" \
     client_secret="new-secret-here"
   ```

3. **Restart pods** to pick up new environment variables:
   ```bash
   kubectl rollout restart deployment/service-x -n neotool-app
   ```

#### Security Best Practices

- ✅ **Store immediately**: Save to Vault right after registration
- ✅ **Never commit secrets**: Use `.gitignore` for any local secret files
- ✅ **Separate environments**: Use different Vault paths per environment
- ✅ **Rotate regularly**: Every 90 days or per security policy
- ✅ **Least privilege**: Services only need read access to their own credentials
- ✅ **Monitor access**: Review Vault audit logs regularly

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

