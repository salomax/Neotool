---
title: ADR-0016 OAuth Login and Consent UI Rendering
type: adr
category: security
status: accepted
version: 1.0.0
tags: [security, oauth, authorization-server, login, consent, mcp, identity-brokering]
related:
  - docs/92-adr/0015-mcp-tool-surface-and-agent-threat-model.md
  - docs/92-adr/0008-interservice-security.md
---

# ADR-0016: OAuth Login and Consent UI Rendering

## Status

Accepted — records the decision behind the login/consent surface of the OAuth 2.1 authorization server (issues #412, #415, #416).

## Context

The OAuth 2.1 authorization server lives in the `security` service. Its authorize flow is a browser-redirect flow: an external agent (Claude, ChatGPT, Gemini) opens a browser to `auth.invistus.com.br/oauth2/authorize`, the user authenticates and consents, and is redirected back to the agent with an authorization code.

Two surfaces must render HTML during that flow: the **login** page and the **consent** page. There are two candidate homes for them:

- **A — Render in the `security` service** (server-rendered templates on the auth origin).
- **B — Reuse the product frontend** (the Next.js web app, `app.invistus.com.br`).

Option B is tempting: the web app owns the design system (MUI), i18n (next-intl), branding, accessibility, and already implements Google sign-in. It would avoid maintaining a second, plainer UI stack (Mustache templates + `.properties` i18n) in the backend.

## Decision

**The login and consent pages are rendered server-side by the `security` service, on the auth origin. The product SPA is never in the credential-collection or consent path.**

### Rationale

1. **Trust boundary.** The password is posted directly to the service that mints tokens, one hop, never transiting a separate origin. The web app is a public OAuth client — precisely the kind of client that must not sit in the credential path.
2. **Same-origin session.** The OAuth2 session cookie is set by `security` on its own origin. Server-rendering keeps it same-origin; reusing the SPA (different origin) reintroduces cross-origin cookie handling, CSRF handoff, and the complexity server-rendering avoids.
3. **Failure-domain independence.** An agent connecting must not depend on the product SPA being deployed and healthy. Auth and product are separate availability domains — the same reasoning that keeps the MCP server separate from the assistant.
4. **Benchmark.** No major authorization server reuses the product's main SPA for login/consent. Keycloak and Spring Authorization Server render server-side; Auth0 hosts its own Universal Login; Ory Hydra pairs with a dedicated login/consent app on the auth domain. The AS owning its own UI is the industry pattern.

### The Google sign-in consequence

Because the login page is server-rendered by the AS, federated ("sign in with Google") users cannot authenticate through the product SPA's existing client-side Google flow, which issues *app* tokens rather than an *authorization code*. Supporting them requires **server-side identity brokering** in the AS: a "Sign in with Google" action that redirects to Google, handles the callback, reuses `GoogleOAuthProvider.validateAndExtractClaims`, resolves the Invistus user, and continues to `/oauth2/consent`. This is tracked as a dedicated issue and is a launch prerequisite for federated users of the MCP feature.

## Consequences

- The `security` service maintains a small server-rendered UI stack (Mustache + `.properties` i18n) for `oauth2-login` and `oauth2-consent`. This is deliberate duplication of styling effort, accepted for the boundary it preserves.
- Consent copy and scope descriptions live in the security service resources, per the #415 decision.

## Future evolution

If the server-rendered UX becomes a bottleneck (richer flows, MFA, design parity), the correct evolution is **a dedicated login/consent application on the auth origin** (the Ory model) — not folding the pages into the product SPA. A dedicated auth-domain app recovers the frontend toolchain (React, design system, i18n) while preserving the trust boundary, the same-origin session, and the failure-domain independence above. This is explicitly out of scope for MVP.
