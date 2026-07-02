#!/usr/bin/env bash
# check-usage.sh — resumo de uso dos agentes de desenvolvimento

LOG=/tmp/goose-bridge.log
TODAY=$(date '+%Y-%m-%d')

echo "📊 *Status dos Agentes — $(date '+%d/%m %H:%M')*"
echo ""

# Helper: roda CLI com PTY (para CLIs que rejeitam stdin puro)
run_with_pty() {
    local cmd="$1"
    local input="$2"
    local timeout="${3:-20}"
    python3 - "$cmd" "$input" "$timeout" << 'PY'
import sys, pty, os, select, time, subprocess, shutil

cmd_name = sys.argv[1]
input_text = sys.argv[2] + "\n"
timeout = float(sys.argv[3])

if not shutil.which(cmd_name):
    sys.exit(1)

master, slave = pty.openpty()
try:
    proc = subprocess.Popen(
        [cmd_name], stdin=slave, stdout=slave, stderr=slave,
        close_fds=True
    )
    os.close(slave)
    time.sleep(6)  # aguarda o CLI inicializar (Codex leva mais tempo com MCPs)
    os.write(master, input_text.encode())

    output = b""
    deadline = time.time() + timeout
    while time.time() < deadline:
        r, _, _ = select.select([master], [], [], 0.5)
        if r:
            try:
                chunk = os.read(master, 4096)
                output += chunk
            except OSError:
                break
    proc.terminate()
    proc.wait(timeout=3)
finally:
    try:
        os.close(master)
    except OSError:
        pass

# Limpa ANSI e lixo de terminal
import re
text = output.decode(errors='replace')
text = re.sub(r'\x1b\[[0-9;]*[mABCDEFGHJKLMPSTfihnrsu]', '', text)
text = re.sub(r'\r', '', text)
# Filtra linhas vazias e lixo de UI
lines = [l for l in text.splitlines() if l.strip() and not l.strip().startswith(('╭','╰','│','┌','└','─','►','❯','>', '$'))]
print('\n'.join(lines[:25]))
PY
}

# ── Claude Code ───────────────────────────────────────────────────────────────
echo "⚡ *Claude Code*"
claude_out=$(echo "/usage" | timeout 15 claude --dangerously-skip-permissions 2>/dev/null | grep -v "^$" | head -20 || true)
if [ -n "$claude_out" ]; then
    echo "$claude_out"
else
    echo "• _(execute \`/usage\` numa sessão \`claude\` para ver consumo)_"
fi

echo ""

# ── Codex ─────────────────────────────────────────────────────────────────────
echo "🔷 *Codex*"
codex_out=$(python3 /workspaces/invistus/.devcontainer/codex-usage.py 2>/dev/null || true)
if [ -n "$codex_out" ]; then
    echo "$codex_out"
else
    echo "• _(execute \`/usage\` numa sessão \`codex\` para ver consumo)_"
fi

echo ""

# ── Cursor CLI ────────────────────────────────────────────────────────────────
echo "🖱️ *Cursor CLI*"
cursor_ver=$(cursor --version 2>/dev/null | head -1 || echo "")
[ -n "$cursor_ver" ] && echo "Versão: $cursor_ver"
echo "Limites: https://cursor.com/dashboard/usage"

echo ""

# ── Gemini 2.5 Flash (via bridge logs) ───────────────────────────────────────
echo "🧠 *Gemini 2.5 Flash (Goose)*"
if [ -f "$LOG" ]; then
    total=$(grep -c "goose OK" "$LOG" 2>/dev/null || echo 0)
    total_today=$(grep "$TODAY" "$LOG" 2>/dev/null | grep -c "goose OK" || echo 0)
    last_ok=$(grep "goose OK" "$LOG" 2>/dev/null | tail -1 | grep -oP '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}' | head -1)
    echo "• Sessões hoje: $total_today  |  Total (log): $total"
    [ -n "$last_ok" ] && echo "• Última: $last_ok"
    errors=$(grep -c "goose ERRO\|Traceback" "$LOG" 2>/dev/null || echo 0)
    [ "$errors" -gt 0 ] && echo "• ⚠️ Erros no log: $errors"
else
    echo "• _(log não encontrado)_"
fi
