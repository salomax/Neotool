#!/usr/bin/env python3
"""
router.py — Classifier + Stage Machine + Dispatcher
Substitui o Goose como router no pipeline Slack → Agentes.
"""
import json, logging, os, re, subprocess, sys, time, urllib.request
from pathlib import Path

log = logging.getLogger("router")

AGENTS_BIN = "/workspaces/invistus/.devcontainer/agents"
SESSION_DIR = "/tmp/bridge-sessions"
WORKSPACE   = "/workspaces/invistus"

# ── Stage machine ─────────────────────────────────────────────────────────────

STAGES = ["issue", "design", "backlog", "impl", "validate"]

def session_path(thread: str) -> Path:
    Path(SESSION_DIR).mkdir(parents=True, exist_ok=True)
    return Path(SESSION_DIR) / f"{thread}.json"

def load_state(thread: str) -> dict:
    p = session_path(thread)
    try:
        return json.loads(p.read_text()) if p.exists() else {}
    except Exception:
        return {}

def save_state(thread: str, updates: dict):
    p = session_path(thread)
    state = load_state(thread)
    state.update(updates)
    p.write_text(json.dumps(state, indent=2))

# ── Classifier ────────────────────────────────────────────────────────────────

CLASSIFY_PROMPT = """\
You are a routing classifier for a dev pipeline. Given a user message and thread state, return ONLY valid JSON.

Message: {message}
Thread state: {state}

Return JSON with these fields:
{{
  "intent": one of [check_usage, question, stage_issue, stage_design, stage_backlog, stage_impl, stage_pr, fast_track, advance],
  "agent": one of [claude, codex, cursor, null],
  "model": model name string or null,
  "issue_number": integer or null,
  "fast_track": boolean,
  "stages": list of stage names if fast_track else []
}}

Rules:
- "advance"= user says sim/ok/aprovado/yes/continua/next/proceed
- "check_usage" = user asks about token usage, agent status, check-usage
- "fast_track" = user explicitly asks to do multiple stages at once, or "de uma vez", "tudo junto", "em uma etapa"
- "stage_issue" = create or enrich a GitHub issue
- "stage_design" = solution design, technical analysis, viability, impact
- "stage_backlog" = generate task backlog
- "stage_impl" = implement code (default agent: codex)
- "stage_pr" = create pull request
- "stage_validate" = run tests, lint, typecheck
- "question" = simple question about codebase/behavior (default agent: claude)
- agent override: look for "@claude", "@codex", "@cursor", "use claude/codex/cursor", "pelo claude/codex/cursor"
- model override: look for "model X", "gpt-X", "sonnet-X", "-m X"
- if fast_track, list ALL stages mentioned in "stages" array
"""

def classify(message: str, state: dict) -> dict:
    """Rule-based shortcuts first, Gemini fallback."""
    msg = message.strip().lower()

    # Shortcut: check-usage
    if re.search(r"check-usage|/check-usage", msg):
        return {"intent": "check_usage", "agent": None, "model": None,
                "issue_number": None, "fast_track": False, "stages": []}

    # Shortcut: advance
    if re.match(r"^(sim|ok|aprovado|continua|continuar|próximo|proximo|avança|avanca|yes|proceed|next)[\s!.]*$", msg):
        return {"intent": "advance", "agent": None, "model": None,
                "issue_number": None, "fast_track": False, "stages": []}

    # Use Gemini Flash for everything else
    try:
        return _classify_gemini(message, state)
    except Exception as e:
        log.warning("Gemini classify failed: %s — fallback to rules", e)
        return _classify_rules(message, state)


def _classify_gemini(message: str, state: dict) -> dict:
    api_key = os.environ.get("GOOGLE_API_KEY", "")
    if not api_key:
        raise ValueError("GOOGLE_API_KEY not set")

    prompt = CLASSIFY_PROMPT.format(
        message=message,
        state=json.dumps({k: v for k, v in state.items()
                          if k in ("current_stage", "issue_number")})
    )
    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0, "maxOutputTokens": 256},
    }).encode()

    url = (f"https://generativelanguage.googleapis.com/v1beta/models/"
           f"gemini-2.5-flash-lite:generateContent?key={api_key}")
    req = urllib.request.Request(url, data=payload,
                                  headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=10)
    body = json.loads(resp.read())
    text = body["candidates"][0]["content"]["parts"][0]["text"].strip()

    # Extract JSON (may be wrapped in ```json ... ```)
    m = re.search(r"\{.*\}", text, re.DOTALL)
    if not m:
        raise ValueError(f"No JSON in response: {text[:200]}")
    return json.loads(m.group(0))


def _classify_rules(message: str, state: dict) -> dict:
    """Pure regex fallback."""
    msg = message.lower()
    result = {"intent": "question", "agent": None, "model": None,
              "issue_number": None, "fast_track": False, "stages": []}

    for agent, pat in [("claude", r"@claude|use\s+o?\s*claude|pelo\s+claude"),
                        ("codex",  r"@codex|use\s+o?\s*codex|pelo\s+codex"),
                        ("cursor", r"@cursor|use\s+o?\s*cursor|pelo\s+cursor")]:
        if re.search(pat, msg, re.I):
            result["agent"] = agent
            break

    m = re.search(r"model\s+([\w.\-]+)|gpt-([\w.]+)", msg)
    if m:
        result["model"] = m.group(0).replace("model ", "")

    m = re.search(r"(?:issue\s*#?|#)(\d+)", msg)
    if m:
        result["issue_number"] = int(m.group(1))

    if re.search(r"de\s+uma\s+(única|unica)\s+etapa|de\s+uma\s+vez|tudo\s+junto|ao\s+mesmo\s+tempo", msg):
        result["fast_track"] = True

    for intent, pat in [
        ("stage_pr",       r"\bpr\b|pull\s*request"),
        ("stage_impl",     r"\bimplementa|implement"),
        ("stage_validate", r"\btesta|valida|lint"),
        ("stage_backlog",  r"\bbacklog|tasks?"),
        ("stage_design",   r"\bdesign|solução|análise|viabilidade|impacto"),
        ("stage_issue",    r"\bissue|demanda|enriquec|detalh"),
    ]:
        if re.search(pat, msg, re.I):
            result["intent"] = intent
            break

    return result

# ── Default agents per intent ─────────────────────────────────────────────────

DEFAULT_AGENTS = {
    "question":       "claude",
    "stage_issue":    "claude",
    "stage_design":   "claude",
    "stage_backlog":  "claude",
    "stage_impl":     "codex",
    "stage_validate": "cursor",
    "stage_pr":       "cursor",
    "fast_track":     "claude",
}

SKILL_MAP = {
    "stage_issue":   "/neotool-open-issue",
    "stage_design":  "/neotool-solution-design",
    "stage_backlog": "/neotool-backlog-gen",
}

# ── Prompt builder ────────────────────────────────────────────────────────────

def build_prompt(classification: dict, original_message: str, state: dict) -> str:
    intent       = classification["intent"]
    issue_number = classification.get("issue_number") or state.get("issue_number")

    if intent == "fast_track":
        # Passa a mensagem original limpa — o agente decide o que fazer
        return original_message

    if intent in SKILL_MAP:
        skill = SKILL_MAP[intent]
        base  = f"{skill}\n\n{original_message}"
        if issue_number:
            base += f"\n\nIssue: #{issue_number}"
        return base

    if intent == "stage_impl":
        ref = f"issue #{issue_number}" if issue_number else "a tarefa descrita"
        return f"Implementar o backlog da {ref}.\n{original_message}"

    if intent == "stage_pr":
        ref = f"issue #{issue_number}" if issue_number else "o trabalho atual"
        return f"Criar pull request para {ref}.\n{original_message}"

    if intent == "stage_validate":
        return f"Validar implementação: rodar testes, lint e typecheck.\n{original_message}"

    if intent == "advance":
        stage_idx = state.get("current_stage", 0)
        next_stage = STAGES[stage_idx] if stage_idx < len(STAGES) else "impl"
        skill = SKILL_MAP.get(f"stage_{next_stage}", "")
        if skill and issue_number:
            return f"{skill}\n\nIssue: #{issue_number}"
        return original_message

    # question / fallback
    return original_message

# ── Dispatcher ────────────────────────────────────────────────────────────────

WRAPPER = {
    "claude": f"{AGENTS_BIN}/claude-dev",
    "codex":  f"{AGENTS_BIN}/codex-dev",
    "cursor": f"{AGENTS_BIN}/cursor-dev",
}

def dispatch(
    classification: dict,
    prompt: str,
    thread: str,
    channel: str,
    ts: str,
    slack_token: str,
) -> str:
    intent = classification["intent"]
    agent  = classification.get("agent") or DEFAULT_AGENTS.get(intent, "claude")
    model  = classification.get("model")

    log.info("dispatch intent=%s agent=%s model=%s", intent, agent, model)

    wrapper = WRAPPER.get(agent, WRAPPER["claude"])
    cmd = [wrapper]
    if model:
        if agent == "codex":
            cmd += ["-m", model]
        elif agent == "claude":
            cmd += ["--model", model]
        elif agent == "cursor":
            cmd += ["--model", model]
    cmd.append(prompt)

    env = {
        **os.environ,
        "GOOSE_SESSION_NAME": thread,
        "SLACK_CHANNEL":      channel,
        "SLACK_TS":           ts,
        "SLACK_TOKEN":        slack_token,
        "PATH":               f"{AGENTS_BIN}:{os.environ.get('PATH', '')}",
    }

    log.info("running: %s %s", wrapper, prompt[:80])
    t0 = time.time()
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True,
            timeout=600, cwd=WORKSPACE, env=env,
        )
        elapsed = time.time() - t0
        output = result.stdout.strip() or result.stderr.strip()
        log.info("agent done in %.1fs exit=%d chars=%d", elapsed, result.returncode, len(output))
        if result.returncode != 0:
            log.error("stderr: %s", result.stderr[:300])
        return output or f"_(sem resposta do agente — exit {result.returncode})_"
    except subprocess.TimeoutExpired:
        log.error("timeout after 600s")
        return "❌ Timeout — agente excedeu 10 minutos."
    except Exception as e:
        log.exception("dispatch error: %s", e)
        return f"❌ Erro interno: {e}"


def clean_slack_message(message: str) -> str:
    """Remove formatação Slack: <url|texto> → texto, <url> → url."""
    # <https://....|label> → label
    message = re.sub(r'<https?://[^|>]+\|([^>]+)>', r'\1', message)
    # <https://...> → url
    message = re.sub(r'<(https?://[^>]+)>', r'\1', message)
    # @mentions
    message = re.sub(r'<@[A-Z0-9]+>', '', message)
    return message.strip()


def extract_issue_from_message(message: str) -> int | None:
    """Extrai número de issue de #123, issue 123 ou URL do GitHub."""
    # URL GitHub: /issues/123
    m = re.search(r'github\.com/[^/]+/[^/]+/issues/(\d+)', message)
    if m:
        return int(m.group(1))
    # #123 ou issue #123 ou issue 123
    m = re.search(r'(?:issue\s*#?|#)(\d+)', message, re.I)
    if m:
        return int(m.group(1))
    return None


def route(
    message: str,
    thread: str,
    channel: str,
    ts: str,
    slack_token: str,
) -> str:
    """Entry point: classifica, constrói prompt, despacha, atualiza estado."""
    message_clean = clean_slack_message(message)
    state = load_state(thread)
    log.info("route thread=%s state=%s msg=%r", thread, state, message_clean[:100])

    classification = classify(message_clean, state)
    # Extrai issue de URL se o classifier não encontrou
    if not classification.get("issue_number"):
        classification["issue_number"] = extract_issue_from_message(message)
    log.info("classification: %s", json.dumps(classification))

    # check-usage: tratado separadamente
    if classification["intent"] == "check_usage":
        return None  # bridge já trata

    prompt = build_prompt(classification, message_clean, state)
    log.info("prompt built: %s", prompt[:120])

    output = dispatch(classification, prompt, thread, channel, ts, slack_token)

    # Atualiza estado da thread
    updates = {}
    if classification.get("issue_number"):
        updates["issue_number"] = classification["issue_number"]
    intent = classification["intent"]
    stage_map = {"stage_issue": 1, "stage_design": 2, "stage_backlog": 3,
                 "stage_impl": 4, "stage_validate": 5}
    if intent in stage_map:
        updates["current_stage"] = stage_map[intent]
    if updates:
        save_state(thread, updates)

    return output
