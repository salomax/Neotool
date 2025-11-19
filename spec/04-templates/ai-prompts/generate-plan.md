---
title: Generate Implementation Plan AI Prompt
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, planning, implementation-plan]
ai_optimized: true
search_keywords: [ai-prompt, plan, implementation, checklist]
related:
  - 04-templates/feature-creation/feature-form.md
  - 06-workflows/feature-development.md
---

# Generate Implementation Plan AI Prompt

> **Purpose**: AI prompt template for generating implementation plans from feature forms.

## Usage

Use this prompt after completing the feature form to generate a detailed implementation plan.

## Prompt Template

```
Generate a comprehensive implementation plan for a NeoTool feature.

## Feature Form

[Paste completed feature form here]

## Specification References

- Architecture: spec/00-core/architecture.md
- Project Structure: spec/00-core/project-structure.md
- Rules: spec/01-rules/
- Patterns: spec/03-patterns/
- Workflows: spec/06-workflows/

## Requirements

Generate an implementation plan that includes:

1. Complete artifact checklist (all files, tests, schemas)
2. Implementation phases (Backend → Schema/Types → Frontend → Integration)
3. Exact file paths for all artifacts
4. Spec references for each artifact
5. Validation steps
6. Dependencies between artifacts

## Plan Format

- Use markdown format
- Include checkboxes for tracking
- Reference specific spec documents
- Provide exact file paths
- Include implementation order

Generate the complete implementation plan.
```

