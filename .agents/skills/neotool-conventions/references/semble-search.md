# Semble — semantic code search for agents

[Semble](https://github.com/MinishLab/semble) is the semantic code-search tool for Invistus
agents. It returns only the relevant snippets (~98% fewer tokens than grep+read), runs on CPU,
and needs **no API key**.

DevPod installs a pinned `semble[mcp]==0.5.2` (via `uv`) and wires the MCP server for Claude
Code, Cursor, and Codex on `postCreate` (`.devcontainer/setup-semble.sh`). Project Cursor
config: `.cursor/mcp.json`.

## When to use

The **mandatory** code-discovery gate (when Semble is required vs. when direct Grep/Read is
allowed) is defined in [token-discipline.md](token-discipline.md) §2. This file covers **how**
to call Semble — do not re-author the gate here.

**`semble search`** — locate unknown implementations:

- “Where is X implemented?”
- “How does auth / idempotency / GraphQL wiring work?”
- “Which files own this failing test / error code?”

**`semble find-related`** — after you have a known `file:line` (from search, a diff, or a
stack frame), expand its neighborhood instead of manually opening siblings/callers. Example:
you found `IdempotencyService.kt` via search or a stack trace; the next question is “what else
touches this?” (callers, GraphQL wiring, nearby tests) — run `find-related` on that path
rather than browsing adjacent files by hand:

```bash
semble find-related service/kotlin/banking/src/main/kotlin/.../IdempotencyService.kt 40 .
```

Do not use `find-related` when you already have the exact path you need to edit (read-before-edit
is an exception in token-discipline §2).

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
- **Semble** bounds *code-discovery* tokens (mandatory gate in token-discipline §2).
- Both apply under [token-discipline.md](token-discipline.md).
