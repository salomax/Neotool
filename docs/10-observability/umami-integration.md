# User Story: Umami Web Analytics Integration

## Story

**As a** product manager
**I want** to have web analytics tracking on the Invistus platform
**So that** I can understand user behavior, measure engagement, and make data-driven product decisions

---

## Context

Per [ADR-0012](../92-adr/0012-web-analytics-tooling.md), we are implementing a two-tool analytics stack:
- **Umami** (self-hosted) for web analytics
- **Microsoft Clarity** (cloud, free) for heatmaps, session replay, and Web Vitals

This story covers **Umami integration only**. Microsoft Clarity will be handled in a separate story.

---

## Acceptance Criteria

### Functional Requirements
- [ ] Umami tracking script is loaded on all Next.js pages
- [ ] Script is only loaded when `NEXT_PUBLIC_UMAMI_ENABLED=true`
- [ ] Page views are tracked automatically
- [ ] Custom events can be tracked via JavaScript API
- [ ] User identification works for logged-in users
- [ ] UTM campaign parameters are tracked
- [ ] Real-time dashboard shows visitor data
- [ ] Analytics data is stored in self-hosted PostgreSQL instance

### Non-Functional Requirements
- [ ] Script size is ~2 KB (as per ADR specification)
- [ ] Script loads asynchronously without blocking page rendering
- [ ] No cookies are set (cookieless tracking)
- [ ] No PII is stored in Umami database
- [ ] Integration follows existing pattern from `CloudflareAnalytics.tsx`
- [ ] Zero impact on Core Web Vitals

### Privacy & Compliance
- [ ] IP addresses are discarded after geolocation lookup
- [ ] No consent banner required (cookieless, GDPR-compliant)
- [ ] Data is stored on self-hosted infrastructure only

---

## Technical Implementation Tasks

### Task 1: Infrastructure - Deploy Umami Instance

**Description:** Set up self-hosted Umami instance with PostgreSQL backend

**Subtasks:**
1. Provision PostgreSQL database instance
   - Create new PostgreSQL database named `umami`
   - Set up database user with appropriate permissions
   - Configure connection pooling if needed
   - Document connection string format

2. Deploy Umami application
   - Choose deployment method (Docker Compose, Kubernetes, or VPS)
   - Pull Umami Docker image (latest stable version)
   - Configure environment variables:
     - `DATABASE_URL`: PostgreSQL connection string
     - `HASH_SALT`: Random string for hashing (generate securely)
   - Expose Umami on HTTPS endpoint (e.g., `https://analytics.invistus.com`)
   - Set up reverse proxy (Nginx/Caddy) if needed

3. Configure SSL/TLS
   - Set up SSL certificate (Let's Encrypt recommended)
   - Enforce HTTPS only
   - Configure CORS headers if needed

4. Initial Umami setup
   - Access Umami admin panel
   - Change default admin password
   - Create website entry for Invistus
   - Copy website ID for Next.js integration
   - Copy tracking script URL

**Acceptance Criteria:**
- [ ] Umami instance is accessible via HTTPS
- [ ] PostgreSQL database is connected and healthy
- [ ] Admin panel is accessible and secured
- [ ] Website is created in Umami with tracking code
- [ ] Script URL is documented (e.g., `https://analytics.invistus.com/script.js`)

**Documentation:**
- Document deployment architecture
- Save connection details in secure vault (1Password, AWS Secrets Manager)
- Create runbook for Umami maintenance and updates

---

### Task 2: Frontend - Create UmamiAnalytics Component

**Description:** Create React component to inject Umami tracking script into Next.js app

**File to Create:** `apps/web/components/analytics/UmamiAnalytics.tsx`

**Implementation Details:**

```typescript
'use client';

import Script from 'next/script';

const UmamiAnalytics = () => {
  const enabled = process.env.NEXT_PUBLIC_UMAMI_ENABLED === 'true';
  const websiteId = process.env.NEXT_PUBLIC_UMAMI_WEBSITE_ID;
  const scriptUrl = process.env.NEXT_PUBLIC_UMAMI_SCRIPT_URL;

  // Don't render if not enabled or missing configuration
  if (!enabled || !websiteId || !scriptUrl) {
    return null;
  }

  return (
    <Script
      src={scriptUrl}
      data-website-id={websiteId}
      strategy="afterInteractive"
      defer
    />
  );
};

export default UmamiAnalytics;
```

**Subtasks:**
1. Create component file following above structure
2. Use Next.js `Script` component with `strategy="afterInteractive"`
3. Add environment variable validation
4. Add TypeScript types if needed
5. Follow existing pattern from `CloudflareAnalytics.tsx` for consistency

**Acceptance Criteria:**
- [ ] Component only renders when `NEXT_PUBLIC_UMAMI_ENABLED=true`
- [ ] Component validates required environment variables
- [ ] Script loads asynchronously via `strategy="afterInteractive"`
- [ ] Script has `defer` attribute for non-blocking load
- [ ] TypeScript types are properly defined
- [ ] Code follows project linting and formatting standards

---

### Task 3: Frontend - Integrate Component into Root Layout

**Description:** Add UmamiAnalytics component to Next.js root layout

**File to Modify:** `apps/web/app/layout.tsx` (or equivalent root layout)

**Implementation Details:**

```typescript
import UmamiAnalytics from '@/components/analytics/UmamiAnalytics';
import CloudflareAnalytics from '@/components/analytics/CloudflareAnalytics';

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {children}
        <CloudflareAnalytics />
        <UmamiAnalytics />
      </body>
    </html>
  );
}
```

**Subtasks:**
1. Import `UmamiAnalytics` component
2. Add component to layout (inside `<body>`, after content)
3. Ensure it's positioned alongside existing analytics components
4. Verify no duplicate imports or renders

**Acceptance Criteria:**
- [ ] Component is rendered in root layout
- [ ] Component is placed after main content (before `</body>`)
- [ ] No layout shift or visual impact on page
- [ ] Component renders on all pages (home, dashboard, settings, etc.)

---

### Task 4: Configuration - Add Environment Variables

**Description:** Add Umami configuration to environment files

**Files to Modify:**
- `.env.example` (template)
- `.env.local` (local development)
- `.env.production` (production environment)

**Environment Variables:**

```bash
# Umami Web Analytics
NEXT_PUBLIC_UMAMI_ENABLED=true
NEXT_PUBLIC_UMAMI_WEBSITE_ID=<website-id-from-umami-dashboard>
NEXT_PUBLIC_UMAMI_SCRIPT_URL=https://analytics.invistus.com/script.js
```

**Subtasks:**
1. Add variables to `.env.example` with placeholder values and comments
2. Set `NEXT_PUBLIC_UMAMI_ENABLED=false` in `.env.example` (disabled by default)
3. Configure actual values in `.env.local` for local testing
4. Configure actual values in production environment (Vercel, AWS, etc.)
5. Document environment variables in project README or docs

**Acceptance Criteria:**
- [ ] `.env.example` includes all Umami variables with comments
- [ ] Local environment has Umami configured (or disabled for local dev)
- [ ] Production environment has Umami enabled with correct credentials
- [ ] Environment variables are documented
- [ ] Secrets are not committed to git

---

### Task 5: Frontend - Create Umami Tracking Helper

**Description:** Create utility functions for custom event tracking and user identification

**File to Create:** `apps/web/lib/analytics/umami.ts`

**Implementation Details:**

```typescript
/**
 * Umami analytics tracking utilities
 * Docs: https://umami.is/docs/tracker-functions
 */

declare global {
  interface Window {
    umami?: {
      track: (eventName: string, eventData?: Record<string, any>) => void;
      identify: (properties: Record<string, any>) => void;
    };
  }
}

/**
 * Track a custom event in Umami
 * @param eventName - Name of the event (e.g., "button-click", "form-submit")
 * @param eventData - Optional event properties
 */
export const trackEvent = (
  eventName: string,
  eventData?: Record<string, any>
) => {
  if (typeof window !== 'undefined' && window.umami) {
    window.umami.track(eventName, eventData);
  }
};

/**
 * Identify a user in Umami (for logged-in users)
 * @param userId - Unique user identifier
 * @param properties - Additional user properties
 */
export const identifyUser = (
  userId: string,
  properties?: Record<string, any>
) => {
  if (typeof window !== 'undefined' && window.umami) {
    window.umami.identify({
      userId,
      ...properties,
    });
  }
};

/**
 * Track page view manually (if needed)
 * Umami automatically tracks page views, but this can be used for SPA navigation
 */
export const trackPageView = (url?: string) => {
  if (typeof window !== 'undefined' && window.umami) {
    window.umami.track('pageview', { url: url || window.location.pathname });
  }
};
```

**Subtasks:**
1. Create utility file with TypeScript types
2. Implement `trackEvent()` function for custom events
3. Implement `identifyUser()` function for user identification
4. Implement `trackPageView()` function (optional, for manual tracking)
5. Add JSDoc comments for documentation
6. Add window type declarations for TypeScript support

**Acceptance Criteria:**
- [ ] Functions are exported and ready to use
- [ ] TypeScript types are properly defined
- [ ] Functions check for `window.umami` availability
- [ ] Functions are safe to call server-side (SSR-safe)
- [ ] Code includes JSDoc documentation
- [ ] Code follows project standards

---

### Task 6: Integration - Add User Identification on Login

**Description:** Identify users in Umami when they log in

**File to Modify:** Authentication flow (e.g., `apps/web/app/api/auth/callback.ts` or login component)

**Implementation Details:**

```typescript
import { identifyUser } from '@/lib/analytics/umami';

// After successful authentication
const user = await getAuthenticatedUser();

if (user) {
  identifyUser(user.id, {
    email: user.email, // Only if privacy policy allows
    plan: user.subscriptionPlan,
    createdAt: user.createdAt,
    // Add other non-PII properties as needed
  });
}
```

**Subtasks:**
1. Find authentication success handler in codebase
2. Import `identifyUser` function
3. Call `identifyUser()` after successful login
4. Pass user ID and relevant non-PII properties
5. Ensure this doesn't break authentication flow
6. Add error handling (try/catch) to prevent analytics from breaking auth

**Acceptance Criteria:**
- [ ] `identifyUser()` is called after successful login
- [ ] User ID is passed (non-PII identifier)
- [ ] Additional properties are passed if privacy-compliant
- [ ] Error in analytics doesn't break authentication
- [ ] Works on both client and server authentication flows

**Privacy Note:**
- Only pass non-PII data (user ID, plan type, creation date)
- Avoid passing email, name, or other PII unless explicitly allowed by privacy policy
- Confirm with legal/privacy team before passing any user data

---

### Task 7: Integration - Track Custom Events (Examples)

**Description:** Add custom event tracking to key user interactions

**Example Implementations:**

1. **Button clicks:**
```typescript
import { trackEvent } from '@/lib/analytics/umami';

<button onClick={() => {
  trackEvent('cta-click', { button: 'signup', location: 'hero' });
  // ... handle click
}}>
  Sign Up
</button>
```

2. **Form submissions:**
```typescript
const handleSubmit = async (data) => {
  trackEvent('form-submit', { form: 'contact', fields: Object.keys(data) });
  // ... submit form
};
```

3. **Feature usage:**
```typescript
const exportReport = async () => {
  trackEvent('feature-usage', { feature: 'export-report', format: 'pdf' });
  // ... export logic
};
```

**Subtasks:**
1. Identify 5-10 key events to track (e.g., signup, login, feature usage)
2. Add `trackEvent()` calls to those interactions
3. Define consistent event naming convention (e.g., `kebab-case`)
4. Document tracked events in a separate file or README
5. Ensure events don't block user interactions (fire-and-forget)

**Acceptance Criteria:**
- [ ] At least 5 custom events are tracked
- [ ] Event names follow consistent naming convention
- [ ] Event tracking doesn't impact UX or performance
- [ ] Events are documented for future reference

**Recommended Events to Track:**
- `signup-complete`
- `login-success`
- `subscription-upgrade`
- `report-export`
- `dashboard-view`
- `settings-update`
- `payment-complete`
- `feature-{name}-used`

---

### Task 8: Testing - Verify Tracking in Development

**Description:** Test Umami integration in local development environment

**Testing Checklist:**

1. **Script Loading:**
   - [ ] Inspect page source and verify Umami script tag is present
   - [ ] Verify script URL is correct
   - [ ] Verify `data-website-id` attribute is set
   - [ ] Check browser DevTools Network tab for script load (200 status)

2. **Page View Tracking:**
   - [ ] Navigate to multiple pages
   - [ ] Check Umami dashboard for real-time page views
   - [ ] Verify page URLs are tracked correctly
   - [ ] Test with different browsers (Chrome, Firefox, Safari)

3. **Custom Events:**
   - [ ] Trigger custom events (button clicks, form submits)
   - [ ] Verify events appear in Umami dashboard
   - [ ] Check event properties are captured correctly

4. **User Identification:**
   - [ ] Log in as test user
   - [ ] Verify user identification in Umami dashboard
   - [ ] Check user properties are stored correctly

5. **Privacy:**
   - [ ] Verify no cookies are set (check DevTools Application > Cookies)
   - [ ] Verify no localStorage data related to Umami
   - [ ] Test in incognito/private mode

6. **Performance:**
   - [ ] Run Lighthouse audit (target: no impact on scores)
   - [ ] Check script size is ~2 KB (via Network tab)
   - [ ] Verify async loading (script doesn't block rendering)

**Acceptance Criteria:**
- [ ] All tests pass in local environment
- [ ] Data appears in Umami dashboard
- [ ] No console errors related to Umami
- [ ] No negative performance impact

---

### Task 9: Testing - Staging Environment Validation

**Description:** Validate Umami integration in staging environment

**Testing Checklist:**

1. **Deployment Verification:**
   - [ ] Verify environment variables are set in staging
   - [ ] Verify `NEXT_PUBLIC_UMAMI_ENABLED=true` in staging
   - [ ] Check build logs for any Umami-related errors

2. **Functional Testing:**
   - [ ] Repeat all tests from Task 8 in staging environment
   - [ ] Test with different devices (desktop, mobile, tablet)
   - [ ] Test with different network conditions (slow 3G, 4G)

3. **Cross-Browser Testing:**
   - [ ] Chrome
   - [ ] Firefox
   - [ ] Safari
   - [ ] Edge
   - [ ] Mobile browsers (iOS Safari, Chrome Android)

4. **Analytics Data:**
   - [ ] Generate test traffic (multiple page views, events)
   - [ ] Wait 5-10 minutes for data processing
   - [ ] Verify all data appears correctly in Umami dashboard

**Acceptance Criteria:**
- [ ] All tests pass in staging environment
- [ ] Cross-browser compatibility confirmed
- [ ] Analytics data is accurate and complete

---

### Task 10: Documentation

**Description:** Document Umami integration for team reference

**Documentation Tasks:**

1. **Update Project README:**
   - Add Umami to "Analytics" section
   - Document environment variables
   - Link to this story or ADR-0012

2. **Create Analytics Documentation:**
   - File: `docs/analytics/umami-integration.md`
   - Include:
     - Overview of Umami integration
     - How to track custom events (with code examples)
     - How to use `identifyUser()` function
     - List of currently tracked events
     - Link to Umami dashboard (with access instructions)
     - Troubleshooting guide

3. **Create Runbook for Umami Maintenance:**
   - File: `docs/runbooks/umami-maintenance.md`
   - Include:
     - How to access Umami admin panel
     - How to create new websites
     - How to update Umami instance
     - How to backup PostgreSQL data
     - How to troubleshoot tracking issues

4. **Update Environment Variables Documentation:**
   - Document all Umami environment variables
   - Explain when to enable/disable tracking
   - Provide examples for different environments

**Acceptance Criteria:**
- [ ] README includes Umami documentation
- [ ] Analytics documentation is created with examples
- [ ] Runbook is created for operations team
- [ ] Environment variables are documented
- [ ] Team is aware of new documentation

---

### Task 11: Deployment - Production Rollout

**Description:** Deploy Umami integration to production

**Deployment Checklist:**

1. **Pre-Deployment:**
   - [ ] Code review completed and approved
   - [ ] All tests passing in staging
   - [ ] Environment variables configured in production
   - [ ] Umami instance is production-ready (HTTPS, scaled appropriately)
   - [ ] Team notified of deployment

2. **Deployment:**
   - [ ] Merge to main branch
   - [ ] Trigger production deployment
   - [ ] Monitor deployment logs for errors
   - [ ] Verify deployment completes successfully

3. **Post-Deployment Verification:**
   - [ ] Visit production site and verify script loads
   - [ ] Check Umami dashboard for real-time data
   - [ ] Test custom events in production
   - [ ] Monitor error tracking (Sentry) for any Umami-related errors
   - [ ] Run Lighthouse audit (ensure no performance regression)

4. **Monitoring (First 24 Hours):**
   - [ ] Check Umami dashboard every few hours
   - [ ] Verify visitor counts are reasonable
   - [ ] Ensure no spike in errors or performance issues
   - [ ] Review Sentry for any analytics-related errors

**Acceptance Criteria:**
- [ ] Deployment completes without errors
- [ ] Umami tracking works in production
- [ ] No performance degradation
- [ ] Analytics data is flowing to Umami dashboard
- [ ] Team is notified of successful deployment

**Rollback Plan:**
- If critical issues arise, set `NEXT_PUBLIC_UMAMI_ENABLED=false` in production
- This will disable tracking without requiring a full rollback
- Fix issues and re-enable once resolved

---

### Task 12: Post-Launch - Dashboard Setup

**Description:** Configure Umami dashboard for team use

**Configuration Tasks:**

1. **Create Team Accounts:**
   - Create accounts for product managers, engineers, leadership
   - Set appropriate permissions (viewer, admin)
   - Share login credentials via secure method

2. **Configure Dashboard Filters:**
   - Set default date range (last 30 days)
   - Create saved views for common reports:
     - Homepage performance
     - User signup funnel
     - Feature usage
     - Geographic distribution

3. **Set Up Alerts (if available):**
   - Configure alerts for traffic anomalies
   - Set up weekly/monthly email reports

4. **Document Dashboard Usage:**
   - Create guide: "How to Use Umami Dashboard"
   - Include screenshots and common queries
   - Share with team

**Acceptance Criteria:**
- [ ] Team members have dashboard access
- [ ] Dashboard is configured with useful views
- [ ] Team is trained on how to use dashboard
- [ ] Documentation is available

---

## Definition of Done

- [ ] All tasks above are completed
- [ ] Code is merged to main branch
- [ ] Umami is live in production
- [ ] Analytics data is flowing correctly
- [ ] Team has access to Umami dashboard
- [ ] Documentation is complete
- [ ] No performance regressions
- [ ] No privacy or security issues
- [ ] Stakeholders are notified

---

## Dependencies

1. **Infrastructure:**
   - PostgreSQL instance (self-hosted or managed)
   - HTTPS endpoint for Umami (domain/subdomain)
   - SSL certificate

2. **Access:**
   - Access to production environment variables
   - Access to deployment pipeline
   - Admin access to Umami instance

3. **Related Work:**
   - This story is independent of Microsoft Clarity integration
   - Can be implemented and deployed separately

---

## Risks & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Umami instance downtime | Analytics data loss | Low | Set up monitoring and alerts; document recovery process |
| Performance impact on site | Degraded user experience | Low | Async script loading; monitor Core Web Vitals |
| Privacy compliance issues | Legal/regulatory risk | Low | Cookieless tracking; no PII storage; GDPR-compliant by design |
| Data inconsistency | Inaccurate metrics | Medium | Use Umami as source of truth; document tracking methodology |
| Script blocked by ad blockers | Incomplete data | Medium | Expected behavior; self-hosted reduces blocking; not critical |

---

## Success Metrics

After deployment, validate success with:
- [ ] 95%+ of page views are tracked (compare to server logs)
- [ ] Custom events are firing correctly
- [ ] User identification works for logged-in users
- [ ] Lighthouse score remains the same or better
- [ ] Team is actively using dashboard for insights
- [ ] Zero Sentry errors related to Umami

---

## Timeline Estimate

| Phase | Estimated Time |
|-------|---------------|
| Infrastructure setup (Tasks 1) | 2-4 hours |
| Frontend implementation (Tasks 2-3) | 2-3 hours |
| Configuration (Task 4) | 1 hour |
| Utilities & integration (Tasks 5-7) | 3-4 hours |
| Testing (Tasks 8-9) | 2-3 hours |
| Documentation (Task 10) | 2-3 hours |
| Deployment & post-launch (Tasks 11-12) | 2-3 hours |
| **Total** | **14-23 hours** (2-3 days) |

*Note: Timeline assumes familiarity with Next.js and existing codebase*

---

## References

- [ADR-0012: Web Analytics Tooling](../92-adr/0012-web-analytics-tooling.md)
- [Umami Documentation](https://umami.is/docs)
- [Umami Tracker Functions](https://umami.is/docs/tracker-functions)
- [Next.js Script Optimization](https://nextjs.org/docs/app/building-your-application/optimizing/scripts)

---

## Related Stories

- **Next:** Microsoft Clarity Integration (heatmaps, session replay, Web Vitals)
- **Future:** Mobile Analytics with PostHog (if React Native/Expo analytics needed)

---

**Created:** 2026-02-16
**Status:** Ready for Implementation
**Priority:** High
**Estimated Effort:** 2-3 days
