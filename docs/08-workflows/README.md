---
title: Workflows Index
type: workflow
category: index
status: current
version: 2.1.0
tags: [workflows, index]
ai_optimized: true
search_keywords: [workflows, index, spec-driven]
related:
  - 00-overview/principles.md
  - docs/manifest.md
---

# Workflows Index

> **Purpose**: Index of Spec-Driven development workflows optimized for AI-assisted development.

## Core Workflows

- [Feature Development](./feature-development.md) - Complete Spec-Driven feature development workflow from idea to production
- [Code Review](./code-review.md) - Spec-Driven code review process ensuring spec compliance
- [Testing Workflow](./testing-workflow.md) - Spec-Driven testing workflow with coverage requirements
- [Deployment Workflow](./deployment-workflow.md) - Spec-Driven deployment workflow with validation gates

## Context Strategy

- [Spec Context Strategy](./spec-context-strategy.md) - **NEW**: Optimized context loading strategy for AI assistants working with the specification

## Workflow Overview

All workflows follow **Spec-Driven Development (SDD)** principles:

1. **Specification First**: Always reference specification before implementation
2. **Pattern-Based**: Use documented patterns from `docs/05-backend/patterns/`
3. **Template-Driven**: Leverage templates from `docs/91-templates/`
4. **Validation-Focused**: Use checklists from `docs/94-validation/`
5. **Context-Optimized**: Reference only relevant spec sections (see [Spec Context Strategy](./spec-context-strategy.md))

## Quick Navigation

### For Developers
- **Starting a Feature**: [Feature Development](./feature-development.md)
- **Reviewing Code**: [Code Review](./code-review.md)
- **Writing Tests**: [Testing Workflow](./testing-workflow.md)
- **Deploying**: [Deployment Workflow](./deployment-workflow.md)

### For AI Assistants
- **Context Strategy**: [Spec Context Strategy](./spec-context-strategy.md) - **Start here for context optimization**
- **Feature Work**: [Feature Development](./feature-development.md) - Phase-based context loading
- **Code Review**: [Code Review](./code-review.md) - Spec compliance validation

## Examples

- [SWAPI ETL Workflow](../07-examples/batch-workflows/swapi-etl-workflow.md) - Complete example of a batch ETL workflow using Prefect and Kafka

