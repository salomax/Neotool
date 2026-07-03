#!/usr/bin/env python3
"""
Goose HTTP Bridge — exposto via Cloudflare Tunnel para o Slack bot.

POST /chat
  Body:    { "message": "...", "session": "nome", "resume": bool,
             "channel": "C...", "ts": "...", "slack_token": "xoxb-..." }
  Returns: { "status": "processing", "session": "nome" }   (imediato — 202)

O Goose roda em background thread. Quando termina, chama diretamente
a API do Slack para atualizar a mensagem.
"""

import json
import logging
import os
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# Router próprio (substitui Goose)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import router as _router

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    stream=sys.stdout,
    force=True,
)
log = logging.getLogger("goose-bridge")

# Garante que api-keys.env está carregado mesmo quando o bridge é reiniciado manualmente
_API_KEYS_FILE = os.path.expanduser("~/.vault-keys/api-keys.env")
if os.path.exists(_API_KEYS_FILE):
    with open(_API_KEYS_FILE) as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith('#') and '=' in _line:
                _k, _, _v = _line.partition('=')
                os.environ.setdefault(_k.strip(), _v.strip())

PORT     = int(os.environ.get("GOOSE_BRIDGE_PORT", "3285"))
SECRET   = os.environ.get("GOOSE_SERVER_SECRET", "")
WORKSPACE = "/workspaces/invistus"
AGENTS_SKILLS = "/home/dev/.agents/skills"
MAX_CHARS = 3800

SYSTEM_PROMPT = """You are the AI development orchestrator for the Invistus project — a fintech monorepo in Kotlin/Micronaut + Next.js + React Native.

## Project context
- Workspace: /workspaces/invistus
- Services: security (8080), assets (8082), assistant (8083), comms (8084), financialdata (8091), banking (8092)
- Frontend: web/ (Next.js, port 3000), mobile/ (React Native)
- Infra: PostgreSQL, Kafka, MinIO, Vault via docker-compose
- CLI: neotool, gh (GitHub CLI with GH_TOKEN set)
- IMPORTANT: `gh` runs in a non-TTY environment — always use non-interactive flags:
  `gh issue create --title "..." --body "..." --label "..." --repo invistus/invistus`
  Never run `gh issue create` without --title and --body (it will hang waiting for input)

## Your role
You are a **router** in an assisted development pipeline. You delegate ALL work to agents.

**Golden rule: NEVER run `gh`, `git`, `cat`, `ls`, `tree`, or any CLI tool yourself.**
Use your `shell` tool ONLY to call the appropriate agent CLI.
All agents run from `/workspaces/invistus` with full permissions.

**If a shell command fails:** show the raw error message. NEVER say "I need permission" or "need approval".

**When an agent responds:** output its response VERBATIM after the 💬 marker. Do not summarize or paraphrase.

**The ONLY time you wait for the user:** between pipeline stages (issue → design → backlog → implement).

## Agent routing — NON-NEGOTIABLE RULES

### Rule 1 — Explicit override (highest priority)
If the user mentions an agent in ANY message at ANY stage, use it:
- "use Claude" / "usa o Claude" → `claude-dev "..." --dangerously-skip-permissions --output-format text`
- "use Codex" / "usa o Codex" → `codex-dev "..."`
- "use Cursor" / "usa o Cursor" → `cursor-dev "..." --dangerously-skip-permissions --output-format text`

### Rule 2 — Defaults by stage (when the user doesn't specify)
| Stage | Default agent | Command |
|---|---|---|
| Issue creation | Claude | `claude-dev "..." --dangerously-skip-permissions --output-format text` |
| Solution Design | Claude | `claude-dev "..." --dangerously-skip-permissions --output-format text` |
| Backlog | Claude | `claude-dev "..." --dangerously-skip-permissions --output-format text` |
| Implementation | Codex | `codex-dev "..."` |
| Bug fix / lint / typecheck / tests | Cursor | `cursor-dev "..." --dangerously-skip-permissions --output-format text` |

### Rule 3 — Always include the agent name in the 🤖 line
Examples:
- `🤖 _Gemini 2.5 Flash → Claude Code_`
- `🤖 _Gemini 2.5 Flash → Codex_`
- `🤖 _Gemini 2.5 Flash → Cursor CLI_`

## Pipeline stages

### Stage 0 — Classify the request
Determine what the user wants:

- **Simple question** about the codebase, environment, or behavior → delegate to Claude Code:
  ```bash
  cd /workspaces/invistus && claude-dev "$(message)" --dangerously-skip-permissions --output-format text
  ```
- **"ok" / "sim" / "aprovado" / "continua"** on an existing thread → advance to next stage
- **Implementation request for existing issue #N** → go to Stage 3 directly
- **New requirement or bug** → go to Stage 1

### Stage 0b — Fast track (single step)
If the user explicitly asks to combine or skip multiple pipeline stages in a single request (e.g. "create the issue and implement", "do everything at once", "skip approvals"):
- Pass the FULL user message verbatim to the specified agent (or default agent for the task type)
- Do NOT split into stages, do NOT wait for intermediate approvals
- The agent handles everything autonomously
- Return the agent output VERBATIM

### Stage 1 — Create or confirm GitHub issue
Delegate to Claude Code with the neotool-open-issue skill:
```bash
cd /workspaces/invistus && claude-dev "/neotool-open-issue

$(message)" --dangerously-skip-permissions --output-format text
```
After Claude creates the issue, post to Slack:
"✅ Issue criada: {GH link} — Quer que eu faça a análise técnica? Responda 'sim' para continuar."

### Stage 2 — Technical analysis (solution design)
Delegate to Claude Code with the neotool-solution-design skill:
```bash
cd /workspaces/invistus && claude-dev "/neotool-solution-design

Issue: #{number}" --dangerously-skip-permissions --output-format text
```
After Claude posts the analysis comment on GH, post to Slack:
"✅ Análise técnica postada: {GH comment link} — Quer que eu gere o backlog? Responda 'sim' para continuar."

### Stage 3 — Generate backlog
Delegate to Claude Code with the neotool-backlog-gen skill:
```bash
cd /workspaces/invistus && claude-dev "/neotool-backlog-gen

Issue: #{number}" --dangerously-skip-permissions --output-format text
```
After Claude posts the backlog comment on GH, post to Slack:
"✅ Backlog gerado: {GH comment link}

Escopo detectado: [backend | frontend | ambos]
Pronto para implementar? Responda 'sim' para continuar."

### Stage 4 — Implementation
Route by scope:
- **Backend only or backend-heavy** → Cursor CLI (simple/medium) or Claude Code (complex):
  ```bash
  cd /workspaces/invistus && cursor-dev "/neotool-implement\\n\\nIssue: #{number}" --dangerously-skip-permissions --output-format text
  # or for complex:
  cd /workspaces/invistus && claude-dev "/neotool-implement\\n\\nIssue: #{number}" --dangerously-skip-permissions --output-format text
  ```
- **Frontend (any complexity)**:
  ```bash
  cd /workspaces/invistus && claude-dev "/neotool-implement\\n\\nIssue: #{number}" --dangerously-skip-permissions --output-format text
  ```

After implementation, post to Slack:
"✅ Implementação concluída: {GH comment link}

Arquivos alterados: [list]
Branch: {branch}
Próximo passo: abrir o PR referenciando a issue."

## Infrastructure management
If services need to be restarted after changes, use shell:
- Restart web: `cd /workspaces/invistus/web && npm run dev &`
- Restart Kotlin service: `cd /workspaces/invistus && ./gradlew :{service}:run &`

## Rules
1. **Always respond in the same language the user writes in**
2. **One stage at a time** — never advance without explicit approval
3. **Always include the GH link** in your Slack response after each stage
4. **Delegate coding to agents** — do not write code yourself; use claude, agent, or codex via shell
5. **Be concise** — the user reads responses on mobile via Slack
6. **Skills path**: {agents_skills}

### Stage 5 — Validation (post-implementation, Cursor by default)
Automatically triggered after Stage 4 completes. Do NOT wait for the user to say "sim".

Delegate to Cursor CLI:
```bash
cd /workspaces/invistus && cursor-dev "/neotool-conventions

Run full validation for the changes in the current branch.

Detect scope from changed files (`git diff --name-only origin/main`):

**If web/ files changed:**
- pnpm run typecheck (in web/)
- pnpm run lint (in web/)
- pnpm run test (in web/)
- pnpm run test:coverage (in web/)

**If service files changed (e.g. banking/, security/, assets/):**
- ./gradlew :{service}:classes --no-daemon
- ./gradlew :{service}:test --no-daemon
- ./gradlew :{service}:ktlintCheck --no-daemon
- ./gradlew :{service}:koverXmlReport :{service}:koverVerify --no-daemon

**E2E (always):**
- Identify playwright specs impacted by changed files
- node_modules/.bin/playwright test <impacted specs>

**Security (always):**
- trivy fs --format table --severity CRITICAL,HIGH .

For each check: report PASS or FAIL with the actual error output.
Do NOT auto-fix failures — return a report only." --dangerously-skip-permissions --output-format text
```

After Cursor runs validations, post to Slack:

**If all passed:**
"✅ Validação completa — todos os checks passaram.
Branch pronta para PR: `{branch}`
🤖 _Gemini 2.5 Flash → Codex (impl) → Cursor CLI (validação)_"

**If any failed:**
"⚠️ Validação concluída com erros:

{report por check — PASS/FAIL + output do erro}

O que você quer fazer?
• Corrigir e revalidar: responda 'corrige'
• Ignorar e abrir PR mesmo assim: responda 'pr'
• Cancelar: responda 'cancelar'
🤖 _Gemini 2.5 Flash → Codex (impl) → Cursor CLI (validação)_"

## Output format (CRITICAL — NON-NEGOTIABLE)
After finishing all tool calls and reasoning, ALWAYS write your final reply starting with the exact marker `💬` on its own line.

**When you delegated to an agent (claude-dev, codex-dev, cursor-dev):**
- Copy the agent stdout EXACTLY and VERBATIM after 💬 — every character, every line
- Do NOT summarize, rephrase, translate, shorten, or add any commentary
- Do NOT add "O agente respondeu:" or any wrapper text
- The agent output IS the message to the user — pass it through unchanged

**When answering directly (no agent):**
- Write a concise answer in Portuguese

Example (resposta direta):
💬
O filtro "Todo o período" usa **3 anos** como padrão, definido em `useDashboardDateRange.ts` linha 47.
🤖 _Gemini 2.5 Flash_

Example (com delegação):
💬
✅ Issue criada: https://github.com/...
🤖 _Gemini 2.5 Flash → Claude Code_

Always end with `🤖 _<agentes usados>_`. Use `→` to chain agents in order.
Possible values: `Gemini 2.5 Flash`, `Claude Code`, `Cursor CLI`, `Codex`."""

SYSTEM_PROMPT = SYSTEM_PROMPT.replace("{agents_skills}", AGENTS_SKILLS)


# ── Helpers ───────────────────────────────────────────────────────────────────

def clean_goose_output(text: str) -> str:
    text = re.sub(r'[─━═╌┄┈]{3,}', '', text)
    text = re.sub(r'[ \t]*[▸▶►][ \t]*', '', text)
    text = re.sub(r'\x1b\[[0-9;]*[mK]', '', text)
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip()


def extract_answer(raw: str) -> str:
    marker = "💬"
    if marker in raw:
        text = raw.split(marker, 1)[1].strip()
        log.info("marcador 💬 encontrado — %d chars", len(text))
    else:
        log.warning("marcador 💬 não encontrado — retornando output limpo")
        text = clean_goose_output(raw) or "_(sem resposta)_"

    if len(text) > MAX_CHARS:
        text = text[:MAX_CHARS] + "\n\n_(resposta truncada)_"
        log.warning("resposta truncada para %d chars", MAX_CHARS)
    return text


def slack_update(token: str, channel: str, ts: str, text: str) -> None:
    payload = json.dumps({"channel": channel, "ts": ts, "text": text}).encode()
    req = urllib.request.Request(
        "https://slack.com/api/chat.update",
        data=payload,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read())
            if not data.get("ok"):
                log.error("slack.update error: %s | chars: %d", data.get("error"), len(text))
            else:
                log.info("slack.update OK | chars: %d", len(text))
    except Exception as e:
        log.error("slack.update exception: %s", e)


def run_usage_check(channel: str, ts: str, slack_token: str) -> None:
    """Roda check-usage.sh e posta resultado no Slack."""
    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-usage.sh")
    log.info(">>> /uso — rodando check-usage.sh")
    try:
        result = subprocess.run(
            ["bash", script],
            capture_output=True, text=True, timeout=60,
            cwd=WORKSPACE, env={**os.environ, "GOOSE_SESSION_NAME": "usage-check"},
        )
        raw = result.stdout.strip() or result.stderr.strip() or "_(sem dados)_"
        if len(raw) > MAX_CHARS:
            raw = raw[:MAX_CHARS] + "\n_(truncado)_"
        text = f"```\n{raw}\n```"
    except Exception as e:
        text = f"❌ Erro ao verificar uso: {e}"
    log.info("<<< /uso — %d chars", len(text))
    slack_update(slack_token, channel, ts, text)


def run_goose(session: str, message: str, resume: bool,
              channel: str, ts: str, slack_token: str) -> None:
    """Roda em background thread. Chama Goose e atualiza o Slack diretamente."""
    cmd = [
        "goose", "run",
        "--with-builtin", "developer",
        "--system", SYSTEM_PROMPT,
        "--quiet",
        "--name", session,
        "--text", message,
    ]
    if resume:
        cmd.append("--resume")

    log.info(">>> session=%s resume=%s msg=%r", session, resume, message[:120])
    t0 = time.time()

    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            cwd=WORKSPACE,
            env={**os.environ, "GOOSE_SESSION_NAME": session, "SLACK_CHANNEL": channel, "SLACK_TS": ts, "SLACK_TOKEN": slack_token},
        )
        log.info("goose PID=%d iniciado", proc.pid)

        stdout_lines: list[str] = []
        assert proc.stdout is not None
        for line in proc.stdout:
            line_s = line.rstrip()
            stdout_lines.append(line_s)
            # Loga linhas relevantes em tempo real (filtra lixo de UI)
            stripped = line_s.strip()
            if stripped and not stripped.startswith(("╭", "╰", "│", "─", "┌", "└")):
                log.debug("goose> %s", stripped[:200])

        proc.wait(timeout=600)
        elapsed = time.time() - t0

        if proc.returncode != 0:
            log.error("goose exit=%d (%.1fs)", proc.returncode, elapsed)
        else:
            log.info("goose OK — %.1fs, stdout=%d chars", elapsed, sum(len(l) for l in stdout_lines))

        raw = "\n".join(stdout_lines).strip()
        text = extract_answer(raw)

    except subprocess.TimeoutExpired:
        elapsed = time.time() - t0
        proc.kill()
        log.error("timeout após %.0fs", elapsed)
        text = "❌ Timeout — a tarefa excedeu 10 minutos."
    except Exception as e:
        log.exception("erro inesperado: %s", e)
        text = f"❌ Erro interno: {e}"

    log.info("<<< %d chars → slack", len(text))
    slack_update(slack_token, channel, ts, text)


# ── HTTP Handler ──────────────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def send_json(self, code: int, data: dict):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"status": "ok"})
        else:
            self.send_json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/chat":
            self.send_json(404, {"error": "not found"})
            return

        if SECRET:
            auth = self.headers.get("Authorization", "")
            if auth != f"Bearer {SECRET}":
                log.warning("401 unauthorized")
                self.send_json(401, {"error": "unauthorized"})
                return

        length = int(self.headers.get("Content-Length", 0))
        try:
            body = json.loads(self.rfile.read(length))
        except json.JSONDecodeError:
            self.send_json(400, {"error": "invalid JSON"})
            return

        message     = body.get("message", "").strip()
        session     = body.get("session", "default")
        resume      = bool(body.get("resume", False))
        channel     = body.get("channel", "")
        ts          = body.get("ts", "")
        slack_token = body.get("slack_token", "")

        if not message:
            self.send_json(400, {"error": "message is required"})
            return
        if not channel or not ts or not slack_token:
            self.send_json(400, {"error": "channel, ts e slack_token são obrigatórios"})
            return

        # Comandos especiais — resolvidos no bridge, sem custo de tokens do Goose
        _is_uso = message.strip().lower() in {"check-usage", "/check-usage"}
        if _is_uso:
            t = threading.Thread(
                target=run_usage_check,
                args=(channel, ts, slack_token),
                daemon=True,
            )
            t.start()
            self.send_json(202, {"status": "processing", "session": "usage-check"})
            return

        # Dispara router em background e responde imediatamente
        def _run_router():
            try:
                output = _router.route(message, session, channel, ts, slack_token)
                log.info("router output: %r", (output or "")[:200])
                if output is None:
                    return  # check_usage já tratado antes
                text = output.strip() or "_(sem resposta)_"
                log.info("posting to slack: %d chars — %r", len(text), text[:120])
                if len(text) > MAX_CHARS:
                    text = text[:MAX_CHARS] + "\n_(truncado)_"
                slack_update(slack_token, channel, ts, text)
            except Exception as e:
                log.exception("router error: %s", e)
                slack_update(slack_token, channel, ts, f"❌ Erro interno: {e}")

        t = threading.Thread(target=_run_router, daemon=True)
        t.start()

        self.send_json(202, {"status": "processing", "session": session})


if __name__ == "__main__":
    log.info("goose-bridge iniciando em http://127.0.0.1:%d (async)", PORT)
    log.info("GOOGLE_API_KEY presente: %s", bool(os.environ.get("GOOGLE_API_KEY")))
    log.info("SECRET configurado: %s", bool(SECRET))
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    server.serve_forever()
