---
title: ADR-0016 OAuth Login and Consent UI Rendering
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/security/src/main/resources/templates/oauth2-login
  - service/kotlin/security/src/main/resources/templates/oauth2-consent
  - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2LoginController.kt
  - service/kotlin/security/src/main/kotlin/io/github/salomax/neotool/security/http/OAuth2ConsentController.kt
load_when:
  - changing OAuth login/consent UX home
  - debugging authorize redirect UI
supersedes:
schema: docs-metadata-v1
---

# ADR-0016: OAuth Login and Consent UI Rendering

Purpose: record the durable decision and consequences for oauth login and consent ui rendering.

## Status

accepted

## Context

OAuth authorize flows need login and consent HTML. Hosting them in the product SPA would put credentials on a public-client origin.

## Decision

Render login and consent server-side in the `security` service on the auth origin. Keep the Next.js app out of credential collection and consent.

## Consequences

- Same-origin session cookies and clearer trust boundary.
- Separate, simpler template/i18n stack inside security.

## Evidence

- Templates under `resources/templates/oauth2-login` and `oauth2-consent`.
- `OAuth2LoginController` and `OAuth2ConsentController` exist.

## Related

- [README](README.md)
- [ADR-0015](0015-mcp-tool-surface-and-agent-threat-model.md)
