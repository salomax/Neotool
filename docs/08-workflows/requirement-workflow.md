---
title: Requirement Workflow — Claude-Driven
type: workflow
category: requirement-management
status: current
version: 1.1.0
date: 2026-06-16
tags: [workflow, requirements, github, issues, claude, refinement]
ai_optimized: true
search_keywords: [requirement, issue, github, refinement, acceptance criteria, branch, backlog]
---

# Requirement Workflow — Claude-Driven

> **Philosophy**: Requirements are born from conversations. Claude structures, Marcos refines, GitHub records.

---

## Overview

```
Idea / Problem
     │
     ▼
Claude thread  ──→  Claude fills the template
     │
     ▼
Refinement with Marcos  ──→  scope + acceptance criteria
     │
     ▼
Claude creates the GitHub Issue
     │
     ▼
Claude creates the Branch (GH-N convention)
     │
     ▼
(Optional) Claude generates SDD spec in spec/features/
     │
     ▼
Dev implements
```

---

## Steps in detail

### Step 1 — Capture the requirement (informal)

The requirement can start in any form:
- An idea described informally
- A reported bug
- An improvement identified during use
- A backlog item that needs detailing

**Claude does not need a specific format here.** The conversation is free-form.

**Example thread opening:**
```
"I want to add support for password-protected PDFs in the statement upload.
Sometimes the bank protects the file and the system rejects it without
giving the user a meaningful error message."
```

---

### Step 2 — Claude structures the requirement

Based on the conversation, Claude fills the [GitHub Issue Template](../91-templates/requirement-templates/github-issue-template.md) and presents it for review.

**What Claude does in this step:**
- Identifies context and motivation
- Formulates the requirement objectively
- Proposes verifiable acceptance criteria
- Defines scope (includes / excludes)
- Suggests labels, track, estimated effort
- Proposes branch name

**Claude presents the draft and asks:**
> "Is this scope correct? Are any acceptance criteria missing?"

---

### Step 3 — Refinement

This step is a conversation in the thread. Claude updates the template as decisions are made.

**Typical refinement questions:**

- Is the scope right? What is out of scope?
- Are the acceptance criteria verifiable?
- Are there edge cases to cover?
- Are there dependencies on other issues or [BK-N]?
- What is the priority? (P0/P1/P2/P3)
- Do we need a full SDD spec or is the issue enough?

**At the end of refinement**, the template must be complete and approved by Marcos.

---

### Step 4 — Create the GitHub Issue

With the template approved, Claude creates the issue via **GitHub MCP** (if connected) or generates the `gh` command for local execution.

**Via GitHub MCP:**
```
Claude creates the issue directly with title, body, labels, and assignees.
```

**Via `gh` CLI (fallback):**
```bash
gh issue create \
  --title "[feat] PDF upload with password support" \
  --body "$(cat /tmp/issue-body.md)" \
  --label "feat,P1,track:black" \
  --repo invistus/invistus
```

Claude records the issue number for use in the branch name.

---

### Step 5 — Create the Branch

With the issue number in hand:

```bash
git checkout -b feat/GH-{number}-{slug}
git push -u origin feat/GH-{number}-{slug}
```

**Example:**
```bash
git checkout -b feat/GH-42-pdf-password-support
git push -u origin feat/GH-42-pdf-password-support
```

Claude runs this via Bash or provides the command for the dev to execute.

---

### Step 6 — SDD Spec (when needed)

If the issue is a new feature or a significant behavior change, Claude creates (or suggests creating) an SDD spec:

```bash
# Suggested path
spec/features/{feature-slug}.md
```

The spec follows the [SDD Workflow](../workflows/sdd-workflow.md) format:
- Problem / Solution
- Success Criteria (mirrors the issue acceptance criteria)
- Integration Points (where to explore in the codebase)
- Technical Constraints

**When NOT to write a spec:**
- Simple bug fixes
- Chores / dependency updates
- Minor refactors with an established pattern

---

## Issue states

```
draft (Claude thread)
    │
    ▼
in refinement (Claude thread + Marcos)
    │
    ▼
approved → created on GitHub (status: open)
    │
    ├──→ in progress (branch created, dev working)
    │
    ├──→ in review (PR open)
    │
    └──→ done (PR merged, issue closed)
```

---

## Instructions for Claude — how to behave in this workflow

When Marcos opens a thread describing a requirement, Claude must:

1. **Listen** without interrupting — let Marcos describe freely
2. **Ask** only what is essential if something is ambiguous (one question at a time)
3. **Fill** the template and present it clearly
4. **Refine** iteratively based on feedback
5. **Confirm** before creating the issue: _"Can I create the issue with this content?"_
6. **Create** the issue and the branch
7. **Suggest** an SDD spec if the complexity warrants it

**Tone:** direct, no bureaucracy. The goal is for creating a well-structured requirement to be fast and natural.

---

## Full example

### Opening message
```
Marcos: "I want the app to process password-protected statement PDFs.
Bradesco sometimes sends them that way."
```

### Claude responds with draft
```markdown
## Context
Bradesco statements frequently come password-protected,
preventing automatic processing. The current system rejects these
files without a clear error message.

## Requirement
Support for password-protected PDF uploads: the user provides the
password and the system processes the file normally.

## Expected behavior
- Upload accepts a password-protected PDF when the correct password is provided
- Specific error message when the password is wrong
- PDFs without a password continue working unchanged

## Acceptance criteria
- [ ] Password field appears when a protected PDF is detected
- [ ] PDF is processed successfully when the correct password is provided
- [ ] Clear error (not generic) when password is incorrect
- [ ] PDFs without password processed with no UX change
- [ ] Password is not stored after processing

## Scope
Includes: protected PDF detection, password input, processing
Excludes: PDFs with multiple passwords, edit-only passwords (open password only)

## Track: Black | Type: feat | Effort: S | Services: backend + frontend
## Suggested branch: feat/GH-{N}-pdf-password-support
```

### Refinement
```
Marcos: "Also add: if the user tries 3 times with the wrong password,
show a hint about where to find it."
Claude: [updates acceptance criteria]
```

### Approval
```
Marcos: "OK, go ahead."
Claude: [creates issue #42, creates branch feat/GH-42-pdf-password-support]
```

---

## Related

- [GitHub Issue Template](../91-templates/requirement-templates/github-issue-template.md)
- [Backlog Master](../00-planning/backlog.md)
- [SDD Workflow](../workflows/sdd-workflow.md)
- [Feature Form](../91-templates/feature-templates/feature-creation/feature-form.md)
