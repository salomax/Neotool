#!/usr/bin/env python3
"""
neotool-dev-agent — Slack Socket Mode bridge para pipeline Slack → Agentes.

Recebe mensagens via Socket Mode (WebSocket outbound, sem inbound), posta
"⏳ Trabalhando...", classifica via router.py e despacha para o agente certo.

Health check HTTP em http://127.0.0.1:3285/health (compatível com watchdog e dev.sh).
"""

import json
import logging
import os
import re
import subprocess
import sys
import threading
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from slack_bolt import App
from slack_bolt.adapter.socket_mode import SocketModeHandler

# Router próprio (mesmo diretório)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import router as _router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    stream=sys.stdout,
    force=True,
)
log = logging.getLogger("neotool-dev-agent")


# ── Env loading ───────────────────────────────────────────────────────────────

def _load_env_file(path: str) -> None:
    """Carrega key=value de um env file para os.environ (setdefault — não sobrescreve)."""
    try:
        with open(path) as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    k, _, v = line.partition('=')
                    os.environ.setdefault(k.strip(), v.strip())
    except FileNotFoundError:
        pass


_load_env_file(os.path.expanduser("~/.vault-keys/api-keys.env"))

_DEVPOD_N = os.environ.get("NEOTOOL_DEVPOD_N", "1")
_DEVPOD_ENV = os.path.expanduser(f"~/.vault-keys/devpod-{_DEVPOD_N}.env")
_load_env_file(_DEVPOD_ENV)


# ── Config ────────────────────────────────────────────────────────────────────

PORT            = int(os.environ.get("NEOTOOL_AGENT_PORT", "3285"))
SLACK_BOT_TOKEN = os.environ.get("SLACK_BOT_TOKEN", "")
SLACK_APP_TOKEN = os.environ.get("SLACK_APP_TOKEN", "")
WORKSPACE       = "/workspaces/invistus"
MAX_CHARS       = 3800
PROGRESS_INTERVAL = 30  # segundos entre updates de progresso

# ── ANSI strip ────────────────────────────────────────────────────────────────

_ANSI_RE = re.compile(r'\x1b(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')

def _strip_ansi(text: str) -> str:
    return _ANSI_RE.sub('', text)


# ── Status estruturado ────────────────────────────────────────────────────────

_STAGE_EMOJI = {
    "stage_issue":    "📋",
    "stage_design":   "🔍",
    "stage_backlog":  "📝",
    "stage_impl":     "⚙️",
    "stage_review":   "🧐",
    "stage_validate": "🧪",
    "stage_pr":       "🚀",
    "question":       "💬",
}

_NEXT_STAGE = {
    "stage_issue":    "design",
    "stage_design":   "backlog",
    "stage_backlog":  "impl",
    "stage_impl":     "review",
    "stage_review":   "validate",
    "stage_validate": "pr",
}


# ── Session persistence ───────────────────────────────────────────────────────

_SESSIONS_FILE = f"/tmp/neotool-sessions-{_DEVPOD_N}.json"


def _sessions_load() -> dict:
    try:
        with open(_SESSIONS_FILE) as f:
            return json.load(f)
    except Exception:
        return {}


def _sessions_save(data: dict) -> None:
    try:
        with open(_SESSIONS_FILE, "w") as f:
            json.dump(data, f)
    except Exception:
        pass


# ── Slack helpers ─────────────────────────────────────────────────────────────

def slack_update(channel: str, ts: str, text: str) -> None:
    payload = json.dumps({"channel": channel, "ts": ts, "text": text}).encode()
    req = urllib.request.Request(
        "https://slack.com/api/chat.update",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {SLACK_BOT_TOKEN}",
        },
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


# ── check-usage ───────────────────────────────────────────────────────────────

def run_usage_check(channel: str, ts: str) -> None:
    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-usage.sh")
    log.info("check-usage — rodando %s", script)
    try:
        result = subprocess.run(
            ["bash", script],
            capture_output=True, text=True, timeout=60,
            cwd=WORKSPACE,
        )
        raw = result.stdout.strip() or result.stderr.strip() or "_(sem dados)_"
        if len(raw) > MAX_CHARS:
            raw = raw[:MAX_CHARS] + "\n_(truncado)_"
        text = f"```\n{raw}\n```"
    except Exception as e:
        text = f"❌ Erro ao verificar uso: {e}"
    slack_update(channel, ts, text)


# ── Health HTTP server (porta 3285) ───────────────────────────────────────────

class _HealthHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # silencia access log HTTP

    def do_GET(self):
        if self.path == "/health":
            body = json.dumps({
                "status": "ok",
                "agent":  "neotool-dev-agent",
                "mode":   "socket",
                "devpod": _DEVPOD_N,
            }).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()


def _start_health_server() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", PORT), _HealthHandler)
    t = threading.Thread(target=server.serve_forever, daemon=True)
    t.start()
    log.info("Health server em http://127.0.0.1:%d/health", PORT)


# ── Bolt App ──────────────────────────────────────────────────────────────────

app = App(token=SLACK_BOT_TOKEN)


@app.event("message")
def handle_message(event, client, logger):
    """Processa mensagens recebidas via Socket Mode."""
    # Ignora mensagens de bots e eventos de subtipo (edit, delete, etc.)
    if event.get("bot_id") or event.get("subtype"):
        return

    channel   = event.get("channel", "")
    ts        = event.get("ts", "")
    thread_ts = event.get("thread_ts")
    text      = (event.get("text") or "").strip()

    if not text or not channel or not ts:
        return

    is_new_task  = not thread_ts or thread_ts == ts
    parent_ts    = ts if is_new_task else thread_ts
    session_key  = f"{channel}:{parent_ts}"
    session_name = f"slack-{channel}-{parent_ts}".replace(".", "-")

    log.info("→ msg channel=%s session=%s new=%s text=%r",
             channel, session_name, is_new_task, text[:80])

    # Resolve resume (sessão já existe na thread)
    sessions = _sessions_load()
    resume = not is_new_task and session_key in sessions
    if is_new_task:
        sessions[session_key] = session_name
        _sessions_save(sessions)

    # Posta "⏳ Trabalhando..." e obtém o ts para atualizar depois
    try:
        resp = client.chat_postMessage(
            channel=channel,
            text="⏳ Trabalhando...",
            thread_ts=parent_ts,
        )
        working_ts = resp["ts"]
        log.info("  workingTs=%s resume=%s", working_ts, resume)
    except Exception as e:
        log.error("Falha ao postar ⏳: %s", e)
        return

    # check-usage — handler especial, não passa pelo router
    if text.lower() in {"check-usage", "/check-usage"}:
        threading.Thread(
            target=run_usage_check,
            args=(channel, working_ts),
            daemon=True,
        ).start()
        return

    # Despacha para router em background
    def _run():
        # Metadata preenchido quando o router inicia o dispatch (após classificação)
        meta = {}
        dispatch_started = threading.Event()

        def _meta_callback(data: dict) -> None:
            meta.update(data)
            dispatch_started.set()
            log.info("  dispatch: intent=%s agent=%s model=%s",
                     data.get("intent"), data.get("agent"), data.get("model"))

        stop_progress = threading.Event()

        def _progress_updater():
            # Espera o dispatch iniciar (classificador pode levar alguns segundos)
            if not dispatch_started.wait(timeout=120):
                return
            t_start = __import__("time").time()
            while not stop_progress.wait(PROGRESS_INTERVAL):
                elapsed = int(__import__("time").time() - t_start)
                mins, secs = elapsed // 60, elapsed % 60

                intent = meta.get("intent", "")
                agent  = meta.get("agent", "")
                model  = meta.get("model", "")
                label  = meta.get("label", "Trabalhando")
                emoji  = _STAGE_EMOJI.get(intent, "⏳")

                agent_str = f"`{agent}`" if agent else ""
                if model:
                    agent_str += f" / `{model}`"

                parts = [f"{emoji} {label}"]
                if agent_str:
                    parts.append(agent_str)
                parts.append(f"{mins:02d}:{secs:02d}")

                status = "  ·  ".join(parts)
                slack_update(channel, working_ts, status)
                log.info("  progress: %s", status)

        updater = threading.Thread(target=_progress_updater, daemon=True)
        updater.start()

        log.info("▶ session=%s msg=%r", session_name, text[:120])
        try:
            output = _router.route(text, session_name, channel, working_ts, SLACK_BOT_TOKEN,
                                   meta_callback=_meta_callback)
            log.info("◀ router output: %r", (output or "")[:200])
            if output is None:
                return

            result = output.strip() or "_(sem resposta)_"
            if len(result) > MAX_CHARS:
                result = result[:MAX_CHARS] + "\n_(truncado)_"

            # ── Mensagem final: header + resultado + próximo stage ─────────────
            intent  = meta.get("intent", "")
            agent   = meta.get("agent", "")
            model   = meta.get("model", "")
            issue   = meta.get("issue_number")
            label   = meta.get("label", "")
            branch  = meta.get("branch")

            header_parts = []
            if intent.startswith("stage_"):
                stage_short = intent.replace("stage_", "")
                emoji = _STAGE_EMOJI.get(intent, "✅")
                header_parts.append(f"{emoji} *{stage_short} concluído*")
            if issue:
                header_parts.append(f"issue #{issue}")
            if branch:
                header_parts.append(f"`{branch}`")
            elif agent:
                a = f"`{agent}`"
                if model:
                    a += f" / `{model}`"
                header_parts.append(a)
            if branch and agent:
                a = f"`{agent}`"
                if model:
                    a += f" / `{model}`"
                header_parts.append(a)

            header = "  ·  ".join(header_parts)

            next_s = _NEXT_STAGE.get(intent)
            footer = f"\n\n→ próximo: `{next_s}`  —  `sim` para continuar" if next_s else ""

            final = f"{header}\n\n{result}{footer}" if header else result
            slack_update(channel, working_ts, final)
            log.info("■ session=%s done (%d chars)", session_name, len(final))

        except Exception as e:
            log.exception("✗ router error: %s", e)
            try:
                slack_update(channel, working_ts, f"❌ Erro interno: {e}")
            except Exception:
                pass
        finally:
            stop_progress.set()

    threading.Thread(target=_run, daemon=True).start()


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    missing = []
    if not SLACK_BOT_TOKEN:
        missing.append(f"SLACK_BOT_TOKEN (em ~/.vault-keys/devpod-{_DEVPOD_N}.env)")
    if not SLACK_APP_TOKEN:
        missing.append(f"SLACK_APP_TOKEN (em ~/.vault-keys/devpod-{_DEVPOD_N}.env)")
    if missing:
        for m in missing:
            log.error("Variável não configurada: %s", m)
        sys.exit(1)

    if not SLACK_APP_TOKEN.startswith("xapp-"):
        log.error(
            "SLACK_APP_TOKEN deve começar com 'xapp-' (App-Level Token). "
            "Verifique ~/.vault-keys/devpod-%s.env", _DEVPOD_N
        )
        sys.exit(1)

    log.info("neotool-dev-agent iniciando (DevPod %s, Socket Mode)", _DEVPOD_N)
    log.info("  OPENAI_API_KEY:    %s", "✓" if os.environ.get("OPENAI_API_KEY") else "✗ AUSENTE")
    log.info("  GOOGLE_API_KEY:    %s", "✓" if os.environ.get("GOOGLE_API_KEY") else "✗ AUSENTE")
    log.info("  GH_TOKEN:          %s", "✓" if os.environ.get("GH_TOKEN") else "✗ AUSENTE")
    log.info("  devpod env:        %s", _DEVPOD_ENV)

    _start_health_server()

    handler = SocketModeHandler(app, SLACK_APP_TOKEN)
    handler.start()  # blocking
