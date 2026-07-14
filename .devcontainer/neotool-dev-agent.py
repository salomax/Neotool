#!/usr/bin/env python3
"""
neotool-dev-agent — Slack Socket Mode bridge para pipeline Slack → Agentes.

Thin Slack adapter: builds a Task from Bolt events and delegates to core.handle_task().
Health check HTTP em http://127.0.0.1:3285/health (compatível com watchdog e dev.sh).
"""

import json
import logging
import os
import subprocess
import sys
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from slack_bolt import App
from slack_bolt.adapter.socket_mode import SocketModeHandler

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from core import Task, handle_task
from slack_format import (
    _NEXT_STAGE,
    _STAGE_EMOJI,
    format_stage_result,
)
import state_store

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    stream=sys.stdout,
    force=True,
)
log = logging.getLogger("neotool-dev-agent")


def _load_env_file(path: str) -> None:
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

PORT            = int(os.environ.get("NEOTOOL_AGENT_PORT", "3285"))
SLACK_BOT_TOKEN = os.environ.get("SLACK_BOT_TOKEN", "")
SLACK_APP_TOKEN = os.environ.get("SLACK_APP_TOKEN", "")
WORKSPACE       = "/workspaces/invistus"
MAX_CHARS       = 3800
PROGRESS_INTERVAL = 30

_SESSIONS_FILE = f"/tmp/neotool-sessions-{_DEVPOD_N}.json"

# Event dedup: track recently-seen event IDs (client_msg_id or event_ts) to prevent re-running
# when Slack redelivers the same event
_SEEN_EVENTS = {}  # dedup_id -> timestamp
_SEEN_EVENTS_MAX_SIZE = 1000  # bounded set
_SEEN_EVENTS_LOCK = threading.Lock()


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


def format_slack_result(output: str | None, meta: dict) -> str:
    return format_stage_result(output, meta, max_chars=MAX_CHARS)


def format_slack_progress(meta: dict, elapsed_secs: int) -> str:
    intent = meta.get("intent", "")
    agent = meta.get("agent", "")
    model = meta.get("model", "")
    label = meta.get("label", "Trabalhando")
    emoji = _STAGE_EMOJI.get(intent, "⏳")
    mins, secs = divmod(elapsed_secs, 60)

    agent_str = f"`{agent}`" if agent else ""
    if model:
        agent_str += f" / `{model}`"

    parts = [f"{emoji} {label}"]
    if agent_str:
        parts.append(agent_str)
    parts.append(f"{mins:02d}:{secs:02d}")
    return "  ·  ".join(parts)


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


class _HealthHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

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


app = App(token=SLACK_BOT_TOKEN)


@app.event("message")
def handle_message(event, client, logger):
    if event.get("bot_id") or event.get("subtype"):
        return

    # Dedup: check if we've seen this event before
    dedup_id = event.get("client_msg_id") or event.get("event_ts", "")
    if dedup_id:
        with _SEEN_EVENTS_LOCK:
            if dedup_id in _SEEN_EVENTS:
                log.info("⚠️  Event dedup: ignoring redelivered event %s", dedup_id[:20])
                return
            # Add to seen set with timestamp, keep it bounded
            _SEEN_EVENTS[dedup_id] = time.time()
            if len(_SEEN_EVENTS) > _SEEN_EVENTS_MAX_SIZE:
                # Remove oldest entries to keep size bounded
                oldest_keys = sorted(_SEEN_EVENTS.items(), key=lambda x: x[1])[:100]
                for k, _ in oldest_keys:
                    del _SEEN_EVENTS[k]

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

    sessions = _sessions_load()
    resume = not is_new_task and session_key in sessions
    if is_new_task:
        sessions[session_key] = session_name
        _sessions_save(sessions)

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

    if text.lower() in {"check-usage", "/check-usage"}:
        threading.Thread(
            target=run_usage_check,
            args=(channel, working_ts),
            daemon=True,
        ).start()
        return

    task = Task(
        message=text,
        task_id=session_name,
        requester=f"slack:{channel}",
        adapter_env={
            "SLACK_CHANNEL": channel,
            "SLACK_TS": working_ts,
            "SLACK_TOKEN": SLACK_BOT_TOKEN,
        },
    )

    def _run():
        meta_snapshot: dict = {}
        progress_started = threading.Event()
        t_progress_start = [0.0]

        def _on_meta(data: dict) -> None:
            meta_snapshot.update(data)
            progress_started.set()
            t_progress_start[0] = time.time()

        def _on_progress(_status: str) -> None:
            if not progress_started.is_set():
                return
            elapsed = int(time.time() - t_progress_start[0])
            slack_update(channel, working_ts, format_slack_progress(meta_snapshot, elapsed))

        def _on_result(output: str | None, meta: dict) -> None:
            if output is None:
                return
            final = format_slack_result(output, meta)
            slack_update(channel, working_ts, final)
            log.info("■ session=%s done (%d chars)", session_name, len(final))

        # Use per-thread lock to serialize work for this session key
        # This prevents concurrent stage execution for the same Slack thread
        thread_lock = state_store.get_thread_lock(session_key)
        with thread_lock:
            try:
                handle_task(
                    task,
                    on_progress=_on_progress,
                    on_result=_on_result,
                    meta_callback=_on_meta,
                    progress_interval=PROGRESS_INTERVAL,
                    clean_message=True,
                )
            except Exception as e:
                log.exception("✗ handle_task error: %s", e)
                try:
                    slack_update(channel, working_ts, f"❌ Erro interno: {e}")
                except Exception:
                    pass

    threading.Thread(target=_run, daemon=True).start()


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
    handler.start()
