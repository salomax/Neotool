#!/usr/bin/env bash
# slack-status.sh — atualiza a mensagem "Trabalhando" com status intermediário
# Uso: slack-status.sh "🔄 Chamando claude-dev..."
# Requer env: SLACK_TOKEN, SLACK_CHANNEL, SLACK_TS

[ -z "${SLACK_TOKEN:-}" ] || [ -z "${SLACK_CHANNEL:-}" ] || [ -z "${SLACK_TS:-}" ] && exit 0

python3 - "$1" << 'PY'
import sys, json, os, urllib.request

msg = sys.argv[1] if len(sys.argv) > 1 else "..."
data = json.dumps({
    "channel": os.environ["SLACK_CHANNEL"],
    "ts":      os.environ["SLACK_TS"],
    "text":    msg,
}).encode()
req = urllib.request.Request(
    "https://slack.com/api/chat.update",
    data=data,
    headers={
        "Content-Type":  "application/json",
        "Authorization": f"Bearer {os.environ['SLACK_TOKEN']}",
    },
)
try:
    urllib.request.urlopen(req, timeout=5)
except Exception:
    pass
PY
