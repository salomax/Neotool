# Skill: Create Feature Specification (Adaptive)

**Trigger:** User says "create spec", "new spec", "/spec-create", or asks to create a specification

## Purpose

Help the user create a minimal, focused specification for any type of development work using the adaptive Spec-Driven Development approach.

## Core Principle

**Specification defines WHAT and WHY. Codebase shows HOW.**

## What This Skill Handles

This skill adapts to different types of work:
- Full-stack features
- Backend only (services, entities, GraphQL)
- Frontend only (components, pages, hooks)
- Database schema/migrations
- GraphQL schema
- Refactoring/migrations
- Single components
- Design implementations

## Instructions

Follow the complete instructions in `.cursor/commands/spec-create.md`

## Quick Process

1. **Accept flexible input** - Freeform description, image, requirements paste, module path
2. **Detect request type** - Analyze what's being built
3. **Ask targeted questions** - Only relevant questions based on type
4. **Auto-suggest integration points** - Search codebase for similar code
5. **Generate type-specific spec** - Use appropriate template
6. **Confirm and create** - Show summary, create spec file

## Key Features

- **Adaptive questioning** - Not all requests need all questions
- **Auto-detection** - Understands intent from description
- **Smart suggestions** - Finds similar code automatically
- **Type-aware templates** - Different specs for different needs
- **Minimal output** - Only what's needed, no more

## Examples

**Full feature:**
```
You: create spec for team management with invitations and roles

Claude: [Detects full-stack feature]
        [Asks targeted questions]
        [Creates spec/features/teams/team-management.md]
```

**Backend only:**
```
You: create spec for GraphQL mutations on existing team entities

Claude: [Detects backend-only GraphQL]
        [Asks API-specific questions]
        [Creates spec/features/teams/team-graphql-api.md]
```

**Component:**
```
You: create spec
     [Uploads card design screenshot]

Claude: [Detects design implementation]
        [Analyzes image]
        [Asks component-specific questions]
        [Creates spec/features/shared/action-card.md]
```

**Refactoring:**
```
You: create spec to migrate UserService to use new JWT library

Claude: [Detects refactoring]
        [Asks migration-specific questions]
        [Creates spec/features/security/jwt-migration.md]
```

## Success Indicators

Spec created with:
- ✅ Clear type identification
- ✅ Relevant questions only
- ✅ Integration points (code to explore)
- ✅ Success criteria (measurable)
- ✅ Minimal, focused content

## Next Step

Tell user:
```
✅ Spec created: spec/features/[module]/[slug].md

To implement: "implement spec [slug]"
```

## Notes

- Always detect type before asking questions
- Auto-suggest similar code from codebase
- Keep specs minimal - just WHAT and WHERE to look
- Don't explain HOW - let code show patterns
- Module and feature slug are always required
