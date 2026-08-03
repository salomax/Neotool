---
title: Mobile Expo Standards
type: development
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - mobile/package.json
  - mobile/codegen.ts
  - libs/shared-graphql
load_when:
  - building Expo/React Native screens
  - running mobile tests or codegen
supersedes: []
schema: docs-metadata-v1
---

# Mobile Expo Standards

Purpose: standards for the Expo mobile app under `mobile/`.

## When to load

Load for work under `mobile/`.

## Authority

`mobile/package.json`, mobile `codegen.ts`, and `libs/shared-graphql`.

## Rules

1. Stack is Expo 52 + React Native 0.76 + Apollo + React Native Paper.
2. Prefer shared GraphQL documents/types via `libs/shared-graphql` and mobile codegen.
3. Use Jest (`npm test` in `mobile/`) for unit tests.
4. Keep navigation and native UX in mobile-specific code; do not import web MUI UI.
5. Validate with root `npm run check:mobile` before merge when feasible.

## Commands

```bash
cd mobile
npm start
npm run typecheck
npm run lint
npm test
npm run codegen
```

## Related

- [GraphQL codegen](graphql-codegen.md)
- [ADR-0010](../04-decisions/0010-react-native-expo-mobile.md)
- [ADR-0014](../04-decisions/0014-web-mobile-code-sharing-strategy.md)
- [Testing](testing-strategy.md)
