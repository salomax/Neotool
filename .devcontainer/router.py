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
from planner import (
    Plan,
    format_plan_message,
    generate_plan,
    normalize_plan_issue,
    resolve_plan_issue_number,
    stage_to_intent,
)
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
import state_store
import state_machine
from decision import Action, next_action

log = logging.getLogger("router")
SESSION_DIR = "/tmp/bridge-sessions"
WORKSPACE   = "/workspaces/invistus"

# ── Stage machine ─────────────────────────────────────────────────────────────
# Imported from state_machine to maintain canonical list

STAGES = state_machine.STAGES
INTENT_TO_STAGE = state_machine.INTENT_TO_STAGE
STAGE_TO_INTENT = state_machine.STAGE_TO_INTENT

def session_path(thread: str) -> Path:
    """[Legacy] Kept for compatibility. State is now stored in SQLite via state_store."""
    Path(SESSION_DIR).mkdir(parents=True, exist_ok=True)
    return Path(SESSION_DIR) / f"{thread}.json"

def load_state(thread: str) -> dict:
    """Load state from durable state_store (SQLite)."""
    return state_store.load_state(thread)

def save_state(thread: str, updates: dict):
    """Atomically update state in durable state_store (SQLite)."""
    state_store.update_state(thread, updates)

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
- "advance" = handled locally for bare sim/ok/aprovado — do NOT classify these from the message text
- "check_usage" = user asks about token usage, agent status, check-usage
- "check_config" = user asks which agents/models are configured per stage, routing config, orchestrator model
- "fast_track" = user explicitly asks to do multiple stages at once ("de uma vez", "tudo junto", "em uma etapa")
- "stage_issue" = create or enrich a GitHub issue ("abra/crie uma issue", "open an issue", "nova issue")
- "stage_design" = solution design, technical analysis, viability, impact
- "stage_backlog" = generate task backlog
- "stage_impl" = ONLY when user explicitly asks to write/implement NEW code or apply backlog tasks. The word "implementação" as a noun referring to past work does NOT trigger this.
- "stage_pr" = commit changes and open pull request (default agent: cursor, model: auto). NEVER merge — merge is always human on GitHub
- "stage_validate" = run tests, lint, typecheck (default agent: cursor, model: auto)
- "stage_review" = code review of EXISTING implementation: "revise o código", "faz um review", "faça a revisão", "analise a implementação", "review", "já implementei" + pedido de revisão, link para comentário de implementação na issue (default agent: claude)
- "question" = everything else: questions, instructions to an agent that are not a pipeline stage, "poste um comentário", "crie um comentário", "verifique", "o que acha", "sumarize", "explique" (default agent: claude)
- IMPORTANT: GitHub issue URLs (…/issues/123) or issuecomment links do NOT mean stage_issue — use stage_review when the user asks for review of existing work
- IMPORTANT: If user says work was already done outside the pipeline ("já implementei") and asks for review → stage_review, NOT stage_impl or stage_design
- IMPORTANT: Use thread state (next_pipeline_stage_if_user_says_sim, last_checkpoint) to disambiguate resume vs new stage requests
- IMPORTANT: "codex/claude/cursor, <do something>" where <something> is not a pipeline stage → "question" with that agent

Thread state fields:
- issue_number: active GitHub issue for this Slack thread
- next_pipeline_stage_if_user_says_sim: stage that bare "sim" would run next (issue→design→backlog→impl→review→validate→pr)
- last_checkpoint: last recorded pipeline stage on the issue and its status (completed/in_progress/failed)
- has_pending_plan: true when a fast_track plan awaits approval

Extract issue_number from message text or URLs when present.

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
  "já implementei, faça a revisão" + issuecomment URL -> stage_review, agent=claude, false
"""

_ADVANCE_ONLY_RE = re.compile(
    r"^(sim|ok|aprovado|continua|continuar|próximo|proximo|avança|avanca|yes|proceed|next)[\s!.]*$",
    re.IGNORECASE,
)
_ISSUE_COMMENT_URL_RE = re.compile(
    r"github\.com/[^/\s]+/[^/\s]+/issues/(\d+)#issuecomment-(\d+)",
    re.IGNORECASE,
)


class ClassificationError(Exception):
    """Orchestrator LLM classifier failed or is unavailable."""


def _classification_state_for_prompt(state: dict) -> dict:
    """Thread context passed to the LLM classifier."""
    ctx: dict = {}
    issue = state.get("issue_number")
    if issue:
        ctx["issue_number"] = issue
    stage_idx = state.get("current_stage")
    if stage_idx is not None:
        idx = int(stage_idx)
        ctx["current_stage_index"] = idx
        if 0 <= idx < len(STAGES):
            ctx["next_pipeline_stage_if_user_says_sim"] = STAGES[idx]
    last_cp = state.get("last_checkpoint")
    if isinstance(last_cp, dict):
        ctx["last_checkpoint"] = {
            "stage": last_cp.get("stage"),
            "status": last_cp.get("status"),
        }
    if state.get("pending_plan"):
        ctx["has_pending_plan"] = True
    return ctx



# ── Fallback rules-based classifier ───────────────────────────────────────────

# Pattern matchers for rule-based classification
_AGENT_MENTION_RE = re.compile(
    r"@(?:claude|codex|cursor)|"
    r"use\s+(?:claude|codex|cursor)|"
    r"(?:codex|claude|cursor)\s+(?:abra?|crie|implemente|revise|faça)",
    re.IGNORECASE,
)

_MODEL_OVERRIDE_RE = re.compile(
    r"-m\s+(\S+)|"
    r"model\s+(\S+)|"
    r"gpt-(\S+)|"
    r"sonnet-(\S+)",
    re.IGNORECASE,
)

_CHECK_USAGE_RE = re.compile(
    r"check-usage|check usage|usage|uso|token usage|quanto gastei|quanto\s+(?:já\s+)?usei|quanto\s+(?:foi\s+)?gasto",
    re.IGNORECASE,
)

_CHECK_CONFIG_RE = re.compile(
    r"(?:qual|quais|which)\s+(?:agente|modelo|agent|model)|"
    r"config|routing|orchestrator|qual\s+agente|quais\s+modelos",
    re.IGNORECASE,
)

_FAST_TRACK_RE = re.compile(
    r"de uma vez|tudo junto|tudo de uma vez|em uma etapa|em uma só etapa|pipeline completo|todas as fases|all at once|everything together|full pipeline|all stages",
    re.IGNORECASE,
)

_STAGE_ISSUE_RE = re.compile(
    r"(?:abra|abre|crie|criar|open|new)\s+(?:um|uma|an|a)?\s*issue|nova\s+issue",
    re.IGNORECASE,
)

_STAGE_DESIGN_RE = re.compile(
    r"(?:design|análise\s+técnica|viabilidade|impacto|como\s+(?:implementar|resolver))",
    re.IGNORECASE,
)

_STAGE_BACKLOG_RE = re.compile(
    r"(?:backlog|quebra\s+em\s+tarefas|gera?s?\s+(?:as\s+)?tasks?|atividades)",
    re.IGNORECASE,
)

_STAGE_IMPL_RE = re.compile(
    r"(?:implemente|implementar|escreva\s+(?:o\s+)?código|apply\s+backlog)",
    re.IGNORECASE,
)

_STAGE_REVIEW_RE = re.compile(
    r"(?:revise?|review|faz?\s+(?:um\s+)?review|faça?\s+a\s+revisão|analise?\s+a\s+implementação|já\s+implementei)",
    re.IGNORECASE,
)

_STAGE_VALIDATE_RE = re.compile(
    r"(?:rode?\s+(?:os\s+)?testes?|valid|lint|typecheck)",
    re.IGNORECASE,
)

_STAGE_PR_RE = re.compile(
    r"(?:faz?\s+(?:o\s+)?commit|abra?\s+(?:o\s+)?pr|pull\s+request)",
    re.IGNORECASE,
)


def _extract_agent_from_message(message: str) -> str | None:
    """Extract agent mention from message: @claude, use claude, claude <verb>, etc."""
    msg_lower = message.lower()
    if "@claude" in msg_lower or "use claude" in msg_lower:
        return "claude"
    if "@codex" in msg_lower or "use codex" in msg_lower:
        return "codex"
    if "@cursor" in msg_lower or "use cursor" in msg_lower:
        return "cursor"
    # Pattern: "codex/claude/cursor <verb>"
    if re.match(r"^\s*(?:codex|claude|cursor)\s+", message, re.IGNORECASE):
        match = re.match(r"^\s*(codex|claude|cursor)\s+", message, re.IGNORECASE)
        if match:
            return match.group(1).lower()
    return None


def _extract_model_from_message(message: str) -> str | None:
    """Extract model override from message: model X, gpt-X, sonnet-X, -m X."""
    match = _MODEL_OVERRIDE_RE.search(message)
    if match:
        # Return the first non-None group
        for group in match.groups():
            if group:
                return group
    return None


def _classify_rules(message: str, state: dict) -> dict:
    """
    Rule-based classifier fallback when Gemini is unavailable.
    Returns same dict shape as _classify_gemini.
    """
    msg = message.strip()
    
    # Check for check_usage
    if _CHECK_USAGE_RE.search(msg):
        return {
            "intent": "check_usage",
            "agent": None,
            "model": None,
            "issue_number": None,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    # Check for check_config
    if _CHECK_CONFIG_RE.search(msg):
        return {
            "intent": "check_config",
            "agent": None,
            "model": None,
            "issue_number": None,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    # Extract agent and model overrides
    agent = _extract_agent_from_message(msg)
    model = _extract_model_from_message(msg)
    
    # Extract issue number
    issue_number = extract_issue_from_message(msg)
    
    # Check for fast_track
    if _FAST_TRACK_RE.search(msg):
        return {
            "intent": "fast_track",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": True,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    # Check for stage intents (in order of priority/clarity)
    # Check concrete actions before abstract ones
    if _STAGE_IMPL_RE.search(msg):
        return {
            "intent": "stage_impl",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    if _STAGE_REVIEW_RE.search(msg):
        return {
            "intent": "stage_review",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    if _STAGE_PR_RE.search(msg):
        return {
            "intent": "stage_pr",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    if _STAGE_VALIDATE_RE.search(msg):
        return {
            "intent": "stage_validate",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    if _STAGE_ISSUE_RE.search(msg):
        return {
            "intent": "stage_issue",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    if _STAGE_DESIGN_RE.search(msg):
        return {
            "intent": "stage_design",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    if _STAGE_BACKLOG_RE.search(msg):
        return {
            "intent": "stage_backlog",
            "agent": agent,
            "model": model,
            "issue_number": issue_number,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        }
    
    # Default: question
    return {
        "intent": "question",
        "agent": agent,
        "model": model,
        "issue_number": issue_number,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }


def classify(message: str, state: dict) -> dict:
    """Bare advance (sim/ok/…) is local; everything else goes to the LLM classifier."""
    if _ADVANCE_ONLY_RE.match(message.strip()):
        return _sanitize_classification({
            "intent": "advance",
            "agent": None,
            "model": None,
            "issue_number": None,
            "fast_track": False,
            "stages": [],
            "needs_confirmation": False,
            "ambiguity_reason": None,
        })

    try:
        return _sanitize_classification(_classify_gemini(message, state))
    except Exception as e:
        log.warning("Gemini classify failed: %s — falling back to rules", e)
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
        state=json.dumps(_classification_state_for_prompt(state), ensure_ascii=False),
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
    "stage_impl":    "/neotool-implement",
}

_AGENT_MISSING_ISSUE_RE = re.compile(
    r"preciso do n[uú]mero da issue|"
    r"what is the issue number|"
    r"issue number is required|"
    r"informe no formato\s*#|"
    r"do not proceed without",
    re.IGNORECASE,
)

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
    issue_number = (
        classification.get("issue_number")
        or state.get("issue_number")
        or extract_issue_from_message(original_message)
    )

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
        skill = SKILL_MAP["stage_impl"]
        ref = f"issue #{issue_number}" if issue_number else "a tarefa descrita"
        gh_comment = (
            f"\n\nQuando terminar, execute: "
            f"gh issue comment {issue_number} -b "
            '"\'**✅ Implementação concluída**\\n\\n<resumo do que foi implementado>"'
        ) if issue_number else ""
        base = f"{skill}\n\nImplementar o backlog da {ref}."
        if issue_number:
            base += f"\n\nIssue: #{issue_number}"
        base += f"\n{original_message}{gh_comment}"
        return _with_issue(base)

    if intent == "stage_pr":
        ref = f"issue #{issue_number}" if issue_number else "o trabalho atual"
        return _with_issue(
            f"Fazer commit das alterações na branch atual e abrir pull request para {ref}.\n"
            "NÃO faça merge do PR — merge é sempre decisão humana no GitHub.\n"
            f"{original_message}"
        )

    if intent == "stage_merge":
        ref = f"issue #{issue_number}" if issue_number else "o PR atual"
        return _with_issue(
            f"Fazer merge explicitamente do pull request para {ref}.\n"
            "Só faça merge se a pull request estiver pronta (passou em testes, review aprovado).\n"
            "Use: gh pr merge\n"
            f"{original_message}"
        )

    if intent == "stage_review":
        diff_ctx = fetch_branch_diff()
        comment_url = extract_issue_comment_url(original_message)
        gh_comment = (
            f"\n\nAo terminar, execute: "
            f"gh issue comment {issue_number} -b "
            '"**🔍 Code Review**\\n\\n<achados: problemas encontrados, sugestões e veredicto final>"'
        ) if issue_number else ""
        base = (
            "Revisar o código já implementado (não reimplementar).\n"
            f"{original_message}{gh_comment}"
        )
        if comment_url:
            comment_id = extract_issue_comment_id(original_message)
            base += (
                f"\n\nReferência principal da implementação: {comment_url}\n"
                f"Leia esse comentário com `gh api "
                f"repos/invistus/invistus/issues/comments/{comment_id}`."
            )
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
    "stage_merge":    "Fazer merge do PR",
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


def _checkpoint_status_from_result(
    result: BackendResult,
    *,
    intent: str | None = None,
) -> str:
    """Map backend outcome to checkpoint status."""
    if intent == "stage_impl" and _agent_blocked_on_missing_issue(result.output):
        return "failed"
    return "completed" if result.exit_code == 0 else "failed"


def _agent_blocked_on_missing_issue(output: str) -> bool:
    return bool(_AGENT_MISSING_ISSUE_RE.search(output or ""))


def _classification_with_issue(classification: dict, issue_number: int | None) -> dict:
    if not issue_number:
        return classification
    updated = dict(classification)
    updated["issue_number"] = issue_number
    return updated


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


def _notify_stage_outcome(
    meta_callback: callable | None,
    *,
    intent: str,
    issue_number: int | None,
    status: str,
) -> None:
    """Push terminal stage status into adapter meta (Slack/CLI)."""
    if not meta_callback:
        return
    payload = {
        "intent": intent,
        "status": status,
        "issue_number": issue_number,
        "label": _INTENT_LABELS.get(intent, "Trabalhando"),
    }
    try:
        meta_callback(payload)
    except Exception as exc:
        log.warning("meta_callback status notify failed: %s", exc)


def _finalize_stage_run(
    thread: str,
    classification: dict,
    state: dict,
    *,
    issue_number: int | None,
    intent: str,
    result: BackendResult,
    meta_callback: callable | None = None,
) -> PauseDecision | None:
    """Write terminal checkpoint, update stage state, evaluate circuit-breakers."""
    status = _checkpoint_status_from_result(result, intent=intent)
    blocked = intent == "stage_impl" and _agent_blocked_on_missing_issue(result.output)
    outcome = "blocked" if blocked else status

    if issue_number and intent in INTENT_TO_STAGE:
        _write_stage_checkpoint(
            issue_number,
            intent,
            status=status,
            task_id=thread,
            output_summary=result.output or "",
        )

    if status == "completed":
        _apply_stage_state(thread, classification, state)
        save_state(thread, {
            "awaiting_issue_number": None,
            "pending_retry_intent": None,
        })
    elif blocked:
        # Do not advance current_stage — keep pending so "#N" or retry works.
        save_state(thread, {
            "awaiting_issue_number": True,
            "pending_retry_intent": intent,
            "issue_number": issue_number or state.get("issue_number"),
        })

    _notify_stage_outcome(
        meta_callback,
        intent=intent,
        issue_number=issue_number,
        status=outcome,
    )

    if blocked:
        return PauseDecision(
            pause=True,
            reason=(
                "Implementação bloqueada — o agente não recebeu o número da issue. "
                "Envie `#N` (ex.: `#376`) para retentar. `sim` não avança para review."
            ),
        )

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
        meta_callback=meta_callback,
    )
    output = _format_pause_output(result.output, pause)
    if (
        intent in ("stage_pr", "stage_merge")
        and _checkpoint_status_from_result(result, intent=intent) == "completed"
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
    state = load_state(thread)
    plan = normalize_plan_issue(plan, state)
    issue_number = resolve_plan_issue_number(plan, state)
    if issue_number:
        save_state(thread, {"issue_number": issue_number})

    for stage in plan.stages:
        state = load_state(thread)
        issue_number = resolve_plan_issue_number(plan, state)
        intent = stage_to_intent(stage)
        classification = _classification_with_issue(
            _classification_for_stage(plan, stage),
            issue_number,
        )
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
            meta_callback=meta_callback,
        )
        stage_output = _format_pause_output(result.output, pause)
        if (
            stage == "pr"
            and _checkpoint_status_from_result(result, intent=intent) == "completed"
        ):
            stage_output = _append_thread_summary(thread, issue_number, stage_output)
        outputs.append(f"**{stage}**\n{stage_output}")

        if pause and pause.pause:
            idx = plan.stages.index(stage)
            # Only skip ahead when the stage actually completed (circuit-breaker).
            # Failed/blocked stages must resume on the same index.
            completed = (
                _checkpoint_status_from_result(result, intent=intent) == "completed"
            )
            save_state(thread, {
                "pending_plan": {
                    "plan": plan.to_dict(),
                    "next_stage_index": idx + 1 if completed else idx,
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
    plan = normalize_plan_issue(generate_plan(message, state, issue_context=issue_ctx), state)

    pause = should_pause_for_plan(plan, mode=mode)
    if pause.pause:
        pending_plan = {"plan": plan.to_dict(), "next_stage_index": 0}
        updates: dict = {"pending_plan": pending_plan}
        if plan.issue_number:
            updates["issue_number"] = plan.issue_number
        save_state(thread, updates)
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
    # URL GitHub: /issues/123 (incl. issuecomment links)
    m = re.search(r"github\.com/[^/\s]+/[^/\s]+/issues/(\d+)", message, re.I)
    if m:
        return int(m.group(1))
    # #123 ou issue #123 ou issue 123
    m = re.search(r"(?:issue\s*#?|#)(\d+)", message, re.I)
    if m:
        return int(m.group(1))
    return None


def extract_issue_comment_url(message: str) -> str | None:
    """Return full GitHub issue-comment URL when present in the message."""
    m = _ISSUE_COMMENT_URL_RE.search(message)
    if not m:
        return None
    return m.group(0)


def extract_issue_comment_id(message: str) -> int | None:
    m = _ISSUE_COMMENT_URL_RE.search(message)
    if not m:
        return None
    return int(m.group(2))


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
    """Merge GitHub issue checkpoint into thread state for resume.
    
    Local state_store is authoritative. GitHub checkpoint is read-only to fill gaps.
    Never downgrade local current_stage based on GitHub.
    """
    checkpoint = fetch_last_checkpoint(issue_number)
    if not checkpoint:
        return state
    merged = dict(state)
    stage_idx = current_stage_from_checkpoint(checkpoint, STAGES)
    local_stage = merged.get("current_stage", 0)
    
    # Only update current_stage if GitHub has advanced further than local state
    if stage_idx is not None and stage_idx > local_stage:
        merged["current_stage"] = stage_idx
        log.info(
            "checkpoint resume: advancing from local_stage=%d to GitHub stage=%d",
            local_stage,
            stage_idx,
        )
    elif stage_idx is not None and stage_idx < local_stage:
        log.info(
            "checkpoint resume: local_stage=%d is ahead of GitHub stage=%d — keeping local",
            local_stage,
            stage_idx,
        )
    
    merged["last_checkpoint"] = checkpoint.to_dict()
    if checkpoint.issue_number:
        merged["issue_number"] = checkpoint.issue_number
    log.info(
        "checkpoint sync issue=%s stage=%s status=%s → local_stage=%s",
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
    """Write checkpoint to GitHub as best-effort (don't fail on write error).
    
    Local state_store is the authoritative source of truth; GitHub checkpoint
    is a secondary mirror for cross-session resume.
    """
    stage_name = INTENT_TO_STAGE.get(intent)
    if not stage_name or not issue_number:
        return
    success = write_checkpoint(
        issue_number,
        stage=stage_name,
        status=status,
        task_id=task_id,
        output_summary=output_summary,
    )
    if not success:
        log.warning(
            "GitHub checkpoint write failed for issue=%s stage=%s status=%s — "
            "continuing anyway (local state_store is authoritative)",
            issue_number,
            stage_name,
            status,
        )


def _apply_stage_state(thread: str, classification: dict, state: dict) -> None:
    """Persiste issue_number e current_stage após dispatch.

    current_stage is derived from the canonical STAGES/INTENT_TO_STAGE
    tables (single source of truth) rather than a parallel hardcoded map.
    """
    updates = {}
    if classification.get("issue_number"):
        updates["issue_number"] = classification["issue_number"]
    intent = classification["intent"]
    stage_name = INTENT_TO_STAGE.get(intent)
    if stage_name:
        updates["current_stage"] = STAGES.index(stage_name) + 1
    elif intent == "advance":
        stage_idx = state.get("current_stage", 0)
        updates["current_stage"] = min(stage_idx + 1, len(STAGES))
    if updates:
        save_state(thread, updates)


def _slice_plan_from_index(plan: Plan, start_idx: int) -> Plan:
    """Return `plan` with the first `start_idx` stages already consumed.

    No-op when `start_idx` is 0 (nothing to skip).
    """
    if start_idx <= 0:
        return plan
    return Plan(
        summary=plan.summary,
        stages=plan.stages[start_idx:],
        open_questions=plan.open_questions,
        autonomous_eligible=plan.autonomous_eligible,
        issue_number=plan.issue_number,
    )


def _dispatch_by_action(
    action: Action,
    classification: dict,
    prompt: str,
    thread: str,
    state: dict,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str:
    """The single place that turns an `Action` into an actual dispatch call.

    stage_review always runs through the deterministic verifier gate
    (`_dispatch_review_with_verifier`); every other stage/question dispatches
    directly and finalizes checkpoint/state (`_dispatch_stage_with_finalize`).
    Called from every phase of `route()` that needs to dispatch, so the
    review-vs-stage branch exists in exactly one place.
    """
    if action.kind == "dispatch_review":
        return _dispatch_review_with_verifier(
            classification,
            prompt,
            thread,
            state,
            issue_number=action.issue_number,
            adapter_env=adapter_env,
            progress_log=progress_log,
            meta_callback=meta_callback,
        )
    return _dispatch_stage_with_finalize(
        classification,
        prompt,
        thread,
        state,
        issue_number=action.issue_number,
        intent=action.intent,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )


def _resume_blocked_stage(
    message: str,
    thread: str,
    pending_intent: str,
    provided: int,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str:
    """Retry the blocked stage now that the user supplied `#N`.

    Resumes a pending plan (if any) from where it stopped, forcing the
    freshly-provided issue number; otherwise retries the single blocked
    stage directly.
    """
    save_state(thread, {
        "issue_number": provided,
        "awaiting_issue_number": None,
        "pending_retry_intent": None,
    })
    state = load_state(thread)
    log.info("route: issue #%s fornecida — retentando %s", provided, pending_intent)

    pending_plan = state.get("pending_plan")
    if pending_plan:
        plan = Plan.from_dict(pending_plan.get("plan", {}))
        if plan:
            plan = normalize_plan_issue(
                Plan(
                    summary=plan.summary,
                    stages=plan.stages,
                    open_questions=plan.open_questions,
                    autonomous_eligible=plan.autonomous_eligible,
                    issue_number=provided,
                ),
                state,
            )
            plan = _slice_plan_from_index(plan, int(pending_plan.get("next_stage_index", 0)))
            save_state(thread, {"pending_plan": None})
            return execute_plan(
                plan,
                thread,
                state,
                adapter_env=adapter_env,
                progress_log=progress_log,
                meta_callback=meta_callback,
            )

    clf = {
        "intent": pending_intent,
        "agent": None,
        "model": None,
        "issue_number": provided,
        "fast_track": False,
        "stages": [],
        "needs_confirmation": False,
        "ambiguity_reason": None,
    }
    prompt = build_prompt(clf, message, state)
    action = next_action(state, clf)
    return _dispatch_by_action(
        action,
        clf,
        prompt,
        thread,
        state,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )


def _handle_awaiting_issue_number(
    message: str,
    thread: str,
    state: dict,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str | None:
    """Resume an impl that's blocked waiting for `#N` (has priority over
    pending_plan/pending_confirmation).

    A `#N` reply retries the blocked stage. A bare "sim" is explicitly
    rejected — it must not be mistaken for advancing to review. Any other
    message means this phase doesn't apply, so it returns None and route()
    falls through.
    """
    if not state.get("awaiting_issue_number"):
        return None

    pending_intent = state.get("pending_retry_intent") or "stage_impl"
    provided = extract_issue_from_message(message)

    if provided:
        return _resume_blocked_stage(
            message,
            thread,
            pending_intent,
            provided,
            adapter_env=adapter_env,
            progress_log=progress_log,
            meta_callback=meta_callback,
        )

    if _ADVANCE_ONLY_RE.match(message.strip()):
        return (
            "⏸️ O estágio anterior ficou **bloqueado** — o agente não recebeu "
            "o número da issue.\n"
            "Envie `#N` (ex.: `#376`) para retentar. "
            "`sim` **não** avança para review."
        )

    return None


def _handle_pending_plan(
    message: str,
    thread: str,
    state: dict,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str | None:
    """Approve or cancel a fast-track plan awaiting confirmation.

    Bare "sim" resumes `execute_plan` from `next_stage_index`. Anything else
    discards the pending plan — mutating `state` in place so the next phase
    sees the change — and returns None to fall through to reclassification.
    """
    pending_plan = state.get("pending_plan")
    if not pending_plan:
        return None

    quick = classify(message, {})
    if quick["intent"] == "advance":
        save_state(thread, {"pending_plan": None})
        plan = Plan.from_dict(pending_plan.get("plan", {}))
        if not plan:
            return "❌ Plano pendente inválido — tente novamente."
        state = load_state(thread)
        plan = normalize_plan_issue(plan, state)
        if plan.issue_number:
            save_state(thread, {"issue_number": plan.issue_number})
        plan = _slice_plan_from_index(plan, int(pending_plan.get("next_stage_index", 0)))
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
    return None


def _handle_pending_confirmation(
    message: str,
    thread: str,
    state: dict,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
) -> str | None:
    """Approve or cancel an ambiguous classification awaiting confirmation.

    Bare "sim" dispatches the classification/prompt captured when the
    ambiguity was first detected. Anything else discards the pending
    confirmation — mutating `state` in place — and returns None to fall
    through to reclassification.
    """
    pending = state.get("pending_confirmation")
    if not pending:
        return None

    quick = classify(message, {})
    if quick["intent"] != "advance":
        log.info("route: confirmação cancelada → reclassificando")
        save_state(thread, {"pending_confirmation": None})
        state.pop("pending_confirmation", None)
        return None

    # Usuário confirmou → executa ação pendente
    save_state(thread, {"pending_confirmation": None})
    clf = dict(pending["classification"])
    prompt = pending["prompt"]
    log.info("route: confirmação aceita → intent=%s agent=%s", clf["intent"], clf.get("agent"))

    issue_number = _resolve_issue_number(clf, state, message)
    if issue_number:
        clf["issue_number"] = issue_number
    # Already confirmed by the user — never re-trigger the ambiguity check.
    clf["needs_confirmation"] = False

    action = next_action(state, clf)
    return _dispatch_by_action(
        action,
        clf,
        prompt,
        thread,
        state,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )


def _handle_normal_flow(
    message: str,
    thread: str,
    state: dict,
    *,
    adapter_env: Mapping[str, str] | None = None,
    progress_log: str | None = None,
    meta_callback: callable | None = None,
    mode: str = "interactive",
) -> str | None:
    """Classify the message and dispatch it end to end.

    Runs once every pending-state phase has been ruled out: classifies,
    syncs the GitHub checkpoint, bumps a repeated-but-completed stage,
    remaps bare "sim" to the next pipeline stage, then resolves a single
    `Action` (`decision.next_action`) that drives every remaining branch —
    check_usage/check_config/summarize/fast_track, confirmation, and finally
    stage_review-vs-plain-stage dispatch.
    """
    classification = classify(message, state)

    issue_number = _resolve_issue_number(classification, state, message)
    if issue_number:
        classification["issue_number"] = issue_number
        state = _sync_state_from_checkpoint(state, issue_number)

    last_cp_dict = state.get("last_checkpoint")
    last_checkpoint = Checkpoint.from_dict(last_cp_dict) if last_cp_dict else None
    classification = _maybe_bump_completed_stage_intent(classification, last_checkpoint)
    if classification["intent"] == "advance":
        classification = _remap_advance(classification, state)
    log.info("classification: %s", json.dumps(classification))

    action = next_action(state, classification)

    if action.kind == "check_usage":
        return None

    if action.kind == "check_config":
        return format_routing_summary(_ROUTING_TABLE)

    if action.kind == "summarize":
        summary = write_thread_summary(thread)
        if issue_number:
            _post_issue_comment(issue_number, summary)
        return summary

    if action.kind == "fast_track":
        return _handle_fast_track(
            message,
            thread,
            state,
            mode=mode,
            adapter_env=adapter_env,
            progress_log=progress_log,
            meta_callback=meta_callback,
        )

    prompt = build_prompt(classification, message, state)
    log.info("prompt built: %s", prompt[:120])

    if action.kind == "confirm":
        log.info("route: Gemini pediu confirmação — %s", classification.get("ambiguity_reason"))
        save_state(thread, {"pending_confirmation": {
            "classification": classification,
            "prompt": prompt,
        }})
        return _confirmation_message(classification)

    return _dispatch_by_action(
        action,
        classification,
        prompt,
        thread,
        state,
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )


def _prepare_route_state(
    message: str,
    thread: str,
    clean_message: bool,
    mode: str,
) -> tuple[str, dict]:
    """Clean the incoming message, load thread state, and sync it against
    the GitHub checkpoint when an issue number is already known.

    Returns (message_clean, state) for the phase handlers to consume.
    """
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
    return message_clean, state


# Pending-state phases tried in order before falling back to the normal
# classify-and-dispatch flow. Each returns None when it doesn't apply.
_PENDING_STATE_PHASES = (
    _handle_awaiting_issue_number,
    _handle_pending_plan,
    _handle_pending_confirmation,
)


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
    """Entry point: classifica, confirma se ambíguo, despacha, atualiza estado.

    Thin orchestrator: runs each phase in order and returns the first
    non-None result. Each phase decides for itself whether it applies to
    the current thread state (returning None means "not mine, keep going").
    """
    message_clean, state = _prepare_route_state(message, thread, clean_message, mode)

    phase_kwargs = dict(
        adapter_env=adapter_env,
        progress_log=progress_log,
        meta_callback=meta_callback,
    )

    for handler in _PENDING_STATE_PHASES:
        result = handler(message_clean, thread, state, **phase_kwargs)
        if result is not None:
            return result

    return _handle_normal_flow(message_clean, thread, state, mode=mode, **phase_kwargs)
