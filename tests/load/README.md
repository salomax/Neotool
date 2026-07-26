# Load Tests

## GH-420 Agent Rate Limiting

Functional k6 probes for OAuth2 token and MCP request rate limiting live under `tests/load/k6/`.

### Prerequisites

1. PostgreSQL reachable (local Docker Compose / DevPod is fine).
2. `security` on `:8080` and `mcp` on `:8086`.
3. Lowered limits for a fast probe (example):

```bash
export OAUTH2_TOKEN_RATE_LIMIT_LIMIT=5
export OAUTH2_TOKEN_RATE_LIMIT_WINDOW_MS=10000
export MCP_RATE_LIMIT_TOKEN_READ_LIMIT=5
export MCP_RATE_LIMIT_TOKEN_READ_WINDOW_MS=10000
export MCP_RATE_LIMIT_USER_LIMIT=1000
```

Inside the DevPod you can start both services with those defaults via:

```bash
bash tests/load/k6/start-gh420-services.sh
```

Mint MCP access tokens signed with the Vault JWT private key:

```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=...
pip install PyJWT cryptography   # once
MCP_READ_TOKEN=$(python3 tests/load/k6/mint-mcp-token.py)
MCP_OTHER_TOKEN=$(MCP_TOKEN_JTI=$(uuidgen) MCP_TOKEN_CLIENT_ID=k6-other python3 tests/load/k6/mint-mcp-token.py)
```

### OAuth2 `/oauth2/token`

```bash
k6 run tests/load/k6/oauth2-token-rate-limit.js \
  -e SECURITY_BASE_URL=http://localhost:8080 \
  -e TOKEN_LIMIT=5 \
  -e WINDOW_MS=10000
```

Covers: per-client 429 after limit, `Retry-After`, client isolation, `token_exchange`, window recovery.
`refresh_token` only rate-limits after the refresh token resolves to a client; invalid refresh tokens are expected to skip the limiter.

### MCP `/mcp/events`

```bash
k6 run tests/load/k6/mcp-events-rate-limit.js \
  -e MCP_BASE_URL=http://localhost:8086 \
  -e MCP_READ_TOKEN="$MCP_READ_TOKEN" \
  -e MCP_OTHER_TOKEN="$MCP_OTHER_TOKEN" \
  -e TOKEN_LIMIT=5 \
  -e WINDOW_MS=10000
```

Covers: JSON `429` (not a broken SSE body), `Retry-After`, optional token isolation, window recovery.

### Broader MCP load script

```bash
k6 run tests/load/k6/mcp-rate-limit.js \
  -e MCP_BASE_URL=http://localhost:8086 \
  -e MCP_READ_TOKEN=<read-scope-token> \
  -e MCP_WRITE_TOKEN=<write-scope-token> \
  -e MCP_FINANCIAL_ACCOUNT_ID=<financial-account-id>
```

What to verify:

- Well-behaved clients keep getting `200` responses with stable latency.
- The runaway client starts receiving `429` responses with `Retry-After`.
- Prometheus counters `mcp_rate_limit_exceeded_total` and `oauth2_token_rate_limit_exceeded_total` move as expected.
- No unrelated client is starved by the runaway traffic.
