@RTK.md

# Agent instructions — Invistus

## DevPod agent homes (self-contained)

Inside the DevPod, Claude Code and Codex use **container-local** `~/.claude` and
`~/.codex` seeded from `.devcontainer/agent-home/` (`postCreate` →
`setup-agent-homes.sh`). Host `~/.claude` / `~/.codex` are **not** bind-mounted.

On a new machine (or after `--recreate` / `devpod delete`):

```bash
claude login
codex login
# optional: gh auth login
# OpenCode: opencode  →  /connect
```

`devpod stop` / `devpod up` without recreate keeps the container filesystem (OAuth
sessions survive). Tooling is baked in the image: `claude`, `codex`, `opencode`,
`rtk`, `semble`, `graphify`, `headroom` (proxy on `127.0.0.1:8787` via
`start-headroom.sh`).

## RTK (Rust Token Killer) — shell output discipline

See `@RTK.md` (repo root). Prefer RTK-prefixed shell commands when `rtk` is on PATH.

**Claude Code and Cursor** get Tier-1 hooks inside the DevPod (`postCreate` →
`.devcontainer/setup-rtk.sh`). **Codex / ChatGPT** use this project `AGENTS.md` + root
`RTK.md` (best-effort / model compliance) — not `~/.codex/RTK.md`, so agents outside the
sandbox can resolve the file.

```bash
rtk git status
rtk ./gradlew :banking:testUnit --console=plain
rtk proxy <command>   # raw escape hatch
rtk gain
```

## Semble — semantic code search

Before any exploratory `Grep`/`Read`/`find`/`rg`, run [Semble](https://github.com/MinishLab/semble)
first (~98% fewer tokens). Gate + exceptions:
`.agents/skills/neotool-conventions/references/token-discipline.md` §2. How:
`.agents/skills/neotool-conventions/references/semble-search.md`.

DevPod pins `semble[mcp]==0.5.2` and wires the MCP server on `postCreate`
(`.devcontainer/setup-semble.sh`). Cursor also has `.cursor/mcp.json`.

```bash
semble search "how is MCP write idempotency claimed" .
semble find-related path/to/File.kt 42 .
```

## Graphify — knowledge graph

[Graphify](https://github.com/Graphify-Labs/graphify) turns a folder (code + docs)
into a queryable local knowledge graph (`graphify-out/`). Use it for architecture
maps, cross-file paths, and god-node exploration — **not** as a replacement for
Semble when you only need to locate a symbol/snippet.

DevPod pins `graphifyy[mcp]==0.9.30` (CLI is still `graphify`) and wires Claude /
Codex / OpenCode skills on `postCreate` (`.devcontainer/setup-graphify.sh`).

```bash
/graphify .                                 # build / refresh the graph
graphify query "how is OAuth consent stored?"
graphify path "ConnectedAgentsService" "OAuth2ConsentRepository"
graphify explain "SecurityWiringFactory"
```

Output lives under `graphify-out/` (gitignored). AST code passes need no LLM.

## Headroom — token-saving proxy

DevPod runs `headroom proxy` on `http://127.0.0.1:8787` (`start-headroom.sh` on
postStart). Both agents are wired by default:

- **Codex** — `openai_base_url` in seeded `~/.codex/config.toml`
- **Claude Code** — `ANTHROPIC_BASE_URL=http://127.0.0.1:8787` via
  `devcontainer.json` `remoteEnv`, `.zshrc`, and the `claude-dev` Slack wrapper

Host `agents-proxy/` is optional legacy for Mac-side use only — not required
inside the DevPod.

```bash
curl -sf http://127.0.0.1:8787/readyz
echo "$ANTHROPIC_BASE_URL"   # expect http://127.0.0.1:8787
```

## Shared workflow

Token / validation rules: `.agents/skills/neotool-conventions/references/token-discipline.md`.
