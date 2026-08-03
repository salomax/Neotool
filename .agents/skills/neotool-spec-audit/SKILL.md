---
name: neotool-spec-audit
description: >
  Use this skill in a fresh session, right after `neotool-close` has updated `spec/features` for a
  delivered issue, to check whether the durable spec entry actually matches what the issue asked for
  and what was delivered. It is a lightweight, per-delivery faithfulness check — not a full benchmark —
  that catches gaps (issue asked for something the spec omits), inventions (spec claims something the
  issue/diff never established), and stale or broken evidence citations. Trigger on requests like
  "audit the spec for issue #N", "spec audit #N", "confere se o spec bate com a issue", "valida o close
  da issue #N", "post-close check", "spec faithfulness check", or any request to verify that a spec
  update matches an issue after closeout. An issue number is required — ask for it immediately if not
  provided.
metadata:
  author: invistus
  version: "1.0"
---

# Spec Audit — Invistus

Check, from a fresh session with no memory of the implementation, whether a `spec/features` entry
updated by `neotool-close` is a faithful account of what an issue asked for and what was actually
delivered. This is the cheapest possible regression check for the spec-as-agent-memory model: it turns
every closed issue into a small, real test case instead of requiring a synthetic benchmark.

## Non-negotiable rules

1. **Issue number is required.** If not given, ask before proceeding.
2. **Read the spec before the ground truth.** Produce the "spec claims" summary (step 2) before opening
   the issue or the diff (step 3). Reading them in the other order lets the issue silently shape what
   you notice in the spec, which defeats the point of the check.
3. **This skill reports; it does not edit.** Findings go in the audit output. Applying a fix to
   `spec/features` is a separate, explicit action the user approves afterward — do not silently rewrite
   the spec while auditing it.
4. **Every finding must cite something concrete.** A spec line/section plus an issue line, acceptance
   criterion, or diff path it disagrees with. No vague "seems incomplete."
5. **Keep it cheap.** Read only the spec file(s) touched for this delivery, the issue, and the PR diff
   (stat-level is enough unless a specific claim needs verification). Do not reload the whole `spec/`
   graph or unrelated domain files.

## Workflow

### 1. Identify scope

- Get the issue number from the user.
- Find the `spec/features/**` file(s) `neotool-close` updated for this issue (git log on `spec/features`
  around the close date, or ask the user for the path if ambiguous).

### 2. Blind read — spec claims

Read only the spec file(s) identified above and write a short structured summary, without opening the
issue or diff yet:

```markdown
## Spec claims
- Behavior: [what the spec says was implemented]
- Invariants: [what it says must be preserved]
- Impact surface: backend / graphql / database / tests / frontend / mobile (per `evidence:`)
- Permissions: backend enforcement vs UX hints, as stated
```

### 3. Ground truth — issue and diff

Now load the ground truth:

```bash
gh issue view <N>
gh pr view <N> --json files,body   # or: git log --stat for the merge commit
```

Extract: title, description, acceptance criteria, key decision comments, and the actual changed files.

### 4. Compare

Produce a gap table, each row falsifiable against something quoted or cited in step 2/3:

```markdown
## Audit result — Issue #<N>

**Verdict:** Pass | Pass with gaps | Fail

### Gaps (issue asked for it, spec doesn't mention it)
- [criterion / decision] — not reflected in [spec section]

### Inventions (spec claims it, issue/diff doesn't support it)
- [spec claim] — no evidence in issue or diff

### Evidence check (paths cited in `evidence:` vs the actual diff)
- [path] — exists / missing / not in this PR's diff

### Invariant check
- [invariant] — supported by [test/code] / unverifiable from available evidence
```

### 5. Report

Give a verdict and, if not a clean Pass, a short punch list of exact edits a maintainer could apply to
the spec in minutes (file, section, what to add/remove/correct). Do not apply the edits yourself unless
the user asks you to in a follow-up step.

If the same gap type shows up across two or more independently audited issues, say so explicitly and
suggest routing it to `neotool-retro` as a candidate for a durable process fix (e.g. `neotool-close`
consistently missing a section) — do not fix the process yourself from inside this skill.

## Quality criteria

A good audit is falsifiable and cheap: it names the exact spec line and the exact issue/diff fact it
conflicts with, reads only what this single delivery needs, and ends in a verdict a maintainer can act
on without re-reading the whole issue thread. Prefer flagging one clearly evidenced gap over a long list
of speculative ones.
