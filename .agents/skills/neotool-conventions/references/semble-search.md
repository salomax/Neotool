# Semble — semantic code search for agents

[Semble](https://github.com/MinishLab/semble) is the preferred way to find code during
diagnosis and implementation. It returns only the relevant snippets (~98% fewer tokens than
grep+read), runs on CPU, and needs **no API key**.

DevPod installs a pinned `semble[mcp]==0.5.2` (via `uv`) and wires the MCP server for Claude
Code, Cursor, and Codex on `postCreate` (`.devcontainer/setup-semble.sh`). Project Cursor
config: `.cursor/mcp.json`.

## When to use

Prefer Semble **before** broad `Grep`/`Read` sweeps when you need to answer:

- “Where is X implemented?”
- “How does auth / idempotency / GraphQL wiring work?”
- “Which files own this failing test / error code?”

Fall back to targeted Grep/Read when you already know the exact path or symbol.

## MCP (preferred)

Call the `semble` MCP tools (search / find-related) against the workspace root. Indexes are
built and cached on first use and invalidated when files change.

## CLI (scripts / no MCP session)

```bash
semble search "IdempotencyService claim then store" /workspaces/invistus --top-k 8
semble search "MCP write provenance header" . --content code
semble find-related service/kotlin/banking/src/main/kotlin/.../IdempotencyService.kt 40 .
semble savings
```

If `semble` is not on `PATH`, use the pinned uvx form:

```bash
uvx --from "semble[mcp]==0.5.2" semble search "query" .
```

## Ignore rules

Semble respects `.gitignore` and optional `.sembleignore`. It already skips `node_modules/`,
`build/`, `.gradle/`, etc. Add a `.sembleignore` only for Invistus-specific noise.

## Relationship to RTK / token-discipline

- **RTK** bounds *shell/CLI* output volume.
- **Semble** bounds *code-discovery* tokens (replaces exploratory grep+read).
- Both apply under [token-discipline.md](token-discipline.md).
