# RTK - Rust Token Killer (Codex / project agents)

Token-optimized CLI proxy for shell commands. Lives at the **repo root** so every agent
(Codex, Cursor, Claude Code, in or out of the DevPod) can resolve it — not under
`~/.codex/` (that path is DevPod container-local after `setup-agent-homes.sh` and is not
bind-mounted from the host).

## Rule

Always prefix shell commands with `rtk` when the binary is available:

```bash
rtk git status
rtk ./gradlew :banking:testUnit --console=plain
rtk npm run build
```

## Meta / escape hatch

```bash
rtk gain            # Token savings analytics
rtk gain --history  # Recent command savings history
rtk proxy <cmd>     # Raw command without filtering (when compressed output hides a line)
```

## Verification

```bash
rtk --version
rtk gain
which rtk
```

If `rtk` is missing on the host (agent outside DevPod), install it or skip the prefix —
do not fail the task solely because RTK is absent. Inside the DevPod image, `rtk` is
always present; hooks are wired by `.devcontainer/setup-rtk.sh`.
