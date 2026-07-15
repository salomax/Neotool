---
name: neotool-conventions
description: >
  Cross-cutting conventions that apply to ALL development in the Invistus project,
  regardless of layer — backend, frontend, mobile, or infra. Covers: security
  boundaries (auth enforcement), error propagation, i18n strategy, and patterns
  that span multiple layers. Activate whenever the task involves auth/permissions,
  cross-layer error handling, or internationalization. Also activate alongside
  neotool-kotlin-service or neotool-nextjs-app for any implementation task.
metadata:
  author: invistus
  version: "1.0"
---

# Shared Conventions — Invistus

Conventions that apply to both backend AND frontend. Load reference files as needed:

- Auth, permissions, or security questions → read [references/auth-permissions.md](references/auth-permissions.md)
- Error handling or user feedback questions → read [references/error-handling.md](references/error-handling.md)
- i18n, strings, or localization questions → read [references/i18n-strategy.md](references/i18n-strategy.md)

## Fundamental rule

**Frontend is UX only. Backend is authority.**
Never make security decisions on the frontend. Never assume a frontend permission
check replaces the backend's.
