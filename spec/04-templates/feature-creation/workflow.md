---
title: Feature Development Workflow
type: template
category: feature-creation
status: current
version: 2.0.0
tags: [workflow, feature-development, process]
ai_optimized: true
search_keywords: [workflow, feature-development, process, steps]
related:
  - 04-templates/feature-creation/feature-form.md
  - 04-templates/feature-creation/questionnaire.md
  - 06-workflows/feature-development.md
---

# Feature Development Workflow

> **Purpose**: Step-by-step workflow for creating features using Spec-Driven Development with AI assistance.

## Overview

This workflow guides you through creating features from initial idea to implementation, using structured templates and AI assistance to ensure spec compliance and minimize review cycles.

## Workflow Steps

```
┌─────────────────────────────────────────────────────────────┐
│                    STEP 1: DISCOVERY                        │
│         Complete Feature Questionnaire                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    STEP 2: FORM FILLING                     │
│         Fill Feature Creation Form                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    STEP 3: AI GENERATION                    │
│         AI Generates Implementation Plan                    │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    STEP 4: IMPLEMENTATION                   │
│         Cursor AI Builds Feature                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    STEP 5: VALIDATION                       │
│         Complete Feature Checklist                          │
│         Code Review                                         │
│         Merge                                               │
└─────────────────────────────────────────────────────────────┘
```

## Step 1: Discovery - Complete Feature Questionnaire

### Purpose
Discover and document all requirements for the feature through structured questions.

### Actions
1. Copy the questionnaire template: `04-templates/feature-creation/questionnaire.md`
2. Fill out the questionnaire
3. Review for completeness

### Output
- ✅ Completed questionnaire

### Next Step
Proceed to Step 2: Fill feature form

## Step 2: Form Filling - Fill Feature Creation Form

### Purpose
Create detailed feature specification in structured format.

### Actions
1. Copy the feature form: `04-templates/feature-creation/feature-form.md`
2. Fill out the form with detailed requirements
3. Reference the completed questionnaire
4. Review for completeness

### Output
- ✅ Completed feature form

### Next Step
Proceed to Step 3: AI generation

## Step 3: AI Generation - Generate Implementation Plan

### Purpose
AI generates comprehensive implementation plan from the feature form.

### Actions
1. Provide completed feature form to AI
2. Use AI prompt: `04-templates/ai-prompts/generate-plan.md`
3. AI generates implementation plan with:
   - Complete artifact checklist
   - Implementation phases
   - Exact file paths
   - Spec references

### Output
- ✅ Implementation plan
- ✅ Artifact checklist

### Next Step
Proceed to Step 4: Implementation

## Step 4: Implementation - Build Feature

### Purpose
Implement the feature following the approved plan using AI assistance.

### Actions
1. Use implementation plan as guide
2. For each artifact, use appropriate AI prompts:
   - Backend: `04-templates/ai-prompts/create-backend.md`
   - Frontend: `04-templates/ai-prompts/create-frontend.md`
3. Follow implementation phases:
   - Phase 1: Backend (if applicable)
   - Phase 2: Schema & Types
   - Phase 3: Frontend (if applicable)
   - Phase 4: Integration
4. Check off artifacts as completed

### Output
- ✅ All artifacts implemented
- ✅ All tests passing
- ✅ Code follows spec patterns

### Next Step
Proceed to Step 5: Validation

## Step 5: Validation - Review & Merge

### Purpose
Validate feature completeness and quality before merging.

### Actions
1. Complete feature checklist: `07-validation/feature-checklist.md`
2. Run validation:
   - Pre-commit hooks
   - Linting
   - Type checking
   - Tests
   - Schema validation
3. Code review using: `07-validation/code-review-checklist.md`
4. Address review feedback
5. Merge

### Output
- ✅ Feature checklist completed
- ✅ All validations passing
- ✅ Code review approved
- ✅ Feature merged

## Quick Reference

### Templates Used
- Step 1: Feature Questionnaire
- Step 2: Feature Form
- Step 3: Generate Plan Prompt
- Step 4: Backend/Frontend Feature Prompts
- Step 5: Feature Checklist, Code Review Checklist

### Key Commands
```bash
# Schema sync
./neotool graphql sync

# Schema validation
./neotool graphql validate

# Generate types
npm run codegen

# Run tests
npm test
./gradlew test
```

## Related Documentation

- [Feature Development Workflow](../06-workflows/feature-development.md) - Detailed workflow
- [Feature Form](./feature-form.md) - Feature form template
- [Feature Questionnaire](./questionnaire.md) - Questionnaire template

