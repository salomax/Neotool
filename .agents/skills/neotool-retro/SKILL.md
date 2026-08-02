---
name: neotool-retro
description: >
  Use this skill to run a retrospective after a piece of work (an implementation, a code review, a
  bugfix, a session) to extract concrete improvements to the Invistus SDLC — not just a summary of what
  happened on that one task. Also use it when the user explicitly wants to tighten a requirement
  artifact such as an issue draft, spec, backlog item, or refinement notes by questioning whether the
  content has irrelevant detail, shallow or ambiguous points, missing decisions that may cause rework,
  or a presentation format that could be more assertive or token-efficient. Trigger on requests like
  "retro", "faz uma retro", "o que podemos melhorar", "lições aprendidas", "how can we improve",
  "what should we do differently next time", "gera uma retro do que aconteceu", "improve this issue",
  "review this draft", "is this too vague", or any explicit request to reflect on process, tooling,
  skill gaps, or the quality of a work artifact. Do not run automatically at the end of
  every task — only when explicitly requested.
metadata:
  author: invistus
  version: "1.1"
---

# Retro — Invistus

Turn friction encountered during real work into durable improvements to the Invistus SDLC: skills,
docs, tooling/config, or persistent memory. A retro is not a summary of what happened — it is a set of
proposed, categorized changes that a maintainer can approve and see applied.

This skill supports two explicit modes:
- **Work retro** — after implementation, review, bugfix, or a session, extract process improvements.
- **Artifact retro** — review a requirement artifact such as an issue/spec/backlog draft and improve it as durable project memory.

## Non-negotiable rules

1. **Only run when explicitly asked.** Retros triggered automatically after every task turn into noise and dilute signal — this skill is never self-invoked.
2. **Ground every finding in real material.** For work retros, use what actually happened in the session. For artifact retros, use the actual draft and any decisions already made in the thread. Do not invent generic best-practice advice detached from the concrete work artifact.
3. **Never silently edit shared artifacts.** Skills, SDD/architecture docs, and `settings.json` are used by other engineers and other agents. Propose the change, categorize it, and get confirmation before writing to any of them. Memory entries scoped to this project are the exception — apply those directly, per the memory system's own rules.
4. **A finding must be actionable** — name the exact file/skill/doc to change and what changes, or say explicitly "this needs a human decision" (e.g. changing a personal global tool's config or behavior).
5. **Promote repeated friction, not coincidences.** When the same friction is confirmed in two
   independent issues or sessions, propose a durable change in a shared skill/reference, ADR/SDD,
   or automation. A first confirmed case may be promoted immediately only when recurrence could
   cause a critical security, integrity, or data-loss risk. Keep issue-specific lessons on the
   issue; do not promote local implementation details or one-off coincidences.

## Workflow

### 1. Gather the raw material

First identify which mode applies:

- **Work retro**: the user is asking about what happened during execution, review, bugfix, or a session.
- **Artifact retro**: the user is asking to improve a requirement artifact before it becomes source-of-truth memory.

For work retro, look at what actually happened in the current session, or the specific piece of work the user points to. Sources worth mining:

- Tool calls that failed, were retried, or produced misleading results
- Places where the user corrected an approach, or confirmed one that worked well (candidates for a `feedback` memory)
- Places where a skill's instructions were incomplete, ambiguous, or wrong for something that came up
- Places where a convention was violated because it was not documented anywhere discoverable
- Genuine time/token waste — redundant tool calls, unnecessary subagents, avoidable trial-and-error

If the user references a specific piece of work ("durante o review da #482"), scope the retro to that work; otherwise scope it to the current session.

For artifact retro, inspect the actual draft and any accepted context around it. Mine for:

- Irrelevant information that adds noise without changing implementation
- Ambiguous or shallow statements that a future agent would have to guess
- Missing scope boundaries, assumptions, or refinement decisions
- Acceptance criteria that are not objectively testable
- Presentation choices that waste tokens or weaken clarity
- Omissions likely to cause rework, churn, or repeated clarification later

### 2. Classify each finding by target

Every finding lands in exactly one bucket:

- **Skill doc** — a `.claude/skills/*/SKILL.md` or its `references/*.md` was missing, ambiguous, or wrong for a case that actually came up. Fix: propose the exact edit.
- **SDD / architecture doc** — a decision or convention lives only in someone's head or in this conversation, not in `docs/`. Fix: propose where it should live and draft it.
- **Tooling / config** — hooks, `settings.json`, CI, or local dev tooling caused friction or could prevent it. Route through the `update-config` skill rather than editing `settings.json` directly.
- **Memory** — a fact about how this user likes to work, a project fact, or a reference worth recalling next time — doesn't belong in a shared doc. Apply directly, following the memory system's own type rules (`user` / `feedback` / `project` / `reference`).
- **Artifact quality** — the draft itself needs to be tightened before it becomes durable project memory. Fix: remove noise, clarify ambiguity, strengthen criteria, and restructure the text.
- **Out of scope / human decision** — e.g. a personal global tool's behavior, a third-party bug, an org policy. State it plainly and stop; do not attempt a workaround that oversteps.

### 3. Present findings before applying

Report findings grouped by bucket, each with: what happened (concrete, cite the command/file/exchange),
why it matters, and the proposed fix. For anything touching a shared skill or doc, ask the user which
ones to apply now (`AskUserQuestion` when the set of choices is small and real). Memory writes do not
need to wait for this gate — they follow the memory system's own save rules.

For artifact retro, use this tighter output shape:

```markdown
## Retro assessment
- Signal quality: strong | acceptable | weak
- Main risk: [short sentence]

## Keep
- [High-value information that should remain]

## Cut
- [Irrelevant, repetitive, or low-signal information]

## Clarify
- [Ambiguous, shallow, or underspecified points]

## Restructure
- [Ways to present the same content more clearly or with fewer tokens]

## Revised draft
[Improved version, ready to use]
```

If the artifact is already strong, keep the review brief and say why no major rewrite is needed.

For every proposed promotion, cite the two independent issues/sessions that establish recurrence,
or state the critical security/integrity/data-loss risk that justifies first-case promotion. Choose
the smallest durable target: shared workflow behavior belongs in a skill/reference, architectural
decisions in an ADR/SDD, and reliably detectable repetition in automation. The approval gate for
shared artifacts still applies regardless of promotion threshold.

### 4. Apply approved changes

- **Skill/doc edits**: edit the file directly, matching the target skill's existing frontmatter, section structure, and language (English for anything a non-Portuguese-speaking teammate or agent might read).
- **Tooling/config**: hand off to the `update-config` skill rather than editing `settings.json` inline.
- **Memory**: write immediately in the standard format, linking related entries with `[[name]]`.

### 5. Close the loop

Summarize what changed and where (file paths), what was deliberately left as a human decision, and what
was saved to memory. Do not produce a separate retro report document unless the user asks for one — the
changes themselves are the deliverable.

## Quality criteria

A good retro finding is falsifiable and specific: it names the exact command, file, or exchange that
went wrong, not a vague "communication could be better." Prefer one well-evidenced finding over five
speculative ones. When in doubt about whether something is worth flagging, ask: would this same
friction hit a different agent doing the same task tomorrow? If yes, it is worth fixing. If it was a
one-off fluke, it is not.

For artifact retro specifically, a good result:
- removes noise without erasing decision-grade detail
- makes scope and exclusions explicit
- turns vague goals into testable criteria
- records decisions that would otherwise be lost in chat
- reduces future token cost without making the draft cryptic
