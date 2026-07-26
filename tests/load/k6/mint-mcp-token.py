#!/usr/bin/env python3
"""Mint a short-lived MCP access token signed with the Vault JWT private key.

Usage (inside DevPod or any host that can reach Vault + has PyJWT/cryptography):

  export VAULT_ADDR=http://localhost:8200
  export VAULT_TOKEN=...
  python3 tests/load/k6/mint-mcp-token.py

Prints a single JWT to stdout.
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.request
import uuid


def load_private_pem() -> str:
    inline = os.environ.get("JWT_PRIVATE_KEY")
    if inline:
        return inline.replace("\\n", "\n")

    path = os.environ.get("JWT_PRIVATE_KEY_FILE")
    if path:
        with open(path, "r", encoding="utf-8") as fh:
            return fh.read()

    vault_addr = os.environ.get("VAULT_ADDR", "http://localhost:8200").rstrip("/")
    vault_token = os.environ.get("VAULT_TOKEN")
    if not vault_token:
        raise SystemExit("Set VAULT_TOKEN or JWT_PRIVATE_KEY / JWT_PRIVATE_KEY_FILE")

    secret_path = os.environ.get("VAULT_JWT_SECRET_PATH", "secret/data/jwt/keys/kid-1")
    req = urllib.request.Request(
        f"{vault_addr}/v1/{secret_path}",
        headers={"X-Vault-Token": vault_token},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        payload = json.load(resp)
    data = payload.get("data", {}).get("data", {})
    pem = data.get("private") or data.get("private_key")
    if not pem:
        raise SystemExit(f"Vault secret {secret_path} has no private key field")
    return pem


def mint_token(private_pem: str) -> str:
    try:
        import jwt
        from cryptography.hazmat.primitives import serialization
    except ImportError as exc:
        raise SystemExit(
            "PyJWT and cryptography are required: pip install PyJWT cryptography"
        ) from exc

    private_key = serialization.load_pem_private_key(private_pem.encode("utf-8"), password=None)
    now = int(time.time())
    issuer = os.environ.get("JWT_ISSUER", "http://localhost:8080")
    audience = os.environ.get("MCP_RESOURCE_AUDIENCE", "http://localhost:8086")
    scope = os.environ.get("MCP_TOKEN_SCOPE", "finance:transactions:read mcp:tools:call")
    user_id = os.environ.get("MCP_TOKEN_USER_ID", str(uuid.uuid4()))
    account_id = os.environ.get("MCP_TOKEN_ACCOUNT_ID", str(uuid.uuid4()))
    client_id = os.environ.get("MCP_TOKEN_CLIENT_ID", "k6-mcp-client")
    jti = os.environ.get("MCP_TOKEN_JTI", str(uuid.uuid4()))
    kid = os.environ.get("JWT_KEY_ID", "kid-1")

    claims = {
        "sub": user_id,
        "iss": issuer,
        "aud": [audience],
        "iat": now,
        "exp": now + int(os.environ.get("MCP_TOKEN_TTL_SECONDS", "900")),
        "jti": jti,
        "type": "access",
        "client_id": client_id,
        "current_account": account_id,
        "scope": scope,
    }
    return jwt.encode(claims, private_key, algorithm="RS256", headers={"kid": kid})


def main() -> None:
    token = mint_token(load_private_pem())
    sys.stdout.write(token)
    if sys.stdout.isatty():
        sys.stdout.write("\n")


if __name__ == "__main__":
    main()
