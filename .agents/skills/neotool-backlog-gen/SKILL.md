---
name: neotool-backlog-gen
description: >
  Use this skill to generate a detailed development backlog from a technical solution for a GitHub
  issue in the Invistus project. Trigger on requests like "generate the backlog", "break into tasks",
  "create the tasks", "generate subtasks", "generate backlog for issue #N", "break this into tasks",
  "what tasks do I need", or any request to turn a technical analysis into implementable tasks.
  Always use after a technical solution already exists in the conversation or in the issue comments.
  An issue number is required — ask for it immediately if not provided.
metadata:
  author: invistus
  version: "1.0"
---

# Backlog Generator — Invistus

Generate an executable, ordered, and reviewable backlog from an existing technical solution.

## Non-negotiable rules

1. **All content posted to GitHub must be in English** — task names, comments, headings, labels. No exceptions, regardless of the language used in the conversation.
2. **Issue number is required** — do not proceed without one. If not provided, stop and ask:
   > What is the issue number? e.g. `#42`

## Workflow

### 1. Load the technical solution

Obtain the technical solution from one of these sources, in order:

1. Issue comments via GitHub MCP (look for a `🔍 Technical Analysis` comment)
2. Solution pasted by the user in the current conversation
3. Detailed description provided in the request itself

If no sufficient technical solution exists, stop and ask the user to generate or paste the analysis first.

### 2. Assess scope before breaking into tasks

- If the change fits comfortably in a single delivery, proceed
- If it touches many layers deeply or has multiple independent blocks, propose milestones first

**Heuristic:** deep changes across 3+ layers, several independent parts with clear incremental validation, or clearly more than a day of work across multiple fronts → consider milestones.

If splitting is needed, present this format before proceeding:

```
Large scope detected.

This solution has independent parts that benefit from separate deliveries:

Milestone 1 — [name]: [what's in it and why it comes first]
Milestone 2 — [name]: [what's in it and why it comes next]

Which milestone do you want to detail first?
```

### 3. Load the project conventions

Read the relevant local skills before listing tasks:

- Kotlin/Micronaut backend → `neotool-kotlin-service`
- Web/mobile frontend → `neotool-nextjs-app`
- Personal finance domain → `neotool-finance-domain`
- Cross-cutting rules → `neotool-conventions`

Do not invent generic conventions if a local skill covers the topic.

### 4. Generate the backlog

Break the solution into small, clear, and reviewable tasks. Rules:

- Keep each task small enough for isolated implementation and review
- Order by real dependency
- Name tasks in imperative form, specific to the change
- Include a one-line rationale
- Avoid vague tasks like "adjust backend" or "fix frontend"

**Default ordering for backend → frontend features:**

1. Database migration (if any)
2. Domain and entity changes
3. Repositories
4. Services and business rules
5. GraphQL schema
6. GraphQL resolvers
7. Frontend operations and codegen
8. Frontend hooks
9. Components and pages
10. Tests where appropriate

For refactors or infra, order from most isolated and safe to most risky.

### 5. Treat tests as part of the backlog

Do not push tests to the end as a separate phase. Generate test tasks alongside implementation tasks.

- `test:unit` backend: every new or changed business rule in a service
- `test:integration` backend: new GraphQL/HTTP flow, relevant query, or significant data flow
- `test:unit` frontend: hook with non-trivial logic or component with important conditional rendering
- `test:e2e`: relevant end-to-end user flow — not for every small change

If a task is purely mechanical (e.g., running codegen, adding a migration with no logic), do not invent a test task just for formality.

**For multi-step flows, split "accepts creation" from "is usable in the next hop".** If the technical solution describes a sequence such as register → authorize, create → fetch, issue token → call downstream service, or save config → boot service, the backlog must not stop at the first successful step. Generate acceptance-shaped tasks/tests that prove both:

- the artifact is created/accepted successfully at step N
- the created artifact is actually usable at step N+1 on the real path

Issue #488 is the model case: proving that DCR returned 201 was not enough; the backlog also needed an explicit follow-up assertion that a client registered without `scope` / `resources` could then complete `/oauth2/authorize` with the defaulted values.

### 6. Post on the issue

Publish the backlog as a comment on the issue. Use the following format:

```markdown
## 📋 Development Backlog

> Based on the technical analysis. [Milestone N/N: <name>] (omit if no milestones)

### Backend

- [ ] `[feat]` [Task name] — [short rationale]
- [ ] `[test:unit]` [Test task name]
- [ ] `[feat|fix|refactor|chore]` [Next task]
- [ ] `[test:integration]` [Integration test, if applicable]

### Frontend

- [ ] `[feat|fix|refactor|chore]` [Task name]
- [ ] `[test:unit]` [Hook/component test]
- [ ] `[test:e2e]` [End-to-end flow, if applicable]

### Infra / Workers

- [ ] `[feat|fix|refactor|chore]` [Task name]

---

**Total estimate:** XS–M per task · ~[N] tasks · [total effort]

**Implementation order:** migration → domain → service → resolver → frontend → tests

*Generated by neotool-backlog-gen*
```

After commenting, inform the user how many tasks were generated and which layers were covered.

## Quality principles

Write tasks for both humans and agents. Each item must make sense without forcing the reader to re-read the entire technical analysis. Prefer project conventions over generic boilerplate. Do not explain in the task how to implement a pattern already covered by a local skill — use the task to say what needs to be delivered. Treat milestones as a deliverability recommendation, not a blocker.

When a flow has more than one externally visible hop, acceptance criteria and test tasks should read like a chain, not isolated fragments: "request succeeds" and "result is usable in the next step" are separate deliverables unless the first test already proves both on the real path.
