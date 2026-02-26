import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import {
  isRouteProtected,
  AUTH_HINT_COOKIE_NAME,
} from "@/shared/config/auth-route-policy";

/**
 * Optimistic auth guard: redirects anonymous requests to protected routes
 * to sign-in with next=<requested path>. Uses a lightweight cookie hint
 * only — behavior is optimistic-only; backend must still enforce authorization.
 * No regressions: static assets, _next, and api routes are excluded by matcher.
 *
 * @see AuthProvider sets/clears AUTH_HINT_COOKIE_NAME on client
 */
function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname;

  if (!isRouteProtected(pathname)) {
    return NextResponse.next();
  }

  const hasAuthHint = request.cookies.has(AUTH_HINT_COOKIE_NAME);
  if (hasAuthHint) {
    return NextResponse.next();
  }

  const fullPath =
    pathname + (request.nextUrl.search ? request.nextUrl.search : "");
  const signInUrl = new URL("/signin", request.url);
  signInUrl.searchParams.set("next", fullPath);

  return NextResponse.redirect(signInUrl);
}

export { middleware };

export const config = {
  matcher: [
    /*
     * Skip static assets, _next, and API routes.
     * Optimistic guard runs only on document requests to app routes.
     */
    "/((?!_next/static|_next/image|favicon.ico|images/|api/).*)",
  ],
};
