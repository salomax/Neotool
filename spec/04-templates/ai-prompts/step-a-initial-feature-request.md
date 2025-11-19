---
title: Step A: Initial Feature Request Prompt
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, feature-creation, initial-request]
ai_optimized: true
search_keywords: [ai-prompt, feature, initial, request, discovery]
related:
  - 04-templates/ai-prompts/step-b-generate-form-and-feature-file.md
---

# Step A: Initial Feature Request Prompt

> **Purpose**: Developer enters a simple feature request. AI generates Feature Form and feature file.

## Usage

Developer provides a simple feature request with scenarios and rules. AI generates both the Feature Form and the feature file in docs.

## Prompt Template

```
Create feature [FEATURE_NAME]. 

The scenarios are:
- [Scenario A description]
- [Scenario B description]
- [Scenario C description]

The rules are:
- [Rule X description]
- [Rule Y description]
- [Rule Z description]

[Additional context if needed]
```

## Example

```
Create feature signup. 

The scenarios are:
- User signs up with email and password
- User signs up with Google OAuth
- User signs up with invalid email format

The rules are:
- Email must be unique
- Password must be at least 8 characters with uppercase, lowercase, number and special character
- User must verify email before accessing the app
```

## AI Task

After receiving this prompt, AI should:
1. Generate a complete Feature Form (spec/04-templates/feature-creation/feature-form.md) based on the feature request
2. Generate a feature file in Gherkin format (docs/features/[category]/[feature-name].feature) following the pattern from docs/features/authentication/signin.feature
3. Reference the spec to ensure completeness
4. Save both files for manual review

## Output Files

- Feature Form: [location to be determined or shown to user]
- Feature File: docs/features/[category]/[feature-name].feature

