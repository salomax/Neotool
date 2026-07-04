#!/usr/bin/env python3
"""
neotool-dev-agent — HTTP bridge do pipeline Slack → Agentes de desenvolvimento.

Recebe mensagens do Cloudflare Worker via POST /chat, classifica a intenção
via router.py (Gemini) e despacha para o agente correto (claude-dev, codex-dev
ou cursor-dev). Responde imediatamente (202) e posta o resultado no Slack
quando o agente termina.

POST /chat
  Body:    { "message": "...", "session": "thread-id", "resume": bool,
             "channel": "C...", "ts": "...", "slack_token": "xoxb-..." }
  Returns: { "status": "processing", "session": "thread-id" }
"""

import json
import logging
import os
import re
import subprocess
import sys
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# Router próprio
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import router as _router

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    stream=sys.stdout,
    force=True,
)
log = logging.getLogger("neotool-dev-agent")

# Carrega api-keys.env mesmo quando reiniciado manualmente
_API_KEYS_FILE = os.path.expanduser("~/.vault-keys/api-keys.env")
if os.path.exists(_API_KEYS_FILE):
    with open(_API_KEYS_FILE) as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith('#') and '=' in _line:
                _k, _, _v = _line.partition('=')
                os.environ.setdefault(_k.strip(), _v.strip())

PORT      = int(os.environ.get("NEOTOOL_AGENT_PORT", "3285"))
SECRET    = os.environ.get("NEOTOOL_AGENT_SECRET", "")
WORKSPACE = "/workspaces/invistus"
MAX_CHARS = 3800


# ── Helpers ───────────────────────────────────────────────────────────────────

def slack_update(token: str, channel: str, ts: str, text: str) -> None:
    payload = json.dumps({"channel": channel, "ts": ts, "text": text}).encode()
    req = urllib.request.Request(
        "https://slack.com/api/chat.update",
        data=payload,
        headers={"Content-Type": "application/json",
                 "Authorization": f"Bearer {token}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read())
            if not data.get("ok"):
                log.error("slack.update error: %s | chars: %d",
                          data.get("error"), len(text))
            else:
                log.info("slack.update OK | chars: %d", len(text))
    except Exception as e:
        log.error("slack.update exception: %s", e)


def run_usage_check(channel: str, ts: str, slack_token: str) -> None:
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
    slack_update(slack_token, channel, ts, text)


# ── HTTP Handler ──────────────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # silencia logs HTTP padrão (usamos log próprio)

    def send_json(self, code: int, data: dict):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"status": "ok", "agent": "neotool-dev-agent"})
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
        channel     = body.get("channel", "")
        ts          = body.get("ts", "")
        slack_token = body.get("slack_token", "")

        if not message:
            self.send_json(400, {"error": "message is required"})
            return
        if not channel or not ts or not slack_token:
            self.send_json(400, {"error": "channel, ts e slack_token são obrigatórios"})
            return

        # check-usage — respondido direto, sem passar pelo router
        if message.strip().lower() in {"check-usage", "/check-usage"}:
            t = threading.Thread(
                target=run_usage_check,
                args=(channel, ts, slack_token),
                daemon=True,
            )
            t.start()
            self.send_json(202, {"status": "processing", "session": "usage-check"})
            return

        # Despacha para o router em background
        def _run():
            try:
                output = _router.route(message, session, channel, ts, slack_token)
                log.info("router output: %r", (output or "")[:200])
                if output is None:
                    return
                text = output.strip() or "_(sem resposta)_"
                log.info("posting to slack: %d chars — %r", len(text), text[:120])
                if len(text) > MAX_CHARS:
                    text = text[:MAX_CHARS] + "\n_(truncado)_"
                slack_update(slack_token, channel, ts, text)
            except Exception as e:
                log.exception("router error: %s", e)
                slack_update(slack_token, channel, ts, f"❌ Erro interno: {e}")

        threading.Thread(target=_run, daemon=True).start()
        self.send_json(202, {"status": "processing", "session": session})


if __name__ == "__main__":
    log.info("neotool-dev-agent iniciando em http://127.0.0.1:%d", PORT)
    log.info("GOOGLE_API_KEY presente: %s", bool(os.environ.get("GOOGLE_API_KEY")))
    log.info("SECRET configurado: %s", bool(SECRET))
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    server.serve_forever()
