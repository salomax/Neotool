---
title: Step B: Generate Feature Form and Feature File
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, feature-creation, form-generation]
ai_optimized: true
search_keywords: [ai-prompt, feature-form, feature-file, gherkin]
related:
  - 04-templates/feature-creation/feature-form.md
  - 04-templates/ai-prompts/step-a-initial-feature-request.md
---

# Step B: Generate Feature Form and Feature File

> **Purpose**: AI prompt to generate Feature Form and feature file from initial feature request.

## Usage

Use this prompt internally by AI after receiving Step A initial feature request.

## Prompt Template

```
Generate a complete Feature Form and feature file for the following feature request:

[Paste initial feature request from Step A]

## Requirements

1. Generate a complete Feature Form following spec/04-templates/feature-creation/feature-form.md
   - Fill all relevant sections based on the feature request
   - Infer missing details from the scenarios and rules provided
   - Reference spec/00-overview/architecture.md for architecture decisions
   - Reference spec/05-standards/ for rules and constraints
   - Reference spec/04-patterns/ for implementation patterns

2. Generate a feature file in Gherkin format following the pattern from docs/03-features/authentication/signin.feature
   - Use proper Gherkin syntax (Feature, Background, Rule, Scenario, Given/When/Then)
   - Include all scenarios from the feature request
   - Include rules as Gherkin Rules
   - Add appropriate tags (@feature-name, @category)
   - Support both @web and @mobile if applicable

3. Save files:
   - Feature Form: [Show to user or save to temp location]
   - Feature File: docs/03-features/[category]/[feature-name].feature

## Specification References

- Architecture: spec/00-overview/architecture.md
- Technology Stack: spec/00-overview/technology-stack.md
- Rules: spec/05-standards/
- Patterns: spec/04-patterns/
- Feature Form Template: spec/04-templates/feature-creation/feature-form.md
- Example Feature File: docs/03-features/authentication/signin.feature

Generate both files now.
```

