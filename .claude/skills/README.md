# Claude Code Skills for Spec-Driven Development

This directory contains Claude Code skills that automate the **new** Spec-Driven Development (SDD) workflow.

## What are Skills?

Claude Code skills are similar to Cursor commands - they provide specialized workflows that Claude Code can execute when triggered.

## Available Skills

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `spec-create` | "create spec", "/spec-create" | Create a new feature specification |
| `spec-implement` | "implement spec [slug]" | Implement feature from spec |
| `spec-validate` | "validate spec [slug]" | Validate implementation |

## Usage

### In Claude Code Terminal

```bash
# Start Claude Code
claude

# Use skills with natural language
You: create spec for user profile management
Claude: [Runs spec-create skill]

You: implement spec user-profile-management
Claude: [Runs spec-implement skill]

You: validate spec user-profile-management
Claude: [Runs spec-validate skill]
```

### With Slash Commands

```bash
# Alternative syntax
/spec-create
/spec-implement user-profile-management
/spec-validate user-profile-management
```

## Complete Workflow Example

```
You: Hi Claude, I need to build a user profile feature

Claude: Great! Let me help you create a spec.
        Say "create spec" when ready.

You: create spec

Claude: I'll help you create a feature specification.
        [Asks questions about feature]

You: [Answers questions]

Claude: ✅ Spec created: spec/features/user-profile-management.md
        To implement, say: implement spec user-profile-management

You: implement spec user-profile-management

Claude: [Reads spec, explores code, implements feature]
        ✅ Implementation complete!
        To validate, say: validate spec user-profile-management

You: validate spec user-profile-management

Claude: [Runs tests, checks criteria, validates quality]
        ✅ Validation passed! Ready for code review.
```

## Skill Details

See individual skill files for detailed instructions:
- `spec-create.md` - Feature spec creation
- `spec-implement.md` - Feature implementation
- `spec-validate.md` - Implementation validation

## Cursor vs Claude Code

Both tools support similar workflows:

| Feature | Cursor | Claude Code |
|---------|--------|-------------|
| **Automation** | Commands (`.cursor/commands/`) | Skills (`.claude/skills/`) |
| **Trigger** | `/command-name` | Natural language or `/skill-name` |
| **Interface** | IDE chat | Terminal |
| **File ops** | Inline editing | File write/read tools |

**Recommendation:** Use whichever tool you prefer - both follow the same SDD workflow!

## Prerequisites and Boundaries

- Run skills from repo root with dependencies installed; skills do not install or start infra.
- Specs are written to `spec/features/<slug>.md`; keep working tree clean or track edits.
- Point to clean, tested integration points; avoid legacy/experimental paths.
- Skills do not auto-run lint/build/tests—use `/spec-validate` then project test commands as needed.
- If output looks wrong, refine spec paths/success criteria and rerun instead of forcing manual mid-skill tweaks.

## Resources

- **Workflow Guide:** `docs/workflows/sdd-workflow.md`
- **Complete Example:** `docs/workflows/sdd-walkthrough-example.md`
- **Spec Templates:** `spec/templates/`
- **Cursor Commands:** `.cursor/commands/` (equivalent functionality)

---

**Note:** These skills reference the full instructions in `.cursor/commands/` to avoid duplication. Both Cursor and Claude Code follow the same new SDD approach.
