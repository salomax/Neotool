---
title: ADR-0012 Web Analytics Tooling
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - web/src/shared/analytics/UmamiAnalytics.tsx
  - web/src/shared/analytics/CloudflareAnalytics.tsx
  - web/src/shared/analytics/umami.ts
  - web/src/app/layout.tsx
load_when:
  - changing web analytics vendors
  - wiring client analytics scripts
supersedes:
schema: docs-metadata-v1
---

# ADR-0012: Web Analytics Tooling

Purpose: record the durable decision and consequences for web analytics tooling.

## Status

accepted

## Context

Legacy ADR-0012 selected Umami + Microsoft Clarity. Current web code does not implement Clarity.

## Decision

**Accepted with amended tooling.** Use:

- **Umami** (self-hosted) as the primary product analytics script (`UmamiAnalytics`, `umami.ts`).
- **Cloudflare Web Analytics** as the additional beacon (`CloudflareAnalytics`).

Do not treat Microsoft Clarity as current authority; it is not present under `web/src/shared/analytics/`.

## Consequences

- Decision record kept; consequences updated to match code.
- Heatmaps/session-replay remain an open product choice if needed later.

## Evidence

- `layout.tsx` mounts `UmamiAnalytics`.
- `CloudflareAnalytics.tsx` gated by `NEXT_PUBLIC_CF_WEB_ANALYTICS_*`.
- No Clarity components/env keys found in web analytics code.

## Related

- [README](README.md)
- 
