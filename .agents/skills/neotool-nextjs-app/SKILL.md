---
name: neotool-nextjs-app
description: >
  Implementation patterns for frontend features in the Invistus project.
  Use when building React components, pages, hooks, or any frontend feature —
  web or mobile. Covers: Apollo/GraphQL integration with generated types,
  mutations with mandatory feedback, MUI theme tokens, i18n with next-intl,
  and Vitest/Playwright tests.
  Triggers: creating components, implementing pages, adding queries or mutations,
  styling with MUI, writing frontend tests, or any task described as frontend,
  web, UI, component, page, form, or mobile app.
metadata:
  author: invistus
  version: "1.0"
  paths: "invistus/web/src/**, invistus/mobile/src/**"
---

# Next.js App Patterns — Invistus

Stack: **Next.js 14+ App Router + TypeScript + Apollo Client + MUI v5 + Zustand + next-intl**

Load reference files based on the task:

- Fetching data with Apollo/GraphQL → [references/graphql.md](references/graphql.md)
- Executing mutations (create/update/delete) → [references/mutations.md](references/mutations.md)
- Styling components with MUI → [references/styling.md](references/styling.md)
- Working on responsive web/mobile behavior → [references/responsiveness.md](references/responsiveness.md)
- Writing tests → [references/tests.md](references/tests.md)

## Non-negotiable rules

1. **Zero hardcoded colors** — use MUI theme tokens only (see references/styling.md)
2. **Generated types are mandatory** — import from `@/lib/graphql/operations/*.generated.ts`
3. **Mutations use `useMutationWithRefetch`** — never `useMutation` directly
4. **Toast is mandatory** — every mutation must show success/error feedback to the user
5. **XSS** — never `dangerouslySetInnerHTML` without DOMPurify
6. **i18n** — zero hardcoded pt-BR/en strings in JSX
7. **TypeScript strict** — no `any` unless absolutely unavoidable
8. **Mobile is not squeezed desktop** — in dense operational screens, redesign hierarchy, actions, and edit flows for mobile instead of merely reflowing desktop UI

## Directory structure

```
web/src/
  app/                  # Next.js App Router (route groups)
  features/             # modules per feature
    [feature]/
      components/       # feature-specific components
      hooks/            # feature hooks
      widgets/          # composed UI blocks
  shared/
    components/ui/      # primitives, forms, patterns, feedback
    hooks/              # shared hooks
  lib/
    graphql/operations/ # generated types (*.generated.ts) — do not edit manually
  styles/themes/        # design tokens (tokens.ts)

mobile/src/
  features/             # mobile feature modules
  shared/               # shared mobile components
```

**Rule:** never create files outside these directories.

## Pre-completion checklist

- [ ] Zero hardcoded colors
- [ ] GraphQL types are generated (no raw `gql`)
- [ ] Mutations use `useMutationWithRefetch`
- [ ] Success and error toasts added
- [ ] TypeScript compiles (no unjustified `any`)
- [ ] Strings use i18n keys
- [ ] No `dangerouslySetInnerHTML` without DOMPurify
- [ ] Mobile behavior was reviewed explicitly instead of assuming the desktop layout works on narrow viewports
- [ ] `sticky`/`fixed` layers were mapped with hierarchy, top offset, opaque background, and divider when needed
- [ ] Mobile-specific fixes were implemented at feature/page composition level before changing shared primitives
- [ ] Repeated typography, spacing, and sizing values were promoted to tokens/constants when reused across multiple points

## Also load

For cross-cutting concerns (auth, errors, i18n): activate **neotool-conventions**.
