#!/usr/bin/env python3
"""
router.py — Classifier + Stage Machine + Dispatcher
Substitui o Goose como router no pipeline Slack → Agentes.
"""
import json, logging, os, re, subprocess, sys, threading, time, urllib.request
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
    for k, v in updates.items():
        if v is None:
            state.pop(k, None)   # None = remover chave
        else:
            state[k] = v
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
- "stage_issue" = create or enrich a GitHub issue. ALWAYS use when message says "abra uma issue", "crie uma issue", "open an issue", "nova issue", even if the message also describes implementation work
- "stage_design" = solution design, technical analysis, viability, impact
- "stage_backlog" = generate task backlog
- "stage_impl" = write/implement new code, create files, apply backlog tasks (default agent: codex). NEVER use for review/analysis requests.
- "stage_pr" = create pull request, commit changes (git commit), merge PR/branch. Default agent: cursor, model: auto
- "stage_validate" = run tests, lint, typecheck
- "question" = any question, code review, analysis, "revise", "analise", "verifique", "confira", "olha", "o que acha", "está certo" (default agent: claude)
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
        ("stage_pr",       r"\bpr\b|pull\s*request|\bcommit\b|\bmerge\b|git\s+commit|faz\s+(um\s+)?commit|commita"),  # commit/merge → cursor auto
        # "abra/crie/criar uma issue" tem prioridade sobre qualquer "implemente"
        ("stage_issue",    r"abr[ae]\s+(uma\s+)?issue|cri[ae]\s+(uma\s+)?issue|open\s+(an?\s+)?issue|nova\s+issue|\bissue|demanda|enriquec|detalh"),
        ("stage_impl",     r"\bimplementa|implement"),
        ("stage_validate", r"\btesta|valida|lint"),
        ("stage_backlog",  r"\bbacklog|tasks?"),
        ("stage_design",   r"\bdesign|solução|análise|viabilidade|impacto"),
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

# Modelo padrão por intent (override explícito do usuário tem prioridade)
DEFAULT_MODELS = {
    "stage_pr":       "auto",   # cursor auto para commits, PRs e merges
    "stage_validate": "auto",   # cursor auto para testes/lint
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

# ── Detecção de ambiguidade ──────────────────────────────────────────────────

_AMBIGUITY_PATTERNS = [
    ("stage_pr",       r"\bpr\b|pull\s*request|\bcommit\b|\bmerge\b|git\s+commit|faz\s+(um\s+)?commit|commita"),
    ("stage_issue",    r"abr[ae]\s+(uma\s+)?issue|cri[ae]\s+(uma\s+)?issue|nova\s+issue|\bissue|demanda"),
    ("stage_impl",     r"\bimplementa|implement"),
    ("stage_validate", r"\btesta|valida|lint"),
    ("stage_backlog",  r"\bbacklog|tasks?"),
    ("stage_design",   r"\bdesign|solução|análise|viabilidade|impacto"),
]

_INTENT_LABELS = {
    "stage_issue":    "Abrir issue",
    "stage_design":   "Design de solução",
    "stage_backlog":  "Gerar backlog",
    "stage_impl":     "Implementar código",
    "stage_validate": "Validar / testar",
    "stage_pr":       "Criar PR",
    "question":       "Responder pergunta",
    "fast_track":     "Executar tudo",
}

def detect_ambiguity(message: str) -> list:
    """Retorna lista de stage intents que fazem match na mensagem."""
    msg = message.lower()
    return [intent for intent, pat in _AMBIGUITY_PATTERNS
            if re.search(pat, msg, re.I)]


def _confirmation_message(classification: dict, ambiguous_intents: list) -> str:
    intent = classification["intent"]
    agent  = classification.get("agent") or DEFAULT_AGENTS.get(intent, "claude")
    others = [_INTENT_LABELS.get(i, i) for i in ambiguous_intents if i != intent]
    main   = _INTENT_LABELS.get(intent, intent)
    return (
        f"🤔 *Ambiguidade detectada* — vou executar:\n"
        f"• *{main}* via `{agent}`\n\n"
        f"_(também detectei no texto: {', '.join(others)})_\n\n"
        f"Responda `sim` para confirmar, ou descreva o que prefere fazer."
    )


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
    progress_log: "str | None" = None,
) -> str:
    intent = classification["intent"]
    agent  = classification.get("agent") or DEFAULT_AGENTS.get(intent, "claude")
    # Modelo: declaração explícita do usuário tem prioridade; senão usa default por intent
    model  = classification.get("model") or DEFAULT_MODELS.get(intent)

    TIMEOUT = {"claude": 1800, "codex": 1800, "cursor": 1800}.get(agent, 1800)
    log.info("dispatch intent=%s agent=%s model=%s timeout=%ds", intent, agent, model, TIMEOUT)

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
        "NEOTOOL_THREAD": thread,
        "SLACK_CHANNEL":      channel,
        "SLACK_TS":           ts,
        "SLACK_TOKEN":        slack_token,
        "PATH":               f"{AGENTS_BIN}:{os.environ.get('PATH', '')}",
    }

    log.info("running: %s", " ".join(str(x) for x in cmd[:2]) + " " + prompt[:80])
    t0 = time.time()
    timed_out = [False]

    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,   # stderr separado: não polui progress log nem output final
            text=True, bufsize=1, cwd=WORKSPACE, env=env,
        )

        stdout_lines = []
        stderr_lines = []
        log_fh = open(progress_log, "w", buffering=1) if progress_log else None

        def _read_stdout():
            try:
                for line in iter(proc.stdout.readline, ""):
                    stdout_lines.append(line)
                    if log_fh:
                        log_fh.write(line)
                        log_fh.flush()
            finally:
                proc.stdout.close()

        def _read_stderr():
            try:
                for line in iter(proc.stderr.readline, ""):
                    stderr_lines.append(line)
                    log.debug("[agent stderr] %s", line.rstrip())
            finally:
                proc.stderr.close()

        t_out = threading.Thread(target=_read_stdout, daemon=True)
        t_err = threading.Thread(target=_read_stderr, daemon=True)
        t_out.start(); t_err.start()

        try:
            proc.wait(timeout=TIMEOUT)
        except subprocess.TimeoutExpired:
            timed_out[0] = True
            proc.kill()
            proc.wait()
        finally:
            t_out.join(timeout=5)
            t_err.join(timeout=5)
            if log_fh:
                log_fh.close()

        elapsed = time.time() - t0
        if timed_out[0]:
            log.error("timeout after %ds", TIMEOUT)
            return f"❌ Timeout — agente excedeu {TIMEOUT // 60} minutos."

        output = "".join(stdout_lines).strip() or "".join(stderr_lines).strip()
        log.info("agent done in %.1fs exit=%d chars=%d", elapsed, proc.returncode, len(output))
        if proc.returncode != 0:
            log.error("agent stderr (300): %s", "".join(stderr_lines)[:300])
        return output or f"_(sem resposta do agente — exit {proc.returncode})_"

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


def _apply_stage_state(thread: str, classification: dict, state: dict) -> None:
    """Persiste issue_number e current_stage após dispatch."""
    updates = {}
    if classification.get("issue_number"):
        updates["issue_number"] = classification["issue_number"]
    intent = classification["intent"]
    stage_map = {"stage_issue": 1, "stage_design": 2, "stage_backlog": 3,
                 "stage_impl": 4, "stage_validate": 5}
    if intent in stage_map:
        updates["current_stage"] = stage_map[intent]
    elif intent == "advance":
        stage_idx = state.get("current_stage", 0)
        updates["current_stage"] = min(stage_idx + 1, len(STAGES))
    if updates:
        save_state(thread, updates)


def route(
    message: str,
    thread: str,
    channel: str,
    ts: str,
    slack_token: str,
    progress_log: "str | None" = None,
) -> str:
    """Entry point: classifica, confirma se ambíguo, despacha, atualiza estado."""
    message_clean = clean_slack_message(message)
    state = load_state(thread)
    log.info("route thread=%s state=%s msg=%r", thread, state, message_clean[:100])

    # ── Confirmação pendente ───────────────────────────────────────────────────
    pending = state.get("pending_confirmation")
    if pending:
        quick = classify(message_clean, {})
        if quick["intent"] == "advance":
            # Usuário confirmou → executa ação pendente
            save_state(thread, {"pending_confirmation": None})
            clf  = pending["classification"]
            prom = pending["prompt"]
            log.info("route: confirmação aceita → intent=%s agent=%s",
                     clf["intent"], clf.get("agent"))
            output = dispatch(clf, prom, thread, channel, ts, slack_token,
                              progress_log=progress_log)
            _apply_stage_state(thread, clf, state)
            return output
        else:
            # Usuário quer outra coisa → descarta pendência e reclassifica
            log.info("route: confirmação cancelada → reclassificando")
            save_state(thread, {"pending_confirmation": None})
            state.pop("pending_confirmation", None)
            # cai no fluxo normal abaixo

    # ── Fluxo normal ──────────────────────────────────────────────────────────
    classification = classify(message_clean, state)
    if not classification.get("issue_number"):
        classification["issue_number"] = extract_issue_from_message(message)
    log.info("classification: %s", json.dumps(classification))

    if classification["intent"] == "check_usage":
        return None

    prompt = build_prompt(classification, message_clean, state)
    log.info("prompt built: %s", prompt[:120])

    # ── Detecção de ambiguidade ────────────────────────────────────────────────
    # Só aplica a stage intents — question/advance/fast_track são unívocos
    if classification["intent"] not in ("question", "advance", "check_usage", "fast_track"):
        ambiguous = detect_ambiguity(message_clean)
        if len(ambiguous) >= 2:
            log.info("route: ambiguidade %s → pedindo confirmação", ambiguous)
            save_state(thread, {"pending_confirmation": {
                "classification": classification,
                "prompt": prompt,
            }})
            return _confirmation_message(classification, ambiguous)

    output = dispatch(classification, prompt, thread, channel, ts, slack_token,
                      progress_log=progress_log)
    _apply_stage_state(thread, classification, state)
    return output
