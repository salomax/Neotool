/**
 * Umami Analytics helpers
 *
 * Thin SSR-safe wrappers around `window.umami`.
 * All functions are no-ops on the server or when Umami is not loaded.
 * Never call these functions with PII (e-mail, name, CPF, etc.).
 */

/** Umami global injected by the script tag */
declare global {
  interface Window {
    umami?: {
      /** Track a custom event with optional payload */
      track(eventName: string, eventData?: Record<string, unknown>): void;
      /** Associate the current session with an identifier */
      identify(sessionData: Record<string, unknown>): void;
    };
  }
}

/**
 * Identify the current session with a non-PII user identifier.
 * Safe to call immediately after successful authentication.
 *
 * @param userId - Opaque user ID (UUID). Do NOT pass e-mail or name.
 */
export function identifyUser(userId: string): void {
  try {
    if (typeof window === "undefined" || !window.umami) return;
    window.umami.identify({ userId });
  } catch {
    // Never let analytics break the auth flow
  }
}

/**
 * Track a custom event.
 *
 * @param eventName - Descriptive event name (e.g. "fixed_income_comparison_viewed")
 * @param eventData - Optional non-PII payload (e.g. asset type, filter values)
 */
export function trackEvent(
  eventName: string,
  eventData?: Record<string, unknown>
): void {
  try {
    if (typeof window === "undefined" || !window.umami) return;
    window.umami.track(eventName, eventData);
  } catch {
    // Silently ignore — analytics must never affect product functionality
  }
}

/**
 * Track a manual page view.
 * Umami tracks page views automatically via the script tag;
 * use this only for virtual navigation not captured by the router.
 *
 * @param url - Path to record (e.g. "/dashboard/portfolio")
 */
export function trackPageView(url: string): void {
  try {
    if (typeof window === "undefined" || !window.umami) return;
    window.umami.track("page_view", { url });
  } catch {
    // Silently ignore
  }
}
