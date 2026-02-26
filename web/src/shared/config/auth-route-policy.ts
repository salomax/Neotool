/**
 * Route policy for auth guard: which routes require authentication.
 * Used by useRequireAuth and by middleware for optimistic redirect.
 */

/**
 * Cookie set by client when user has a token; read by middleware as optimistic auth hint only.
 * This does not replace backend authorization — it only avoids redirecting obviously logged-in users.
 */
export const AUTH_HINT_COOKIE_NAME = "auth_hint";

/** Path prefixes for routes that require authentication (logged area). /investments and /financial-data are anonymous. */
export const PROTECTED_ROUTE_PREFIXES = [
  "/personal-finance",
  "/settings",
] as const;

/**
 * Returns true if the pathname is a protected route (requires auth).
 * Auth-only routes (signin, signup, etc.) are not considered protected.
 */
export function isRouteProtected(pathname: string | null): boolean {
  if (pathname == null || pathname === "") return false;
  const normalized = pathname.replace(/\/+$/, "") || "/";
  return PROTECTED_ROUTE_PREFIXES.some(
    (prefix) => normalized === prefix || normalized.startsWith(prefix + "/")
  );
}
