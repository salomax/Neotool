---
title: Create Feature AI Prompt
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, feature-creation, template]
ai_optimized: true
search_keywords: [ai-prompt, feature, creation, template]
related:
  - 04-templates/feature-creation/feature-form.md
  - 04-templates/ai-prompts/create-backend.md
  - 04-templates/ai-prompts/create-frontend.md
---

# Create Feature AI Prompt

> **Purpose**: Main AI prompt template for creating complete features from feature form.

## Usage

1. Fill out the [Feature Form](../feature-creation/feature-form.md)
2. Copy this prompt template
3. Replace placeholders with feature form data
4. Provide to AI (Cursor, ChatGPT, etc.)

## Prompt Template

```
Create a complete feature for NeoTool following the specification.

## Feature Requirements

[Paste completed feature form here]

## Specification References

- Architecture: spec/00-core/architecture.md
- Technology Stack: spec/00-core/technology-stack.md
- Rules: spec/01-rules/
- Patterns: spec/03-patterns/
- Examples: spec/05-examples/

## Implementation Requirements

1. Follow all rules in spec/01-rules/
2. Use patterns from spec/03-patterns/
3. Reference examples from spec/05-examples/
4. Ensure type safety end-to-end
5. Include comprehensive tests
6. Follow coding standards

## Deliverables

- [ ] Backend implementation (if needed)
- [ ] Frontend implementation (if needed)
- [ ] Database migrations (if needed)
- [ ] GraphQL schema (if needed)
- [ ] Tests (unit, integration, E2E)
- [ ] Documentation

Generate the complete implementation following NeoTool patterns and best practices.
```

