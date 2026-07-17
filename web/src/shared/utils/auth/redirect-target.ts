/**
 * Safe redirect target parsing and sanitization for post-login redirects.
 * Validates and normalizes the `next` query parameter to prevent open redirects
 * and redirect loops to auth-only routes.
 */

const AUTH_ONLY_ROUTES = [
  '/signin',
  '/signup',
  '/forgot-password',
  '/reset-password',
  '/verify-email',
  '/verify-email-link',
] as const;

export type SearchParamsLike =
  | { get(key: string): string | null }
  | URLSearchParams
  | null
  | undefined;

const DEFAULT_FALLBACK = '/';

/**
 * Extracts the `next` redirect target from URL search params.
 * Returns the first non-empty string value or null.
 */
export function extractNextFromSearchParams(
  params: SearchParamsLike
): string | null {
  if (!params || typeof params.get !== 'function') {
    return null;
  }
  const raw = params.get('next');
  if (raw == null || typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  return trimmed === '' ? null : trimmed;
}

/**
 * Normalizes a path for auth-route comparison: strip trailing slash, take pathname only.
 */
function normalizedPathname(value: string): string {
  const beforeHash = value.split('#')[0];
  const pathname = beforeHash?.split('?')[0] ?? '';
  return pathname.replace(/\/+$/, '') || '/';
}

/**
 * Returns true if the path is an auth-only route (exact or under that segment).
 */
function isAuthOnlyRoute(pathname: string): boolean {
  const normalized = normalizedPathname(pathname);
  return AUTH_ONLY_ROUTES.some(
    (route) => normalized === route || normalized.startsWith(route + '/')
  );
}

/**
 * Validates and sanitizes a redirect target. Returns fallback for any invalid input.
 * - Must be non-empty string.
 * - Must start with exactly one `/` (reject `//`, no leading slash).
 * - Reject absolute URLs/schemes: no `:` before first `?` or `#`.
 * - Reject auth-only routes.
 */
export function sanitizeRedirectTarget(
  raw: string | null | undefined,
  fallback: string = DEFAULT_FALLBACK
): string {
  if (raw == null || typeof raw !== 'string') return fallback;
  const trimmed = raw.trim();
  if (trimmed === '') return fallback;

  // Must start with exactly one slash (reject // or no leading slash)
  if (!trimmed.startsWith('/') || trimmed.startsWith('//')) return fallback;

  // Reject absolute URLs / schemes: colon before ? or #
  const pathPart = trimmed.split('#')[0]?.split('?')[0] ?? '';
  if (pathPart.includes(':')) return fallback;

  if (isAuthOnlyRoute(trimmed)) return fallback;

  return trimmed;
}

/**
 * Resolves the post-login redirect from search params: extract `next`, then sanitize.
 * Single entry point for the sign-in page.
 */
export function resolvePostLoginRedirect(
  params: SearchParamsLike,
  fallback: string = DEFAULT_FALLBACK
): string {
  const raw = extractNextFromSearchParams(params);
  return sanitizeRedirectTarget(raw, fallback);
}

/**
 * Builds an auth-page href that preserves a safe `next` redirect target when present.
 * Used for links between /signin and /signup during invitation/deep-link flows.
 */
export function buildAuthHrefWithNext(
  path: "/signin" | "/signup",
  params: SearchParamsLike,
): string {
  const next = resolvePostLoginRedirect(params, "");
  if (!next) {
    return path;
  }
  return `${path}?next=${encodeURIComponent(next)}`;
}
