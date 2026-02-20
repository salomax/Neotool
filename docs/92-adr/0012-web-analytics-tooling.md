---
title: ADR-0012 Web Analytics Tooling
type: adr
category: frontend
status: proposed
version: 1.0.0
tags:
  [
    analytics,
    observability,
    privacy,
    heatmaps,
    session-replay,
    web-vitals,
    umami,
    clarity,
  ]
related:
  - adr/0007-asset-service-cloudflare-r2.md
  - adr/0004-typescript-nextjs-frontend.md
  - adr/0010-react-native-expo-mobile.md
---

# ADR-0012: Web Analytics Tooling

## Status

Proposed

## Context

neotool needs product analytics to understand user behavior, measure engagement, and guide product decisions. The current observability stack covers backend infrastructure (Grafana + Prometheus + Loki) and error tracking (Sentry), but there is no client-side product analytics in place.

The Cloudflare Web Analytics integration exists in the codebase but is disabled by default and provides only basic page-level metrics.

### Requirements

- **Unique visitors**: Track distinct users over time
- **Page analytics**: Page views, time on page, bounce rate, referrers
- **Demographics**: Country, region, city, device, browser, OS, language
- **Heatmaps**: Click, scroll, and area heatmaps
- **Session replay**: Record and replay user sessions to understand behavior
- **Performance**: Core Web Vitals (LCP, CLS, INP, TTFB)
- **Basic funnels**: Track user conversion through key flows
- **UTM tracking**: Campaign attribution

### Constraints

- **Privacy-first**: Prefer cookieless tracking and GDPR compliance without consent banners
- **Open source preferred**: Transparency, auditability, and community support
- **Free or low-cost**: Minimize recurring costs, especially in early stages
- **Lightweight**: Minimal impact on page load performance and Core Web Vitals
- **Simple to integrate**: Quick setup with Next.js App Router; can be sophisticating over time

## Decision

We will use a **two-tool combination**: **Umami** (self-hosted) for web analytics and **Microsoft Clarity** (cloud, free) for heatmaps, session replay, and Web Vitals.

### Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   Analytics Stack                        │
├──────────────────┬───────────────────────────────────────┤
│ Layer            │ Tool                                  │
├──────────────────┼───────────────────────────────────────┤
│ Web Analytics    │ Umami (self-hosted, AGPL)             │
│ Heatmaps/Replay  │ Microsoft Clarity (cloud, free)       │
│ Performance      │ Clarity Web Vitals + Lighthouse CI    │
│ Error Tracking   │ Sentry (already configured)           │
│ Backend Metrics  │ Grafana + Prometheus + Loki (existing)│
└──────────────────┴───────────────────────────────────────┘
```

### Umami (Web Analytics)

- **Deployment**: Self-hosted (requires PostgreSQL instance)
- **License**: AGPL v3
- **Script size**: ~2 KB
- **Tracking**: Cookieless by default, no PII stored, IP discarded after geolocation
- **Covers**: Unique visitors, page views, session duration, bounce rate, referrers, UTM campaigns, custom events, basic funnels, journey reports, cohort breakdowns, real-time dashboard
- **User identification**: Supports `identify()` for logged-in users with custom properties

### Microsoft Clarity (Heatmaps, Session Replay, Web Vitals)

- **Deployment**: Cloud only (Microsoft Azure)
- **License**: MIT (clarity-js on GitHub), service is Microsoft-hosted
- **Cost**: Free forever, no traffic limits, no recording caps
- **Script size**: ~22 KB (async loading)
- **Covers**: Click heatmaps, scroll heatmaps, area heatmaps, unlimited session recordings, rage click detection, dead click detection, Core Web Vitals (LCP, CLS, INP)
- **User identification**: Supports `identify()` API with custom-id
- **Privacy**: Automatic PII masking in recordings, configurable masking rules

### Combined Coverage Matrix

| Requirement               | Umami | Clarity         | Covered |
| ------------------------- | ----- | --------------- | ------- |
| Unique visitors           | Yes   | Yes             | Yes     |
| Page views                | Yes   | Yes             | Yes     |
| Time on page              | Yes   | Via replay      | Yes     |
| Bounce rate               | Yes   | —               | Yes     |
| Referrer tracking         | Yes   | Yes             | Yes     |
| UTM campaigns             | Yes   | Via tags        | Yes     |
| Country/Region/City       | Yes   | Yes             | Yes     |
| Device/Browser/OS         | Yes   | Yes             | Yes     |
| Language                  | Yes   | Yes             | Yes     |
| Click heatmaps            | —     | Yes             | Yes     |
| Scroll heatmaps           | —     | Yes             | Yes     |
| Area heatmaps             | —     | Yes             | Yes     |
| Session recordings        | —     | Yes (unlimited) | Yes     |
| Rage/dead click detection | —     | Yes             | Yes     |
| LCP / CLS / INP           | —     | Yes             | Yes     |
| Custom events             | Yes   | Limited (20)    | Yes     |
| Basic funnels             | Yes   | Basic           | Yes     |
| Real-time dashboard       | Yes   | —               | Yes     |
| User identification       | Yes   | Yes             | Yes     |

### Integration Approach

Both tools integrate via script tags in the Next.js layout, following the same pattern as the existing `CloudflareAnalytics.tsx` component:

```typescript
// UmamiAnalytics.tsx
const enabled = process.env.NEXT_PUBLIC_UMAMI_ENABLED === "true";
const websiteId = process.env.NEXT_PUBLIC_UMAMI_WEBSITE_ID;
const scriptUrl = process.env.NEXT_PUBLIC_UMAMI_SCRIPT_URL;

// ClarityAnalytics.tsx
const enabled = process.env.NEXT_PUBLIC_CLARITY_ENABLED === "true";
const projectId = process.env.NEXT_PUBLIC_CLARITY_PROJECT_ID;
```

### Environment Variables

```env
# Umami
NEXT_PUBLIC_UMAMI_ENABLED=true
NEXT_PUBLIC_UMAMI_WEBSITE_ID=<website-id>
NEXT_PUBLIC_UMAMI_SCRIPT_URL=https://your-umami-instance.com/script.js

# Microsoft Clarity
NEXT_PUBLIC_CLARITY_ENABLED=true
NEXT_PUBLIC_CLARITY_PROJECT_ID=<project-id>
```

## Consequences

### Positive

- **Privacy-first**: Umami is fully cookieless and GDPR-compliant without consent banners
- **Lightweight**: Combined script payload of ~24 KB (async) — minimal impact on Core Web Vitals
- **Zero cost for heatmaps**: Clarity is free with no traffic or recording limits
- **Low infrastructure cost**: Self-hosted Umami only requires a PostgreSQL instance (~$5-20/mo on a VPS)
- **Full data ownership**: Umami data stays on our infrastructure
- **Comprehensive coverage**: Together they cover all stated requirements without gaps
- **Simple integration**: Two script tags in the Next.js layout, following existing patterns
- **Independent tools**: Each can be replaced independently without affecting the other

### Negative

- **Two dashboards**: Analytics data is split across Umami and Clarity — no unified view
- **Clarity uses cookies**: Requires a consent banner in EEA/UK/CH regions (since October 2025)
- **Clarity data on Microsoft Azure**: Session recordings and heatmap data are stored on Microsoft infrastructure (US)
- **Umami self-hosting overhead**: Requires maintaining an additional service (PostgreSQL + Umami container)
- **No mobile analytics**: Umami has no React Native / Expo SDK — mobile app analytics are not covered by this decision
- **Limited advanced analytics**: No built-in A/B testing, feature flags, or advanced cohort analysis

### Risks

- **Umami AGPL license**: AGPL requires source disclosure if the software is offered as a service; since we self-host for internal use only, this does not apply
- **Clarity service dependency**: Microsoft could change terms, add limits, or discontinue the free tier
- **Data inconsistency**: Two tools may report slightly different visitor/session numbers due to different tracking methods

### Mitigation Strategies

- **Consent banner**: Implement a lightweight consent mechanism for Clarity in EEA regions; Umami can run without consent
- **Umami as source of truth**: Use Umami for authoritative visitor/session counts (cookieless = more accurate)
- **Clarity terms monitoring**: Periodically review Clarity's terms of service; OpenReplay (self-hosted) is a fallback alternative
- **Umami hosting**: Deploy Umami alongside existing infrastructure using Docker Compose or Kubernetes

## Alternatives Considered

### PostHog (all-in-one)

- **Pros**: Single tool covering analytics, heatmaps, session replay, A/B testing, feature flags, surveys, and error tracking. MIT license. Official React Native/Expo SDK for mobile. Advanced funnels, cohorts, retention analysis. Full API with HogQL (SQL queries). 1M events/month free tier
- **Cons**: Heavy client-side script (~100-266 KB with session replay). Higher self-hosting requirements (4 vCPU, 16 GB RAM minimum). More complex to set up and operate. Session replay limited to 5K recordings/month on free tier (vs unlimited on Clarity). Feature overlap with existing tools (Unleash for feature flags, Sentry for error tracking). Costs can grow quickly at scale
- **Verdict**: Better suited for teams needing advanced product analytics and A/B testing. Consider migrating to PostHog in the future if requirements grow beyond what Umami + Clarity provide

### Google Analytics 4

- **Pros**: Free, widely adopted, demographic data (age/gender) via Google Signals, advanced attribution modeling, BigQuery integration
- **Cons**: Not privacy-first (requires cookies and consent), not open source, data owned by Google, heavy script, complex interface, potential GDPR concerns
- **Verdict**: Rejected due to privacy concerns and vendor lock-in

### Plausible Analytics

- **Pros**: Privacy-first, cookieless, lightweight (~1 KB), clean UI, AGPL license, self-hostable
- **Cons**: No free cloud tier (paid only, starting at $9/mo). Fewer features than Umami (no funnels, no journey reports, no user identification in the same way). Smaller community
- **Verdict**: Strong alternative to Umami, but Umami offers more features (funnels, journey, identify) and a free cloud tier as fallback

### Umami + OpenReplay (instead of Clarity)

- **Pros**: Fully open source and self-hosted stack. Session replay with developer tools (network tab, console logs). No third-party data dependencies
- **Cons**: Higher self-hosting complexity and cost. No dedicated heatmap views (only session replay). Smaller community than Clarity. More maintenance burden
- **Verdict**: Consider if data sovereignty becomes a hard requirement and Clarity's Microsoft-hosted model is unacceptable

### PostHog + Clarity

- **Pros**: PostHog for advanced analytics, Clarity for free unlimited heatmaps/replay
- **Cons**: Combines the heaviest script (PostHog) with an additional script (Clarity). Overlap in session replay capabilities. More complexity than needed at current stage
- **Verdict**: Over-engineered for current requirements

## Out of Scope

The following capabilities are **not covered** by this decision and require separate tooling if needed:

| Capability                                 | Notes                                                                                   |
| ------------------------------------------ | --------------------------------------------------------------------------------------- |
| Age / gender demographics                  | Requires user self-reporting (signup/onboarding) or Google Signals (GA4)                |
| Mobile app analytics (React Native / Expo) | Umami has no mobile SDK; consider PostHog or Clarity mobile SDK separately              |
| A/B testing                                | Not available in Umami or Clarity; use Unleash (already configured) or consider PostHog |
| Advanced funnels and cohorts               | Umami provides basic funnels only; PostHog is the upgrade path                          |
| Backend API monitoring                     | Covered by existing Grafana + Prometheus stack                                          |
| Uptime monitoring                          | Requires dedicated tools (Better Stack, UptimeRobot)                                    |
| Revenue / subscription analytics           | Requires Stripe Analytics, Baremetrics, or similar                                      |
| SEO analytics                              | Requires Google Search Console, Ahrefs, or similar                                      |
| Multi-touch attribution                    | Requires GA4, Amplitude, or similar                                                     |
| Predictive analytics / ML                  | Requires Amplitude, Mixpanel, or custom ML pipelines                                    |
| Consent management platform                | Requires OneTrust, Cookiebot, or similar (needed for Clarity in EEA)                    |
| Cross-platform identity resolution         | Limited to what each tool provides; Segment or mParticle for advanced use cases         |

## Upgrade Path

If requirements grow beyond what Umami + Clarity provide, the recommended migration path is:

1. **Phase 1 (current)**: Umami (self-hosted) + Clarity — covers core analytics, heatmaps, replay, and Web Vitals
2. **Phase 2**: Add PostHog for advanced funnels, cohorts, retention, and A/B testing — either replace Umami or run alongside it
3. **Phase 3**: Add PostHog React Native SDK for mobile analytics — unified cross-platform analytics
4. **Phase 4**: Consider PostHog as single platform if it covers all needs — simplify the stack

## Decision Drivers

- Privacy-first approach aligns with financial platform user expectations
- Lightweight scripts preserve the performance gains from Next.js optimization
- Self-hosted Umami ensures full data ownership for analytics data
- Clarity's free unlimited tier eliminates cost concerns for heatmaps and session replay
- Simple two-script integration minimizes implementation effort and maintenance
- Clear upgrade path to PostHog avoids premature complexity while keeping options open
