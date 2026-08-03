---
id: feature.security.session-tokens.jwks-and-signing-keys
domain: security
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-02
evidence:
  backend:
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/JwksController.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/key/SecurityKeyManagerFactory.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/key/VaultKeyManager.kt
    - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/service/jwt/JwtTokenIssuer.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/key/FileKeyManager.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/key/KeyManagerFactory.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/jwt/JwtTokenValidator.kt
    - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/config/JwtConfig.kt
  graphql: []
  database: []
  tests:
    - service/kotlin/common/src/test/kotlin/io/github/salomax/neotool/common/security/key/FileKeyManagerTest.kt
    - service/kotlin/common/src/test/kotlin/io/github/salomax/neotool/common/security/key/JwksClientTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/JwtTokenIssuerTest.kt
    - service/kotlin/security/src/test/kotlin/io/github/salomax/neotool/security/test/service/unit/VaultClientTest.kt
    - contracts/api-collections/bruno/collections/supergraph/Security/Get JWKS.bru
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.security.session-tokens.access-and-refresh-tokens
schema: spec-metadata-v1
---

# JWKS And Signing Keys

## Behavior

- Tokens signed RS256 with private key; `kid` header set when `JwtConfig.keyId` present (`JwtTokenIssuer.signBuilder`).
- Validation uses public key selected by token `kid` (fallback config/`default`) via common `KeyManagerFactory` → `JwtTokenValidator`.
- Security module can prefer Vault keys when configured/available (`SecurityKeyManagerFactory`), else `FileKeyManager`.
- Public JWKS endpoint: `GET /.well-known/jwks.json` (`JwksController`):
  - Returns 404 if `jwt.jwksEnabled` is false
  - Currently injects **`FileKeyManager` only** (not VaultKeyManager)
  - Exposes single RSA JWK; `Cache-Control: public, max-age=300`
- Other services may fetch JWKS via common `JwksClient` (tested).

## Invariants

- Access tokens require valid signature + issuer.
- Missing public key for kid fails validation.
- JWKS disabled ⇒ endpoint not found.

## Permissions

Backend enforcement:

- JWKS is a public discovery endpoint (when enabled).
- Possession of private key is restricted to security issuer path.

UX hints:

- None (infra).

## Data And Contracts

- HTTP: `GET /.well-known/jwks.json` (RFC 7517-shaped response).
- Config: `JwtConfig` (issuer, keyId, jwksEnabled, key paths), `VaultConfig`.

## Impact Surface

- Backend: JwksController, SecurityKeyManagerFactory, VaultKeyManager, JwtTokenIssuer
- Common: FileKeyManager, KeyManagerFactory, JwtTokenValidator, JwksClient
- Tests: file key manager, JWKS client, issuer, Vault client unit test; Bruno Get JWKS
- Frontend / Mobile / Workers: none directly (validators elsewhere)

## Decisions

- RS256 only for production token crypto in this path.
- Vault optional with file fallback for signing material selection in security module.

## Known Gaps

- **Divergence:** JWKS HTTP endpoint always uses `FileKeyManager`, while issuer may sign with Vault keys when Vault is enabled — published JWKS may not match signing key in Vault mode.
- Limited integration test proving JWKS end-to-end against live Vault.
- No GraphQL surface (HTTP only).

## Legacy Notes

- None used as authority.
