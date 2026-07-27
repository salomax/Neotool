---
name: neotool-retro
description: >
  Use this skill to run a retrospective after a piece of work (an implementation, a code review, a
  bugfix, a session) to extract concrete improvements to the Invistus SDLC — not just a summary of what
  happened on that one task. Trigger on requests like "retro", "faz uma retro", "o que podemos
  melhorar", "lições aprendidas", "how can we improve", "what should we do differently next time",
  "gera uma retro do que aconteceu", or any explicit request to reflect on process, tooling, or skill
  gaps after completing work. Do not run automatically at the end of every task — only when explicitly
  requested.
metadata:
  author: invistus
  version: "1.0"
---

# Retro — Invistus

Turn friction encountered during real work into durable improvements to the Invistus SDLC: skills,
docs, tooling/config, or persistent memory. A retro is not a summary of what happened — it is a set of
proposed, categorized changes that a maintainer can approve and see applied.

## Non-negotiable rules

1. **Only run when explicitly asked.** Retros triggered automatically after every task turn into noise and dilute signal — this skill is never self-invoked.
2. **Ground every finding in what actually happened in this session** — a real error message, a wasted round trip, a wrong assumption, a convention that held up well. Do not invent generic best-practice advice unconnected to the work just done.
3. **Never silently edit shared artifacts.** Skills, SDD/architecture docs, and `settings.json` are used by other engineers and other agents. Propose the change, categorize it, and get confirmation before writing to any of them. Memory entries scoped to this project are the exception — apply those directly, per the memory system's own rules.
4. **A finding must be actionable** — name the exact file/skill/doc to change and what changes, or say explicitly "this needs a human decision" (e.g. changing a personal global tool's config or behavior).

## Workflow

### 1. Gather the raw material

Look at what actually happened in the current session, or the specific piece of work the user points
to. Sources worth mining:

- Tool calls that failed, were retried, or produced misleading results
- Places where the user corrected an approach, or confirmed one that worked well (candidates for a `feedback` memory)
- Places where a skill's instructions were incomplete, ambiguous, or wrong for something that came up
- Places where a convention was violated because it was not documented anywhere discoverable
- Genuine time/token waste — redundant tool calls, unnecessary subagents, avoidable trial-and-error

If the user references a specific piece of work ("durante o review da #482"), scope the retro to that
work; otherwise scope it to the current session.

### 2. Classify each finding by target

Every finding lands in exactly one bucket:

- **Skill doc** — a `.claude/skills/*/SKILL.md` or its `references/*.md` was missing, ambiguous, or wrong for a case that actually came up. Fix: propose the exact edit.
- **SDD / architecture doc** — a decision or convention lives only in someone's head or in this conversation, not in `docs/`. Fix: propose where it should live and draft it.
- **Tooling / config** — hooks, `settings.json`, CI, or local dev tooling caused friction or could prevent it. Route through the `update-config` skill rather than editing `settings.json` directly.
- **Memory** — a fact about how this user likes to work, a project fact, or a reference worth recalling next time — doesn't belong in a shared doc. Apply directly, following the memory system's own type rules (`user` / `feedback` / `project` / `reference`).
- **Out of scope / human decision** — e.g. a personal global tool's behavior, a third-party bug, an org policy. State it plainly and stop; do not attempt a workaround that oversteps.

### 3. Present findings before applying

Report findings grouped by bucket, each with: what happened (concrete, cite the command/file/exchange),
why it matters, and the proposed fix. For anything touching a shared skill or doc, ask the user which
ones to apply now (`AskUserQuestion` when the set of choices is small and real). Memory writes do not
need to wait for this gate — they follow the memory system's own save rules.

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
