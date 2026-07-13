#!/usr/bin/env python3
"""
router.py — Classifier + Stage Machine + Dispatcher
Substitui o Goose como router no pipeline Slack → Agentes.
"""
from __future__ import annotations

import json, logging, os, re, subprocess, urllib.request
from pathlib import Path
from typing import Mapping

from agents.backend import AgentBackend, BackendResult, get_default_backend
from agents.routing import (
    format_routing_summary,
    get_orchestrator_config,
    get_routing_table,
    normalize_agent_override,
    resolve_agent_default_model,
    resolve_route,
)
from checkpoint import (
    Checkpoint,
    current_stage_from_checkpoint,
    fetch_last_checkpoint,
    write_checkpoint,
)
from circuit_breaker import (
    PauseDecision,
    apply_post_stage_circuit_breaker,
    should_pause_for_plan,
)
from planner import Plan, format_plan_message, generate_plan, stage_to_intent
from thread_log import (
    LoggingBackend,
    log_stage_for_intent,
    write_thread_summary,
)
from verifier import (
    IMPL_RETRY_LIMIT,
    VerifierResult,
    build_impl_retry_prompt,
    run_verifier,
)

log = logging.getLogger("router")
SESSION_DIR = "/tmp/bridge-sessions"
WORKSPACE   = "/workspaces/invistus"

# ── Stage machine ─────────────────────────────────────────────────────────────

STAGES = ["issue", "design", "backlog", "impl", "review", "validate", "pr"]

INTENT_TO_STAGE = {
    "stage_issue": "issue",
    "stage_design": "design",
    "stage_backlog": "backlog",
    "stage_impl": "impl",
    "stage_review": "review",
    "stage_validate": "validate",
    "stage_pr": "pr",
}

STAGE_TO_INTENT = {v: k for k, v in INTENT_TO_STAGE.items()}

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
  "intent": one of [check_usage, check_config, question, stage_issue, stage_design, stage_backlog, stage_impl, stage_review, stage_pr, fast_track, advance],
  "agent": one of [claude, codex, cursor, null],
  "model": model name string or null,
  "issue_number": integer or null,
  "fast_track": boolean,
  "stages": list of stage names if fast_track else [],
  "needs_confirmation": boolean,
  "ambiguity_reason": short string explaining the ambiguity, or null
}}

Intent rules:
- "advance" = user says sim/ok/aprovado/yes/continua/next/proceed
- "check_usage" = user asks about token usage, agent status, check-usage
- "check_config" = user asks which agents/models are configured per stage, routing config, orchestrator model
- "fast_track" = user explicitly asks to do multiple stages at once ("de uma vez", "tudo junto", "em uma etapa")
- "stage_issue" = create or enrich a GitHub issue ("abra/crie uma issue", "open an issue", "nova issue")
- "stage_design" = solution design, technical analysis, viability, impact
- "stage_backlog" = generate task backlog
- "stage_impl" = ONLY when user explicitly asks to write/implement NEW code or apply backlog tasks. The word "implementação" as a noun referring to past work does NOT trigger this.
- "stage_pr" = commit changes and open pull request (default agent: cursor, model: auto). NEVER merge — merge is always human on GitHub
- "stage_validate" = run tests, lint, typecheck (default agent: cursor, model: auto)
- "stage_review" = code review of implemented changes: "revise o código", "faz um review", "analise o que foi implementado", "review" (default agent: claude)
- "question" = everything else: questions, instructions to an agent that are not a pipeline stage, "poste um comentário", "crie um comentário", "revise", "analise", "verifique", "o que acha", "sumarize", "explique" (default agent: claude)
- IMPORTANT: "codex/claude/cursor, <do something>" where <something> is not a pipeline stage → "question" with that agent

Agent override rules:
- If message starts with or mentions "@claude", "@codex", "@cursor", "use claude/codex/cursor", "pelo claude/codex/cursor", or "codex/claude/cursor <verb>" — set agent accordingly
- model override: look for "model X", "gpt-X", "sonnet-X", "-m X"

needs_confirmation rules (set true ONLY when genuinely ambiguous):
- true: message could reasonably mean two different pipeline stages AND no explicit agent/intent signal was given
- false: user specified an agent explicitly (codex/claude/cursor prefix or @mention) — NEVER confirm in this case
- false: user used a clear action verb that maps to exactly one stage
- false: intent is question/advance/check_usage/check_config/fast_track
- false: when in doubt, prefer false and dispatch

Examples of needs_confirmation=false:
  "codex crie um comentario na issue sumarizando a implementacao" -> question, agent=codex, false (explicit agent + not a pipeline stage)
  "codex, o que esta na issue 42?" -> question, agent=codex, false
  "abra uma issue e depois implemente" -> stage_issue, false (stage_issue takes priority)
  "faz o commit" -> stage_pr, false (clear action)
  "implemente o backlog" -> stage_impl, false (clear stage verb)
  "o que voce acha desta abordagem?" -> question, false (clear question)

Examples of needs_confirmation=true:
  "pode criar isso?" -> true (create what? issue or implementation?)
  "continua com a issue 42" -> true (which stage? design? impl?)
"""

def classify(message: str, state: dict) -> dict:
    """Rule-based shortcuts first, Gemini fallback."""
    msg = message.strip().lower()

    # Shortcut: check-usage
    if re.search(r"check-usage|/check-usage", msg):
        return _sanitize_classification({"intent": "check_usage", "agent": None, "model": None,
                "issue_number": None, "fast_track": False, "stages": [],
                "needs_confirmation": False, "ambiguity_reason": None})

    # Shortcut: check-config / show routing
    if re.search(
        r"check-config|/check-config|"
        r"quais?\s+(s[aã]o\s+)?(os\s+)?(agentes|models?|modelos)|"
        r"(show|list|exib[ae]|mostra).*(routing|config|agents?|models?|modelos)|"
        r"routing\s+config|configura[cç][aã]o\s+(do\s+)?(routing|agente|dev-?agent)|"
        r"qual\s+(agent|modelo|model)\s+(por\s+stage|do\s+orchestrator)",
        msg,
    ):
        return _sanitize_classification({"intent": "check_config", "agent": None, "model": None,
                "issue_number": None, "fast_track": False, "stages": [],
                "needs_confirmation": False, "ambiguity_reason": None})

    # Shortcut: advance
    if re.match(r"^(sim|ok|aprovado|continua|continuar|próximo|proximo|avança|avanca|yes|proceed|next)[\s!.]*$", msg):
        return _sanitize_classification({"intent": "advance", "agent": None, "model": None,
                "issue_number": None, "fast_track": False, "stages": [],
                "needs_confirmation": False, "ambiguity_reason": None})

    # Use Gemini Flash for everything else
    try:
        return _sanitize_classification(_classify_gemini(message, state))
    except Exception as e:
        log.warning("Gemini classify failed: %s — fallback to rules", e)
        return _sanitize_classification(_classify_rules(message, state))


def _classify_gemini(message: str, state: dict) -> dict:
    api_key = os.environ.get("GOOGLE_API_KEY", "")
    if not api_key:
        raise ValueError("GOOGLE_API_KEY not set")

    orch = get_orchestrator_config(_ROUTING_TABLE)
    if orch.provider != "gemini":
        raise ValueError(
            f"Orchestrator provider {orch.provider!r} is not implemented yet "
            "(only 'gemini' is supported). Update routing.yaml orchestrator.provider."
        )

    prompt = CLASSIFY_PROMPT.format(
        message=message,
        state=json.dumps({k: v for k, v in state.items()
                          if k in ("current_stage", "issue_number")})
    )
    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0, "maxOutputTokens": 384},
    }).encode()

    url = (f"https://generativelanguage.googleapis.com/v1beta/models/"
           f"{orch.classifier_model}:generateContent?key={api_key}")
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
              "issue_number": None, "fast_track": False, "stages": [],
              "needs_confirmation": False, "ambiguity_reason": None}

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
        ("stage_pr",       r"\bpr\b|pull\s*request|\bcommit\b|git\s+commit|faz\s+(um\s+)?commit|commita"),
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

# ── Routing (loaded from agents/routing.yaml at import) ───────────────────────

_ROUTING_TABLE = get_routing_table()


def _sanitize_classification(classification: dict) -> dict:
    updated = dict(classification)
    updated["agent"] = normalize_agent_override(updated.get("agent"))
    model = updated.get("model")
    if isinstance(model, str) and model.strip().lower() in ("", "null", "none"):
        updated["model"] = None
    return updated


def _next_pipeline_stage(state: dict) -> str | None:
    stage_idx = int(state.get("current_stage", 0))
    if stage_idx < 0 or stage_idx >= len(STAGES):
        return None
    return STAGES[stage_idx]


def _remap_advance(classification: dict, state: dict) -> dict:
    """Turn bare advance (sim/ok) into the next pipeline stage intent."""
    next_stage = _next_pipeline_stage(state)
    if not next_stage:
        log.info("advance: pipeline complete → summarize")
        updated = dict(classification)
        updated["intent"] = "summarize"
        updated["agent"] = None
        updated["model"] = None
        return updated
    next_intent = STAGE_TO_INTENT.get(next_stage)
    if not next_intent:
        return classification
    updated = dict(classification)
    updated["intent"] = next_intent
    updated["agent"] = None
    updated["model"] = None
    log.info("advance remapped → %s (next stage=%s)", next_intent, next_stage)
    return updated


def _post_issue_comment(issue_number: int, body: str) -> None:
    try:
        subprocess.run(
            ["gh", "issue", "comment", str(issue_number), "-b", body],
            capture_output=True,
            text=True,
            timeout=30,
            cwd=WORKSPACE,
        )
    except Exception as exc:
        log.warning("failed to post issue comment: %s", exc)


def _append_thread_summary(thread: str, issue_number: int | None, output: str) -> str:
    summary = write_thread_summary(thread)
    if issue_number:
        _post_issue_comment(issue_number, summary)
    return f"{output}\n\n---\n{summary}"


def _resolve_dispatch_targets(classification: dict) -> tuple[str, str | None, str]:
    """Agent, model, backend from routing table with user-override priority."""
    intent = classification["intent"]
    route_cfg = resolve_route(intent, _ROUTING_TABLE)
    user_agent = normalize_agent_override(classification.get("agent"))
    agent = user_agent or route_cfg.agent
    model = classification.get("model")
    if model is None:
        if user_agent and user_agent != route_cfg.agent:
            model = resolve_agent_default_model(agent, _ROUTING_TABLE) or route_cfg.model
        else:
            model = route_cfg.model or resolve_agent_default_model(agent, _ROUTING_TABLE)
    return agent, model, route_cfg.backend

SKILL_MAP = {
    "stage_issue":   "/neotool-open-issue",
    "stage_design":  "/neotool-solution-design",
    "stage_backlog": "/neotool-backlog-gen",
}

# ── Prompt builder ────────────────────────────────────────────────────────────

def fetch_issue_context(issue_number: int) -> str:
    """Busca título, body, labels e últimos comentários da issue via gh CLI."""
    try:
        r = subprocess.run(
            ["gh", "issue", "view", str(issue_number),
             "--json", "number,title,body,labels,comments"],
            capture_output=True, text=True, timeout=15, cwd=WORKSPACE,
        )
        if r.returncode != 0:
            log.warning("fetch_issue_context failed: %s", r.stderr[:200])
            return ""
        data = json.loads(r.stdout)
    except Exception as e:
        log.warning("fetch_issue_context error: %s", e)
        return ""

    out = [f"=== Issue #{data['number']}: {data['title']} ==="]
    labels = [lb["name"] for lb in data.get("labels", [])]
    if labels:
        out.append(f"Labels: {', '.join(labels)}")
    body = (data.get("body") or "").strip()
    if body:
        out += ["", body]
    comments = data.get("comments", [])
    if comments:
        out += ["", "--- Comentários ---"]
        for c in comments[-5:]:
            author = c.get("author", {}).get("login", "?")
            date   = (c.get("createdAt") or "")[:10]
            cbody  = (c.get("body") or "").strip()
            out.append(f"[{date}] @{author}: {cbody}")
    return "\n".join(out)


def fetch_branch_diff() -> str:
    """Retorna git diff --stat + diff completo da branch atual vs main."""
    try:
        branch_r = subprocess.run(
            ["git", "branch", "--show-current"],
            capture_output=True, text=True, timeout=10, cwd=WORKSPACE,
        )
        branch = branch_r.stdout.strip()
        if not branch or branch == "main":
            return ""
        stat = subprocess.run(
            ["git", "diff", f"main...{branch}", "--stat"],
            capture_output=True, text=True, timeout=15, cwd=WORKSPACE,
        ).stdout.strip()
        diff = subprocess.run(
            ["git", "diff", f"main...{branch}"],
            capture_output=True, text=True, timeout=15, cwd=WORKSPACE,
        ).stdout.strip()
        if len(diff) > 8000:
            diff = diff[:8000] + "\n...(truncado)"
        return f"--- Diff: {branch} vs main ---\n{stat}\n\n{diff}"
    except Exception as e:
        log.warning("fetch_branch_diff error: %s", e)
        return ""


def build_prompt(classification: dict, original_message: str, state: dict) -> str:
    intent       = classification["intent"]
    issue_number = classification.get("issue_number") or state.get("issue_number")

    # Contexto completo da issue — injetado em todos os intents
    issue_ctx = fetch_issue_context(issue_number) if issue_number else ""

    def _with_issue(base: str) -> str:
        return f"{base}\n\n{issue_ctx}" if issue_ctx else base

    if intent == "fast_track":
        return _with_issue(original_message)

    if intent in SKILL_MAP:
        skill = SKILL_MAP[intent]
        base  = f"{skill}\n\n{original_message}"
        if issue_number:
            base += f"\n\nIssue: #{issue_number}"
        return _with_issue(base)

    if intent == "stage_impl":
        ref = f"issue #{issue_number}" if issue_number else "a tarefa descrita"
        gh_comment = (
            f"\n\nQuando terminar, execute: "
            f"gh issue comment {issue_number} -b "
            '"\'**✅ Implementação concluída**\\n\\n<resumo do que foi implementado>"'
        ) if issue_number else ""
        return _with_issue(f"Implementar o backlog da {ref}.\n{original_message}{gh_comment}")

    if intent == "stage_pr":
        ref = f"issue #{issue_number}" if issue_number else "o trabalho atual"
        return _with_issue(
            f"Fazer commit das alterações na branch atual e abrir pull request para {ref}.\n"
            "NÃO faça merge do PR — merge é sempre decisão humana no GitHub.\n"
            f"{original_message}"
        )

    if intent == "stage_review":
        diff_ctx = fetch_branch_diff()
        gh_comment = (
            f"\n\nAo terminar, execute: "
            f"gh issue comment {issue_number} -b "
            '"**🔍 Code Review**\\n\\n<achados: problemas encontrados, sugestões e veredicto final>"'
        ) if issue_number else ""
        base = f"Revisar o código implementado.\n{original_message}{gh_comment}"
        if diff_ctx:
            base += f"\n\n{diff_ctx}"
        return _with_issue(base)

    if intent == "stage_validate":
        return _with_issue(f"Validar implementação: rodar testes, lint e typecheck.\n{original_message}")

    if intent == "advance":
        stage_idx = state.get("current_stage", 0)
        next_stage = STAGES[stage_idx] if stage_idx < len(STAGES) else "impl"
        skill = SKILL_MAP.get(f"stage_{next_stage}", "")
        if skill and issue_number:
            return _with_issue(f"{skill}\n\nIssue: #{issue_number}")
        return _with_issue(original_message)

    # question / fallback — passa mensagem + contexto completo da issue
    return _with_issue(original_message)

_INTENT_LABELS = {
    "stage_issue":    "Abrir issue",
    "stage_design":   "Design de solução",
    "stage_backlog":  "Gerar backlog",
    "stage_impl":     "Implementar código",
    "stage_validate": "Validar / testar",
    "stage_review":   "Revisar código",
    "stage_pr":       "Criar PR",
    "question":       "Responder pergunta",
    "fast_track":     "Executar tudo",
}

def _confirmation_message(classification: dict) -> str:
    intent = classification["intent"]
    agent, _, _ = _resolve_dispatch_targets(classification)
    main   = _INTENT_LABELS.get(intent, intent)
    reason = classification.get("ambiguity_reason") or "Intenção ambígua"
    return (
        f"🤔 *{reason}* — vou executar:\n"
        f"• *{main}* via `{agent}`\n\n"
        f"Responda `sim` para confirmar, ou descreva o que prefere fazer."
    )


# ── Dispatcher ────────────────────────────────────────────────────────────────


def _setup_impl_branch(issue_number: int | None) -> str | None:
    """Cria/faz checkout de feat/GH-{N}-{slug} antes do dispatch de impl.
    Retorna o nome da branch criada, ou None se falhar."""
    if not issue_number:
        return None
    try:
        # Pega título da issue para montar o slug
        r = subprocess.run(
            ["gh", "issue", "view", str(issue_number), "--json", "title", "-q", ".title"],
            capture_output=True, text=True, timeout=15, cwd=WORKSPACE,
        )
        title = r.stdout.strip() if r.returncode == 0 else ""
        slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")[:50] if title else ""
        branch = f"feat/GH-{issue_number}-{slug}" if slug else f"feat/GH-{issue_number}"
        log.info("impl branch: %s", branch)

        # Garante que estamos no main atualizado antes de criar a branch
        subprocess.run(["git", "checkout", "main"], capture_output=True, cwd=WORKSPACE)
        subprocess.run(["git", "pull", "--ff-only"], capture_output=True, cwd=WORKSPACE)

        # Cria ou faz checkout da branch
        result = subprocess.run(
            ["git", "checkout", "-b", branch],
            capture_output=True, text=True, cwd=WORKSPACE,
        )
        if result.returncode != 0 and "already exists" in result.stderr:
            subprocess.run(["git", "checkout", branch], capture_output=True, cwd=WORKSPACE)
            log.info("impl branch já existe, checkout: %s", branch)
        else:
            log.info("impl branch criada: %s", branch)
        return branch
    except Exception as e:
        log.warning("_setup_impl_branch failed: %s", e)
        return None


def _checkpoint_status_from_result(result: BackendResult) -> str:
    """Map backend outcome to checkpoint status."""
    return "completed" if result.exit_code == 0 else "failed"


def dispatch_result(
    classification: dict,
    prompt: str,
    thread: str,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: "str | None" = None,
    meta_callback: "callable | None" = None,
    backend: AgentBackend | None = None,
) -> BackendResult:
    intent = classification["intent"]
    agent, model, routing_backend = _resolve_dispatch_targets(classification)

    TIMEOUT = {"claude": 1800, "codex": 1800, "cursor": 1800}.get(agent, 1800)
    log.info(
        "dispatch intent=%s agent=%s model=%s backend=%s timeout=%ds",
        intent, agent, model, routing_backend, TIMEOUT,
    )

    # Para stage_impl: cria/faz checkout da branch antes de chamar o agente
    branch = None
    if intent == "stage_impl":
        branch = _setup_impl_branch(classification.get("issue_number"))

    # Notifica o agente sobre o dispatch (para status estruturado no Slack)
    if meta_callback:
        meta_callback({
            "intent":       intent,
            "agent":        agent,
            "model":        model or "",
            "issue_number": classification.get("issue_number"),
            "label":        _INTENT_LABELS.get(intent, "Trabalhando"),
            "branch":       branch,
        })

    env: dict[str, str] = {"NEOTOOL_THREAD": thread}
    if adapter_env:
        env.update(adapter_env)

    if routing_backend != "cli":
        log.warning("backend %r not implemented — using CLIBackend", routing_backend)

    invoke_backend = backend or LoggingBackend(
        get_default_backend(),
        task_id=thread,
        stage=log_stage_for_intent(intent),
    )
    return invoke_backend.invoke(
        prompt,
        agent=agent,
        model=model,
        thread=thread,
        env=env,
        progress_log=progress_log,
        timeout=TIMEOUT,
    )


def dispatch(
    classification: dict,
    prompt: str,
    thread: str,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: "str | None" = None,
    meta_callback: "callable | None" = None,
    backend: AgentBackend | None = None,
) -> str:
    """User-facing dispatch — returns agent output string only."""
    return dispatch_result(
        classification,
        prompt,
        thread,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
        backend=backend,
    ).output


def _format_pause_output(output: str, pause: PauseDecision | None) -> str:
    if pause and pause.pause:
        return f"{output}\n\n⏸️ _Circuit-breaker: {pause.reason}_"
    return output


def _finalize_stage_run(
    thread: str,
    classification: dict,
    state: dict,
    *,
    issue_number: int | None,
    intent: str,
    result: BackendResult,
) -> PauseDecision | None:
    """Write terminal checkpoint, update stage state, evaluate circuit-breakers."""
    if issue_number and intent in INTENT_TO_STAGE:
        _write_stage_checkpoint(
            issue_number,
            intent,
            status=_checkpoint_status_from_result(result),
            task_id=thread,
            output_summary=result.output or "",
        )
    if _checkpoint_status_from_result(result) == "completed":
        _apply_stage_state(thread, classification, state)

    stage_name = INTENT_TO_STAGE.get(intent)
    if not stage_name:
        return None

    merged_state = {**state, **load_state(thread)}
    pause, updates = apply_post_stage_circuit_breaker(
        stage=stage_name,
        result=result,
        state=merged_state,
    )
    if updates:
        save_state(thread, updates)
    return pause if pause.pause else None


def _dispatch_stage_with_finalize(
    classification: dict,
    prompt: str,
    thread: str,
    state: dict,
    *,
    issue_number: int | None,
    intent: str,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str:
    if issue_number and intent in INTENT_TO_STAGE:
        _write_stage_checkpoint(
            issue_number,
            intent,
            status="in_progress",
            task_id=thread,
        )
    result = dispatch_result(
        classification,
        prompt,
        thread,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )
    pause = _finalize_stage_run(
        thread,
        classification,
        state,
        issue_number=issue_number,
        intent=intent,
        result=result,
    )
    output = _format_pause_output(result.output, pause)
    if (
        intent == "stage_pr"
        and _checkpoint_status_from_result(result) == "completed"
    ):
        output = _append_thread_summary(thread, issue_number, output)
    return output


def _dispatch_review_with_verifier(
    classification: dict,
    prompt: str,
    thread: str,
    state: dict,
    *,
    issue_number: int | None,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
    verifier_fn=run_verifier,
) -> str:
    """Run deterministic verifier before review; retry impl up to IMPL_RETRY_LIMIT."""
    merged_state = {**state, **load_state(thread)}
    retry_count = int(merged_state.get("impl_retry_count", 0))
    verifier: VerifierResult = verifier_fn()

    while not verifier.passed and retry_count < IMPL_RETRY_LIMIT:
        retry_count += 1
        save_state(thread, {"impl_retry_count": retry_count})
        impl_classification = dict(classification)
        impl_classification["intent"] = "stage_impl"
        impl_prompt = build_impl_retry_prompt(verifier, issue_number)
        log.info(
            "verifier failed — impl retry %d/%d",
            retry_count,
            IMPL_RETRY_LIMIT,
        )
        _dispatch_stage_with_finalize(
            impl_classification,
            impl_prompt,
            thread,
            state,
            issue_number=issue_number,
            intent="stage_impl",
            adapter_env=adapter_env,
            progress_log=progress_log,
            meta_callback=meta_callback,
        )
        verifier = verifier_fn()

    if not verifier.passed:
        save_state(thread, {"impl_retry_count": retry_count})
        return (
            "⏸️ _Verifier failed after "
            f"{retry_count} impl retries (limit {IMPL_RETRY_LIMIT})._\n\n"
            f"--- Last verifier output ---\n{verifier.output}"
        )

    save_state(thread, {"impl_retry_count": 0})
    return _dispatch_stage_with_finalize(
        classification,
        prompt,
        thread,
        state,
        issue_number=issue_number,
        intent="stage_review",
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )


def _classification_for_stage(plan: Plan, stage: str) -> dict:
    return {
        "intent": stage_to_intent(stage),
        "agent": None,
        "model": None,
        "issue_number": plan.issue_number,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }


def execute_plan(
    plan: Plan,
    thread: str,
    state: dict,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str:
    """Run all stages in a plan sequentially, honoring circuit-breakers."""
    outputs: list[str] = []
    issue_number = plan.issue_number or state.get("issue_number")

    for stage in plan.stages:
        intent = stage_to_intent(stage)
        classification = _classification_for_stage(plan, stage)
        prompt = build_prompt(classification, plan.summary, state)
        log.info("execute_plan stage=%s issue=%s", stage, issue_number)

        if stage == "review":
            output = _dispatch_review_with_verifier(
                classification,
                prompt,
                thread,
                state,
                issue_number=issue_number,
                adapter_env=adapter_env,
                progress_log=progress_log,
                meta_callback=meta_callback,
            )
            outputs.append(f"**{stage}**\n{output}")
            if output.startswith("⏸️"):
                save_state(thread, {
                    "pending_plan": {
                        "plan": plan.to_dict(),
                        "next_stage_index": plan.stages.index(stage) + 1,
                    },
                })
                return "\n\n".join(outputs)
            state = load_state(thread)
            continue

        if issue_number and intent in INTENT_TO_STAGE:
            _write_stage_checkpoint(
                issue_number,
                intent,
                status="in_progress",
                task_id=thread,
            )

        result = dispatch_result(
            classification,
            prompt,
            thread,
            adapter_env=adapter_env,
            progress_log=progress_log,
            meta_callback=meta_callback,
        )
        pause = _finalize_stage_run(
            thread,
            classification,
            state,
            issue_number=issue_number,
            intent=intent,
            result=result,
        )
        stage_output = _format_pause_output(result.output, pause)
        if (
            stage == "pr"
            and _checkpoint_status_from_result(result) == "completed"
        ):
            stage_output = _append_thread_summary(thread, issue_number, stage_output)
        outputs.append(f"**{stage}**\n{stage_output}")

        if pause and pause.pause:
            save_state(thread, {
                "pending_plan": {
                    "plan": plan.to_dict(),
                    "next_stage_index": plan.stages.index(stage) + 1,
                },
            })
            return "\n\n".join(outputs)

        state = load_state(thread)

    save_state(thread, {"pending_plan": None})
    return "\n\n".join(outputs)


def _handle_fast_track(
    message: str,
    thread: str,
    state: dict,
    *,
    mode: str,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str:
    issue_number = state.get("issue_number") or extract_issue_from_message(message)
    issue_ctx = fetch_issue_context(issue_number) if issue_number else ""
    plan = generate_plan(message, state, issue_context=issue_ctx)
    if issue_number and not plan.issue_number:
        plan = Plan(
            summary=plan.summary,
            stages=plan.stages,
            open_questions=plan.open_questions,
            autonomous_eligible=plan.autonomous_eligible,
            issue_number=issue_number,
        )

    pause = should_pause_for_plan(plan, mode=mode)
    if pause.pause:
        save_state(thread, {"pending_plan": {"plan": plan.to_dict(), "next_stage_index": 0}})
        return format_plan_message(plan, reason=pause.reason)

    return execute_plan(
        plan,
        thread,
        state,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )


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


def _resolve_issue_number(
    classification: dict,
    state: dict,
    message: str,
) -> int | None:
    issue_number = classification.get("issue_number") or state.get("issue_number")
    if not issue_number:
        issue_number = extract_issue_from_message(message)
    return issue_number


def _sync_state_from_checkpoint(state: dict, issue_number: int) -> dict:
    """Merge GitHub issue checkpoint into thread state for resume."""
    checkpoint = fetch_last_checkpoint(issue_number)
    if not checkpoint:
        return state
    merged = dict(state)
    stage_idx = current_stage_from_checkpoint(checkpoint, STAGES)
    if stage_idx is not None:
        merged["current_stage"] = stage_idx
    merged["last_checkpoint"] = checkpoint.to_dict()
    if checkpoint.issue_number:
        merged["issue_number"] = checkpoint.issue_number
    log.info(
        "checkpoint resume issue=%s stage=%s status=%s → current_stage=%s",
        issue_number,
        checkpoint.stage,
        checkpoint.status,
        merged.get("current_stage"),
    )
    return merged


def _maybe_bump_completed_stage_intent(
    classification: dict,
    checkpoint: Checkpoint | None,
) -> dict:
    """If user repeats a stage already completed, advance to the next one."""
    if not checkpoint or checkpoint.status != "completed":
        return classification
    intent = classification.get("intent", "")
    stage_name = INTENT_TO_STAGE.get(intent)
    if not stage_name or stage_name != checkpoint.stage:
        return classification
    idx = STAGES.index(stage_name)
    next_idx = idx + 1
    if next_idx >= len(STAGES):
        return classification
    next_stage = STAGES[next_idx]
    next_intent = STAGE_TO_INTENT[next_stage]
    log.info(
        "checkpoint: stage %s already completed — bumping intent %s → %s",
        stage_name,
        intent,
        next_intent,
    )
    updated = dict(classification)
    updated["intent"] = next_intent
    return updated


def _write_stage_checkpoint(
    issue_number: int,
    intent: str,
    *,
    status: str,
    task_id: str,
    output_summary: str = "",
) -> None:
    stage_name = INTENT_TO_STAGE.get(intent)
    if not stage_name or not issue_number:
        return
    write_checkpoint(
        issue_number,
        stage=stage_name,
        status=status,
        task_id=task_id,
        output_summary=output_summary,
    )


def _apply_stage_state(thread: str, classification: dict, state: dict) -> None:
    """Persiste issue_number e current_stage após dispatch."""
    updates = {}
    if classification.get("issue_number"):
        updates["issue_number"] = classification["issue_number"]
    intent = classification["intent"]
    stage_map = {"stage_issue": 1, "stage_design": 2, "stage_backlog": 3,
                 "stage_impl": 4, "stage_review": 5, "stage_validate": 6,
                 "stage_pr": 7}
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
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: "str | None" = None,
    meta_callback: "callable | None" = None,
    clean_message: bool = True,
    mode: str = "interactive",
) -> str | None:
    """Entry point: classifica, confirma se ambíguo, despacha, atualiza estado."""
    message_clean = clean_slack_message(message) if clean_message else message.strip()
    state = load_state(thread)
    issue_number = state.get("issue_number") or extract_issue_from_message(message_clean)
    if issue_number:
        state = _sync_state_from_checkpoint(state, issue_number)
        save_state(thread, {
            "current_stage": state.get("current_stage"),
            "issue_number": issue_number,
            "last_checkpoint": state.get("last_checkpoint"),
        })
    log.info("route thread=%s mode=%s state=%s msg=%r", thread, mode, state, message_clean[:100])

    # ── Plano pendente (aprovação ou resume pós circuit-breaker) ─────────────
    pending_plan = state.get("pending_plan")
    if pending_plan:
        quick = classify(message_clean, {})
        if quick["intent"] == "advance":
            save_state(thread, {"pending_plan": None})
            plan = Plan.from_dict(pending_plan.get("plan", {}))
            if not plan:
                return "❌ Plano pendente inválido — tente novamente."
            start_idx = int(pending_plan.get("next_stage_index", 0))
            if start_idx > 0:
                plan = Plan(
                    summary=plan.summary,
                    stages=plan.stages[start_idx:],
                    open_questions=plan.open_questions,
                    autonomous_eligible=plan.autonomous_eligible,
                    issue_number=plan.issue_number,
                )
            log.info("route: plano aprovado — executando %d estágios", len(plan.stages))
            return execute_plan(
                plan,
                thread,
                state,
                adapter_env=adapter_env,
                progress_log=progress_log,
                meta_callback=meta_callback,
            )
        log.info("route: plano pendente cancelado → reclassificando")
        save_state(thread, {"pending_plan": None})
        state.pop("pending_plan", None)

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
            issue_number = _resolve_issue_number(clf, state, message_clean)
            if issue_number:
                clf = dict(clf)
                clf["issue_number"] = issue_number
            intent = clf["intent"]
            if intent == "stage_review":
                return _dispatch_review_with_verifier(
                    clf,
                    prom,
                    thread,
                    state,
                    issue_number=issue_number,
                    adapter_env=adapter_env,
                    progress_log=progress_log,
                    meta_callback=meta_callback,
                )
            return _dispatch_stage_with_finalize(
                clf,
                prom,
                thread,
                state,
                issue_number=issue_number,
                intent=intent,
                adapter_env=adapter_env,
                progress_log=progress_log,
                meta_callback=meta_callback,
            )
        else:
            # Usuário quer outra coisa → descarta pendência e reclassifica
            log.info("route: confirmação cancelada → reclassificando")
            save_state(thread, {"pending_confirmation": None})
            state.pop("pending_confirmation", None)
            # cai no fluxo normal abaixo

    # ── Fluxo normal ──────────────────────────────────────────────────────────
    classification = classify(message_clean, state)
    issue_number = _resolve_issue_number(classification, state, message_clean)
    if issue_number:
        classification["issue_number"] = issue_number
        state = _sync_state_from_checkpoint(state, issue_number)

    last_cp_dict = state.get("last_checkpoint")
    last_checkpoint = Checkpoint.from_dict(last_cp_dict) if last_cp_dict else None
    classification = _maybe_bump_completed_stage_intent(classification, last_checkpoint)
    if classification["intent"] == "advance":
        classification = _remap_advance(classification, state)
    log.info("classification: %s", json.dumps(classification))

    if classification["intent"] == "check_usage":
        return None

    if classification["intent"] == "check_config":
        return format_routing_summary(_ROUTING_TABLE)

    if classification["intent"] == "summarize":
        summary = write_thread_summary(thread)
        if issue_number:
            _post_issue_comment(issue_number, summary)
        return summary

    if classification["intent"] == "fast_track":
        return _handle_fast_track(
            message_clean,
            thread,
            state,
            mode=mode,
            adapter_env=adapter_env,
            progress_log=progress_log,
            meta_callback=meta_callback,
        )

    prompt = build_prompt(classification, message_clean, state)
    log.info("prompt built: %s", prompt[:120])

    # ── Confirmação: decisão do Gemini, não de regex ────────────────────────────
    if classification.get("needs_confirmation"):
        log.info("route: Gemini pediu confirmação — %s", classification.get("ambiguity_reason"))
        save_state(thread, {"pending_confirmation": {
            "classification": classification,
            "prompt": prompt,
        }})
        return _confirmation_message(classification)

    intent = classification["intent"]
    if intent == "stage_review":
        return _dispatch_review_with_verifier(
            classification,
            prompt,
            thread,
            state,
            issue_number=issue_number,
            adapter_env=adapter_env,
            progress_log=progress_log,
            meta_callback=meta_callback,
        )

    return _dispatch_stage_with_finalize(
        classification,
        prompt,
        thread,
        state,
        issue_number=issue_number,
        intent=intent,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )
