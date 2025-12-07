"use client";

import * as React from "react";
import { useAuthorization } from "@/shared/providers";

export interface PermissionGateProps {
  /**
   * Single permission or array of permissions (all required)
   * If array, user must have ALL permissions
   */
  require?: string | string[];
  /**
   * Array of permissions where user needs at least one
   * Takes precedence over `require` if both are provided
   */
  anyOf?: string[];
  /**
   * Content to render when permission check fails
   */
  fallback?: React.ReactNode;
  /**
   * Content to render while checking permissions (loading state)
   */
  loadingFallback?: React.ReactNode;
  /**
   * Children to render when permission check passes
   */
  children: React.ReactNode;
}

/**
 * PermissionGate component - Conditionally renders children based on user permissions
 * 
 * @example
 * ```tsx
 * <PermissionGate require="authorization:user:edit" fallback={<div>No access</div>}>
 *   <EditButton />
 * </PermissionGate>
 * ```
 * 
 * @example
 * ```tsx
 * <PermissionGate anyOf={["authorization:user:edit", "authorization:user:view"]}>
 *   <UserActions />
 * </PermissionGate>
 * ```
 */
export const PermissionGate: React.FC<PermissionGateProps> = ({
  require,
  anyOf,
  fallback = null,
  loadingFallback = null,
  children,
}) => {
  const { has, hasAny, hasAll, loading } = useAuthorization();

  // Show loading fallback while checking
  if (loading) {
    return <>{loadingFallback}</>;
  }

  // Check permissions
  let hasPermission = false;

  if (anyOf && anyOf.length > 0) {
    // User needs at least one permission
    hasPermission = hasAny(anyOf);
  } else if (require) {
    // User needs all specified permissions
    const permissions = Array.isArray(require) ? require : [require];
    hasPermission = hasAll(permissions);
  } else {
    // No permission specified, allow access
    hasPermission = true;
  }

  // Render children if permission granted, otherwise fallback
  return <>{hasPermission ? children : fallback}</>;
};
