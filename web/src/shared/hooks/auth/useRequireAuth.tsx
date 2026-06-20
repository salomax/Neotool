"use client";

import * as React from "react";
import { useEffect } from "react";
import { usePathname, useSearchParams, useRouter } from "next/navigation";
import { Box } from "@mui/material";
import { useAuth } from "@/shared/providers";
import { isRouteProtected } from "@/shared/config/auth-route-policy";
import { LoadingSpinner } from "@/shared/components/ui/primitives/LoadingSpinner";

/**
 * Guard hook for protected routes. Redirects anonymous users to sign-in with
 * next=<encoded current pathname + search>. Use in layout or page to enforce auth.
 *
 * @returns { allowed: true } when the user may see the route (authenticated or public).
 * @returns { allowed: false } when redirecting to sign-in; caller should not render protected content.
 */
export function useRequireAuth(): {
  allowed: boolean;
  isPending: boolean;
  isRedirecting: boolean;
} {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

  const protectedRoute = isRouteProtected(pathname ?? null);
  const isPending = protectedRoute && isLoading;
  const shouldRedirect =
    !isLoading && !isAuthenticated && protectedRoute;

  useEffect(() => {
    if (!shouldRedirect) return;
    const searchString = searchParams?.toString();
    const fullPath =
      (pathname ?? "/") + (searchString ? `?${searchString}` : "");
    const signInUrl = `/signin?next=${encodeURIComponent(fullPath)}`;
    router.replace(signInUrl);
  }, [shouldRedirect, pathname, searchParams, router]);

  return {
    allowed: !shouldRedirect && !isPending,
    isPending,
    isRedirecting: shouldRedirect,
  };
}

/**
 * Wrapper that runs useRequireAuth and only renders children when the user
 * is allowed to see the current route (authenticated or public). Use in layout.
 */
export function RequireAuthGuard({ children }: { children: React.ReactNode }) {
  const { allowed, isPending, isRedirecting } = useRequireAuth();

  if (isPending || isRedirecting) {
    return (
      <Box data-testid="auth-guard-loading" sx={{ width: "100%" }}>
        <LoadingSpinner minHeight={280} />
      </Box>
    );
  }

  if (!allowed) return null;
  return <>{children}</>;
}
