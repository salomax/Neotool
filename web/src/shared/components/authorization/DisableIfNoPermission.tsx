"use client";

import * as React from "react";
import { useAuthorization } from "@/shared/providers";

export interface DisableIfNoPermissionProps {
  /**
   * Single permission or array of permissions (all required)
   */
  permission: string | string[];
  /**
   * If true, user needs at least one permission instead of all
   */
  anyOf?: boolean;
  /**
   * Child element to disable (must accept disabled prop)
   */
  children: React.ReactElement;
}

/**
 * DisableIfNoPermission component - Disables a child component if permission is missing
 * 
 * @example
 * ```tsx
 * <DisableIfNoPermission permission="authorization:user:edit">
 *   <Button>Edit User</Button>
 * </DisableIfNoPermission>
 * ```
 * 
 * @example
 * ```tsx
 * <DisableIfNoPermission permission={["authorization:user:edit", "authorization:user:delete"]} anyOf>
 *   <Button>Action</Button>
 * </DisableIfNoPermission>
 * ```
 */
export const DisableIfNoPermission: React.FC<DisableIfNoPermissionProps> = ({
  permission,
  anyOf = false,
  children,
}) => {
  const { has, hasAny, hasAll, loading } = useAuthorization();

  // Determine if user has permission
  let hasPermission = false;

  if (loading) {
    // While loading, disable to prevent premature actions
    hasPermission = false;
  } else if (anyOf) {
    // User needs at least one permission
    const permissions = Array.isArray(permission) ? permission : [permission];
    hasPermission = hasAny(permissions);
  } else {
    // User needs all permissions
    const permissions = Array.isArray(permission) ? permission : [permission];
    hasPermission = hasAll(permissions);
  }

  // Clone child element and add disabled prop
  return React.cloneElement(children, {
    ...children.props,
    disabled: !hasPermission || children.props.disabled,
  });
};
