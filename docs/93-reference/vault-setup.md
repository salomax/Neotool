# Vault Setup Guide

This guide covers how to set up and use HashiCorp Vault for storing JWT signing keys in NeoTool.

## Table of Contents

1. [Overview](#overview)
2. [Local Development Setup](#local-development-setup)
3. [Creating Secrets Manually](#creating-secrets-manually)
   - [Option 1: Using NeoTool CLI (Recommended)](#option-1-using-neotool-cli-recommended-for-local-development)
   - [Option 2: Using Vault CLI](#option-2-using-vault-cli)
   - [Option 3: Using Vault UI](#option-3-using-vault-ui)
   - [Option 4: Using JSON File](#option-4-using-json-file)
   - [Option 5: Using Vault API](#option-5-using-vault-api)
4. [Creating Secrets with Terraform](#creating-secrets-with-terraform)
5. [Configuration](#configuration)
6. [Verification](#verification)
7. [Troubleshooting](#troubleshooting)
8. [Production Considerations](#production-considerations)

---

## Overview

NeoTool uses HashiCorp Vault to securely store JWT signing keys in production environments. The security module automatically retrieves keys from Vault when enabled.

### Key Concepts

- **Secret Path**: `secret/jwt/keys/{keyId}` (default: `secret/jwt/keys/kid-1`)
- **Secret Fields**: `private` (RSA private key) and `public` (RSA public key)
- **Engine**: KV v2 (Key-Value secrets engine version 2)
- **Key Format**: PEM format for both private and public keys

### Secret Structure

```
secret/jwt/keys/{keyId}/
  ├── private: "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
  ├── public: "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
  └── secret: "base64-encoded-secret" (optional, for HS256 fallback)
```

Example for key ID `kid-1`:
- **Path**: `secret/jwt/keys/kid-1`
- **Fields**: `private`, `public`, `secret` (optional)

---

## Local Development Setup

### Step 1: Start Vault

Using docker-compose with the `secrets` profile:

```bash
# Start Vault
docker-compose --profile secrets up -d vault

# Check status
docker-compose --profile secrets ps vault
```

Vault will be available at:
- **API**: http://localhost:8200
- **UI**: http://localhost:8200 (same port)
- **Root Token**: `myroot` (default, or set via `VAULT_ROOT_TOKEN` env var)

### Step 2: Authenticate

**Using CLI:**
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='myroot'  # or your token

# Verify connection
vault status
```

**Using UI:**
1. Open http://localhost:8200
2. Enter token: `myroot`
3. Click **Sign In**

### Step 3: Verify KV v2 Engine

The `secret` mount should already exist in dev mode. Verify:

```bash
# List secret engines
vault secrets list

# You should see 'secret/' with type 'kv'
```

If needed, enable KV v2:

```bash
vault secrets enable -version=2 -path=secret kv
```

---

## Creating Secrets Manually

### Option 1: Using NeoTool CLI (Recommended for Local Development)

The easiest way to create JWT key pairs in Vault for local development is using the NeoTool CLI:

```bash
# Create a new JWT key pair named "kid-1"
./neotool vault create-secret kid-1

# Create with custom key size (default is 4096 bits)
./neotool vault create-secret kid-2 --key-bits 2048

# Overwrite existing secret if needed
./neotool vault create-secret kid-1 --force

# Use custom Vault address
./neotool vault create-secret kid-1 --vault-address http://vault:8200

# Use custom Vault token
./neotool vault create-secret kid-1 --vault-token my-custom-token
```

**What it does:**
- Generates a 4096-bit RSA key pair (configurable)
- Automatically stores both private and public keys in Vault at `secret/jwt/keys/<key-name>`
- Works with local Vault CLI or Docker container
- Validates key names and checks for existing secrets
- Provides clear success messages with usage instructions

**Options:**
- `--key-bits <bits>`: RSA key size in bits (default: 4096)
- `--secret-path <path>`: Vault secret path prefix (default: `secret/jwt/keys`)
- `--vault-address <url>`: Vault server address (default: `http://localhost:8200`)
- `--vault-token <token>`: Vault authentication token (default: from `VAULT_TOKEN` env or `myroot`)
- `--force`: Overwrite existing secret if it exists

**Environment Variables:**
- `VAULT_ADDRESS`: Default Vault server address
- `VAULT_TOKEN`: Default Vault authentication token

**See also:** [NeoTool CLI Commands](./commands.md#vault-management)

---

### Option 2: Using Vault CLI

**1. Generate RSA Key Pair:**

```bash
# Generate private key (4096 bits recommended)
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:4096

# Extract public key
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem

# Verify keys
ls -la jwt-*.pem
```

**2. Create Secret in Vault:**

```bash
# For KV v2, use 'vault kv put'
vault kv put secret/jwt/keys/kid-1 \
  private="$(cat jwt-private.pem)" \
  public="$(cat jwt-public.pem)"
```

**3. Verify Secret:**

```bash
# Read the secret
vault kv get secret/jwt/keys/kid-1

# Get specific field
vault kv get -field=private secret/jwt/keys/kid-1
vault kv get -field=public secret/jwt/keys/kid-1
```

### Option 3: Using Vault UI

1. **Navigate to Secrets:**
   - Open http://localhost:8200
   - Go to **Secrets** → **secret**

2. **Create Secret:**
   - Click **Create secret**
   - **Path**: `jwt/keys/kid-1`
   - Click **Add field** and add:
     - Key: `private`, Value: (paste private key PEM)
     - Key: `public`, Value: (paste public key PEM)
   - Click **Save**

3. **Verify:**
   - Navigate to `secret/jwt/keys/kid-1`
   - Verify both `private` and `public` fields are present

### Option 4: Using JSON File

```bash
# Create JSON file with keys (escape newlines)
cat > jwt-keys.json <<EOF
{
  "private": "$(cat jwt-private.pem | sed ':a;N;$!ba;s/\n/\\n/g')",
  "public": "$(cat jwt-public.pem | sed ':a;N;$!ba;s/\n/\\n/g')"
}
EOF

# Write to Vault
vault kv put secret/jwt/keys/kid-1 @jwt-keys.json

# Clean up
rm jwt-keys.json
```

### Option 5: Using Vault API

```bash
# Get token
TOKEN="myroot"

# Create secret via API
curl \
  --header "X-Vault-Token: $TOKEN" \
  --request POST \
  --data @payload.json \
  http://localhost:8200/v1/secret/data/jwt/keys/kid-1
```

Where `payload.json` contains:
```json
{
  "data": {
    "private": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
    "public": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
  }
}
```

---

## Creating Secrets with Terraform

For production environments, keys should be provisioned as infrastructure using Terraform.

### Terraform Configuration

Create a Terraform file (e.g., `vault-jwt-keys.tf`):

```hcl
terraform {
  required_providers {
    vault = {
      source  = "hashicorp/vault"
      version = "~> 3.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

# Configure Vault provider
provider "vault" {
  address = var.vault_address
  token   = var.vault_token
}

# Generate RSA key pair
resource "tls_private_key" "jwt_signing_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

# Store keys in Vault
resource "vault_kv_secret_v2" "jwt_keys" {
  mount = "secret"
  name  = "jwt/keys/kid-1"

  data_json = jsonencode({
    private = tls_private_key.jwt_signing_key.private_key_pem
    public  = tls_private_key.jwt_signing_key.public_key_pem
  })
}

# Variables
variable "vault_address" {
  description = "Vault server address"
  type        = string
  default     = "http://localhost:8200"
}

variable "vault_token" {
  description = "Vault authentication token"
  type        = string
  sensitive   = true
}

# Outputs
output "key_id" {
  description = "JWT key ID"
  value       = "kid-1"
}

output "vault_path" {
  description = "Vault secret path"
  value       = vault_kv_secret_v2.jwt_keys.path
}
```

### Apply Terraform

```bash
# Initialize Terraform
terraform init

# Set Vault token
export TF_VAR_vault_token="myroot"

# Plan changes
terraform plan

# Apply changes
terraform apply
```

### Key Rotation

To rotate keys, create a new key ID:

```hcl
resource "tls_private_key" "jwt_signing_key_v2" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "vault_kv_secret_v2" "jwt_keys_v2" {
  mount = "secret"
  name  = "jwt/keys/kid-2"  # New key ID

  data_json = jsonencode({
    private = tls_private_key.jwt_signing_key_v2.private_key_pem
    public  = tls_private_key.jwt_signing_key_v2.public_key_pem
  })
}
```

Then update application configuration to use the new key ID.

---

## Configuration

After creating secrets in Vault, configure your services to use them.

### Environment Variables

```bash
# Enable Vault
export VAULT_ENABLED=true

# Vault connection
export VAULT_ADDRESS=http://vault:8200  # From container
# or
export VAULT_ADDRESS=http://localhost:8200  # From host

# Authentication
export VAULT_TOKEN=myroot  # Use proper token in production

# Secret path (default: secret/jwt/keys)
export VAULT_SECRET_PATH=secret/jwt/keys

# Key ID (must match key ID in Vault)
export JWT_KEY_ID=kid-1

# Optional: Timeouts
export VAULT_CONNECTION_TIMEOUT=5000
export VAULT_READ_TIMEOUT=5000
```

### Docker Compose

Add to your service configuration:

```yaml
services:
  security:
    environment:
      - VAULT_ENABLED=true
      - VAULT_ADDRESS=http://vault:8200
      - VAULT_TOKEN=${VAULT_TOKEN:-myroot}
      - VAULT_SECRET_PATH=secret/jwt/keys
      - JWT_KEY_ID=kid-1
    depends_on:
      vault:
        condition: service_healthy
```

### Application Configuration

In `application.yml`:

```yaml
vault:
  enabled: ${VAULT_ENABLED:false}
  address: ${VAULT_ADDRESS:http://localhost:8200}
  token: ${VAULT_TOKEN:}
  secret-path: ${VAULT_SECRET_PATH:secret/jwt/keys}
  engine-version: ${VAULT_ENGINE_VERSION:2}
  connection-timeout: ${VAULT_CONNECTION_TIMEOUT:5000}
  read-timeout: ${VAULT_READ_TIMEOUT:5000}

jwt:
  key-id: ${JWT_KEY_ID:kid-1}  # Must match key ID in Vault
```

---

## Verification

### Verify Secret Exists

```bash
# Using CLI
vault kv get secret/jwt/keys/kid-1

# Using API
curl \
  --header "X-Vault-Token: $VAULT_TOKEN" \
  http://localhost:8200/v1/secret/data/jwt/keys/kid-1
```

### Verify Application Can Access

Check application logs for:
- `Using Vault-based key manager` (success)
- `Vault is configured but not available` (connection issue)
- `Key not found in Vault` (secret doesn't exist)

### Test Key Retrieval

```bash
# Test private key retrieval
vault kv get -field=private secret/jwt/keys/kid-1 | openssl rsa -check -noout

# Test public key retrieval
vault kv get -field=public secret/jwt/keys/kid-1 | openssl rsa -pubin -text -noout
```

---

## Troubleshooting

### Issue: "Vault is configured but client is not available"

**Symptoms:** Application falls back to file-based keys

**Causes:**
- Vault not running
- Incorrect `VAULT_ADDRESS`
- Network connectivity issues

**Solutions:**
```bash
# Check Vault is running
docker-compose ps vault

# Check Vault status
vault status

# Verify address
curl http://localhost:8200/v1/sys/health
```

### Issue: "Key not found in Vault"

**Symptoms:** Application can't find keys

**Causes:**
- Secret doesn't exist
- Wrong path
- Wrong key ID

**Solutions:**
```bash
# List secrets
vault kv list secret/jwt/keys/

# Verify path
vault kv get secret/jwt/keys/kid-1

# Check key ID matches
echo $JWT_KEY_ID
```

### Issue: "Authentication failed"

**Symptoms:** Can't authenticate to Vault

**Causes:**
- Invalid token
- Token expired
- Missing token

**Solutions:**
```bash
# Verify token
vault token lookup

# Get new token (dev mode)
# Token is displayed when Vault starts: "Root Token: myroot"

# Check token in environment
echo $VAULT_TOKEN
```

### Issue: "Invalid key format"

**Symptoms:** Keys can't be parsed

**Causes:**
- Keys not in PEM format
- Keys corrupted
- Missing newlines

**Solutions:**
```bash
# Verify key format
vault kv get -field=private secret/jwt/keys/kid-1 | head -1
# Should start with: -----BEGIN PRIVATE KEY-----

# Recreate secret with proper format
vault kv put secret/jwt/keys/kid-1 \
  private="$(cat jwt-private.pem)" \
  public="$(cat jwt-public.pem)"
```

---

## Production Considerations

### Security Best Practices

✅ **DO:**
- Use proper Vault initialization (not dev mode)
- Store unseal keys securely
- Use Vault policies to restrict access
- Rotate Vault tokens regularly
- Enable Vault audit logging
- Use separate Vault paths per environment
- Use service accounts for authentication (not root tokens)

❌ **DON'T:**
- Use dev mode in production
- Use root tokens in production
- Commit Vault tokens to code
- Grant broad Vault access policies
- Store keys in both Vault and files

### Vault Initialization

In production, Vault must be properly initialized:

```bash
# Initialize Vault (first time only)
vault operator init -key-shares=5 -key-threshold=3

# Save unseal keys and root token securely
# Unseal Vault
vault operator unseal <unseal-key-1>
vault operator unseal <unseal-key-2>
vault operator unseal <unseal-key-3>
```

### Vault Policies

Create a policy for JWT key access:

```hcl
# jwt-keys-policy.hcl
path "secret/data/jwt/keys/*" {
  capabilities = ["read"]
}

path "secret/metadata/jwt/keys/*" {
  capabilities = ["list", "read"]
}
```

Apply policy:

```bash
vault policy write jwt-keys-policy jwt-keys-policy.hcl

# Create token with policy
vault token create -policy=jwt-keys-policy
```

### Key Rotation

1. Create new key in Vault with new key ID
2. Update application configuration to use new key ID
3. Old tokens remain valid until expiration
4. Remove old key after all tokens expire

### Monitoring

Monitor Vault health:
- Vault status endpoint: `/v1/sys/health`
- Application logs for key retrieval errors
- Vault audit logs for access patterns

---

## Related Documentation

- [Authentication Documentation](../../09-security/authentication.md) - JWT token issuing and validation
- [Security Features](../../03-features/security/README.md) - Complete security documentation
- [Google OAuth Setup](./google-oauth-setup.md) - OAuth setup (references Vault for JWT keys)

---

**Last Updated**: December 2024  
**Version**: 1.0  
**Status**: Production Ready

