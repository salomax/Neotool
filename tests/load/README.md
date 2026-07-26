# Load Tests

## MCP Rate Limiting

Run the issue `#420` validation script manually before release candidates or after changing MCP or OAuth2 rate-limit settings:

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
