---
name: neotool-solution-design
description: >
  Use this skill to produce a structured technical analysis for a GitHub issue in the Invistus
  project before implementation begins. Trigger on requests like "analyze the issue", "design the
  solution", "solution design for issue #N", "how would you implement this", "technical proposal",
  "analyze issue #N", or any request to turn an issue into a defensible technical plan.
  Use before generating a backlog or starting to code when the solution is not yet defined.
  An issue number is required — ask for it immediately if not provided.
metadata:
  author: invistus
  version: "1.5"
---

# Solution Design — Invistus

Produce an objective technical proposal, grounded in the actual Invistus codebase and ready for review.

The output of this skill must be good enough to become an issue comment with a `🔍 Technical Analysis`
heading and serve as direct input for `neotool-backlog-gen`.

## Non-negotiable rules

1. **All content posted to GitHub must be in English** — comments, headings, task names, labels. No exceptions, regardless of the language used in the conversation.
2. **Issue number is required** — do not proceed without one. If not provided, stop and ask:
   > What is the issue number? e.g. `#42`
3. **Every factual claim about the code is grounded, not assumed.** When the analysis states what the code does, it must be tagged with its source — `verified in File.kt:123` — or explicitly marked `assumed`. Reading a method's *signature* is not verification: read its *body*. If a cited method throws, is a stub, caps a value, or returns early, the analysis says so. Most design errors in this project came from citing a signature whose body contradicted the claim (a method that threw `UnsupportedOperationException`; a list path that silently capped; a scope that mapped imprecisely rather than not at all). One grep-and-open per cited symbol is cheaper than one wrong backlog.

   **Read the predicate that selects a branch, not only the branch body.** When the fix lives inside one arm of a conditional, the condition choosing that arm is part of the code under analysis. Issue #492 is the counterexample: the analysis correctly read the OAuth refresh branch and prescribed fail-closed handling for the two fields it named, but never read the selector `if (clientId != null && grantedScopes != null)`. A record with a non-null `client_id` and a null `granted_scopes` therefore fell through to the *first-party* arm and minted a full-permission token — an escalation that reached code review because the predicate was never opened. If your proposal changes behaviour inside a branch, quote its selector in the analysis.

## Workflow

### 1. Read the issue and gather context

Use the GitHub MCP tools to read the issue title, description, labels, state, and comments for history, decisions, and supplementary context.

If the description is ambiguous or incomplete, flag this explicitly in the analysis instead of filling gaps with assumptions.

### 2. Map the impact on the actual codebase

Inspect the local code before proposing a solution. Apply the code-discovery gate in
[token-discipline.md](../neotool-conventions/references/token-discipline.md) §2 before any
exploratory Grep/Read. Look for:

- Affected layers
- Similar flows already implemented
- Related GraphQL types, services, repositories, or components
- Existing domain invariants
- Integration points and regression risk

For each symbol you intend to cite (service method, resolver, validator, mapper), open its body — not just its signature. Note anything that changes its observable behaviour: it throws, it is a stub, it caps or coerces, it short-circuits, it reads a claim the caller does not set. These are exactly the facts a backlog built on the analysis will get wrong if the analysis skipped them.

Prefer concrete evidence from the code over abstract opinion.

**Config and env-var changes require framework-level proof.** Before proposing a new YAML placeholder, renamed env var, or config indirection, verify whether the framework already binds the existing property path from environment variables by convention, and treat any currently-working production behavior as evidence against introducing a new binding path. If production or local infra already resolves the value successfully, do not turn "add a placeholder" into a backlog task unless you have explicit proof that the current mechanism is insufficient. When the binding behavior is still uncertain, mark it `assumed` and require a minimal proof step (code, local repro, or infra manifest evidence) before backlog generation. Issue #488 is the counterexample: the analysis correctly focused on `supported-resources`, but the backlog turned an unproven placeholder assumption into implementation work even though production was already using Micronaut's native env-to-property mapping.

**Sequencing across issues.** If this issue *applies* a contract, table, or mapping that another issue *defines* (e.g. a scope→permission table, a shared claim, an error taxonomy), the definer must land first — say so in the implementation order. If this issue must land first for delivery reasons, inject the dependency and record the resulting debt explicitly, so the same contract does not get authored twice in two places. Duplicated authorship (the scope→permission table lived in two issues because of ordering) is a sequencing failure, not a coding one.

**Trust-boundary trace (required for any issue labeled `security`, or that moves a value across a service/network boundary — auth, tokens, headers, scopes, provenance, tenant/account binding).** Grounding a symbol in one file is not enough when the value *travels*: the costliest defects on this project were not wrong claims inside a file, they were at the seams — a router that silently stripped a provenance header, a `delegated_by` marker asserted by the caller instead of derived from it, a tool gated by the wrong scope, a field whose name did not match the delimiter list. No single file revealed any of these. So for every trust-sensitive value the change relies on, the analysis must trace it end to end and answer, explicitly:

- **Who sets it**, and is that the authenticated/trusted source, or something the caller/client asserts?
- **What transports it** across each hop (JWT claim, HTTP header, GraphQL arg, DB column) — and does anything on the path (Apollo Router propagate allowlist, a subgraph, a mapper) drop, rename, or fail to forward it? The router forwards only `authorization`, `x-request-id`, `traceparent`, `tracestate`, `baggage` — anything else is stripped.
- **Who could forge or strip it**, and what happens if they do — does the system fail *closed* (reject / ignore / default to the safe value) or *open* (trust it)?
- **Where it is enforced** — name the single line/gate that decides, and confirm it is on the trusted side of the boundary.
- **What selects the privileged path, and which sibling fields the selector assumes.** When a persisted record chooses between a broader- and a narrower-permission branch (a `client_id` that means "OAuth grant, scoped", a tenant id, a provenance flag), list the selector field, the sibling fields that branch requires to be well-formed, and what happens when each is null or invalid. "This is an OAuth record" and "this OAuth record is complete" are two decisions; if one missing sibling silently routes the record to the *more* privileged branch, that is the finding. Prescribing fail-closed handling for the fields you happened to notice is not enough — enumerate the whole set.

Write this as a short "Trust-boundary trace" subsection in the analysis. If tracing it reveals the value cannot arrive intact or cannot be made unforgeable on the chosen path, that is a design finding to resolve *now* — not a review finding to discover three rounds later.

**Data-access trace (required whenever the proposal introduces or modifies a function called from a loop, `.map`/`.forEach`, or a per-item/per-field resolver).** N+1 query patterns on this project have consistently survived design and review because each call site looked reasonable in isolation — the function read fine on its own, and only calling it from a loop turned one query into N. Issue #570 is the concrete case: `FundValuationService.estimate()` looked correct read alone, but it fetched come-cotas checkpoints internally via `checkpointRepository.findByHoldingAndDateRange()` on every call, even though the same service already established the batch-once pattern elsewhere (`InvestmentQueryService.loadHoldingTransactionsById()` fetches into a `Map<UUID, List<...>>` once per request). Nothing forced a check that would have caught the inconsistency before merge. So for every function the proposal adds or changes that is invoked per-item (inside a loop, a `.map`, or a resolver called once per parent/field), the analysis must state explicitly:

- **Does it perform I/O** (DB query, HTTP call, cache round-trip)? If not, no further tracing is needed.
- **Is that I/O batched once per request, or does it repeat once per item?** Name the call site and the loop/resolver that invokes it.
- **If it repeats per item, is there already an established batch-fetch pattern in the same service/module** (a sibling method that loads into a `Map<Key, List<...>>` once) that this call should reuse instead of introducing its own per-item fetch?

Write this as a short "Data-access trace" subsection. Omit it only if the change adds no function called from a loop/map/per-item resolver context.

### 3. Load the project conventions

Read the relevant local skills based on the type of change:

- Kotlin/Micronaut backend → `neotool-kotlin-service`
- Web/mobile frontend → `neotool-nextjs-app`
- Personal finance domain → `neotool-finance-domain`
- Cross-cutting rules → `neotool-conventions`

Ground the proposal in these skills. Do not suggest architecture that contradicts local conventions.

### 4. Build the technical solution

Produce a solution that objectively answers: what needs to change, in which layers, in what order, what the main risks are, and how to validate.

Close the proposal with a compact delivery contract, following
[token-discipline.md](../neotool-conventions/references/token-discipline.md) §9:

- Assign stable `AC-N` IDs to objectively testable acceptance criteria.
- Assign stable `D-N` IDs to decisions that constrain implementation.
- Assign stable `A-N` IDs to assumptions that still require verification.
- State the invariants that must remain true across implementation and review, referencing the
  relevant IDs where possible.

Keep each entry short and decision-grade. Do not repeat the problem narrative, impacted-layer
summary, or implementation detail already stated elsewhere. The contract must be usable by any
later role without depending on a particular model, provider, or conversation context.

When more than one viable approach exists, choose the recommended one, document 1–2 short alternatives, and explain why the chosen approach is better for the current context.

### 5. Identify gaps and open questions

If relevant information is missing, list open questions at the end of the analysis. Only stop to ask the user before concluding when the missing answer would prevent a minimally safe proposal. Otherwise, continue with explicit assumptions.

**Before listing an open question about production config, env vars, or deployment state, check whether the answer is already available in a local infra/deployment repo** (e.g. `invistus-flux`) before writing it down as unresolved. If that repo is reachable from the sandbox, grep it for the relevant property/env var name. An unresolved question that could have been answered by inspecting available infra manifests is a design gap, not a legitimate unknown — it lets implementation guess wrong, ship a fix, and only discover the real answer two review rounds later (this happened on issue #488: the analysis correctly flagged the risk around `supported-resources` but left "is this already configured in prod" as an open question, when `invistus-flux/infra/kubernetes/flux/apps/services/security/deployment.yaml` already had the answer).

**If the solution depends on a config path, state both the default path and the override path explicitly.** Example: "default remains `supported-resources: []`; production overrides it through `OAUTH_AUTHORIZATION_SERVER_METADATA_SUPPORTED_RESOURCES` via Micronaut's native mapping." This prevents backlog tasks from inventing a second configuration mechanism when one already exists.

### 6. Post as a comment on the issue

Publish the analysis on the issue. Use the following format:

```markdown
## 🔍 Technical Analysis

### Problem summary
[Short synthesis of what the issue asks for and the expected behavior]

### Current state
- [What exists today in the code]
- [Limitations, inconsistencies, or gaps]

### Technical proposal
1. [First structural change]
2. [Second change]

### Impacted layers
- Backend: [yes/no + which areas]
- Frontend: [yes/no + which areas]
- Database: [yes/no + what changes]
- Infra/Workers: [yes/no + what changes]

### Trust-boundary trace
[Required for `security` / cross-service issues; omit only if the change moves no trust-sensitive value. For each value: who sets it · what transports it across each hop (and whether anything strips/renames it) · who could forge or strip it · fail-closed or fail-open · the single line/gate that enforces it.]

### Data-access trace
[Required whenever the change adds/modifies a function called from a loop, `.map`, or per-item resolver; omit only if no such call site exists. For each such function: does it perform I/O · is it batched once per request or repeated per item · if per-item, does an established batch-fetch pattern already exist nearby that it should reuse instead.]

### Risks and considerations
- [Risk 1]

### Validation strategy
- [Relevant unit test]
- [Relevant integration test]
- [Relevant E2E test, if applicable]

### Delivery contract
**Acceptance criteria**
- `AC-1` [Objectively testable outcome]

**Decisions**
- `D-1` [Implementation constraint or chosen approach]

**Assumptions**
- `A-1` [Unverified premise and how it will be checked]

**Invariants**
- [Invariant that must remain true; reference `AC-N` / `D-N` where relevant]

### Suggested implementation order
1. [Recommended order]

### Open questions
- [Question, if any]

---
*Generated by neotool-solution-design*
```

After commenting, inform the user which layers are impacted and whether the issue is ready for backlog generation.

## Quality criteria

Avoid generic analysis. Every significant recommendation must be anchored in code evidence, a project convention, or an explicit assumption. Prefer incremental solutions before proposing broad refactors. Do not write a detailed backlog here — the goal is to close the technical decision.

A claim tagged `verified in File.kt:line` is worth more than a paragraph of confident prose. When in doubt between stating something as fact and marking it `assumed`, mark it `assumed` — a wrong "fact" propagates into the backlog and the code; a flagged assumption gets checked.
