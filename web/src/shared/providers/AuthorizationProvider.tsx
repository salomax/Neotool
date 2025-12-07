"use client";

import * as React from "react";
import { useAuth } from "./AuthProvider";
import { useCurrentUserQuery } from "@/lib/graphql/operations/auth/queries.generated";

export type Role = {
  id: string;
  name: string;
};

export type Permission = {
  id: string;
  name: string;
};

type AuthorizationContextType = {
  permissions: Set<string>;
  roles: Role[];
  loading: boolean;
  has: (permission: string) => boolean;
  hasAny: (permissions: string[]) => boolean;
  hasAll: (permissions: string[]) => boolean;
  refreshAuthorization: () => Promise<void>;
};

const AuthorizationContext = React.createContext<AuthorizationContextType | null>(null);

export const useAuthorization = (): AuthorizationContextType => {
  const ctx = React.useContext(AuthorizationContext);
  if (!ctx) {
    throw new Error("useAuthorization must be used within AuthorizationProvider");
  }
  return ctx;
};

type AuthorizationProviderProps = {
  children: React.ReactNode;
};

export const AuthorizationProvider: React.FC<AuthorizationProviderProps> = ({ children }) => {
  const { user, isAuthenticated } = useAuth();
  const [permissions, setPermissions] = React.useState<Set<string>>(new Set());
  const [roles, setRoles] = React.useState<Role[]>([]);
  const [loading, setLoading] = React.useState(true);

  // Fetch current user with roles and permissions when authenticated
  const { data, loading: queryLoading, refetch } = useCurrentUserQuery({
    skip: !isAuthenticated,
    fetchPolicy: "cache-and-network",
  });

  // Update authorization state from query data
  React.useEffect(() => {
    if (!isAuthenticated || !user) {
      setPermissions(new Set());
      setRoles([]);
      setLoading(false);
      return;
    }

    // If we have query data, use it (it has roles and permissions)
    if (data?.currentUser) {
      const userPermissions = data.currentUser.permissions || [];
      const userRoles = data.currentUser.roles || [];

      setPermissions(new Set(userPermissions.map((p) => p.name)));
      setRoles(userRoles.map((r) => ({ id: r.id, name: r.name })));
      setLoading(queryLoading);
      return;
    }

    // Fallback: try to extract from user object if it has roles/permissions
    // This handles cases where user is set from sign-in/sign-up mutations
    if (user && typeof user === "object" && "roles" in user && "permissions" in user) {
      const userPermissions = (user as any).permissions || [];
      const userRoles = (user as any).roles || [];

      setPermissions(new Set(userPermissions.map((p: Permission) => p.name)));
      setRoles(userRoles.map((r: Role) => ({ id: r.id, name: r.name })));
      setLoading(false);
      return;
    }

    // If no data available yet, keep loading
    setLoading(queryLoading);
  }, [data, user, isAuthenticated, queryLoading]);

  // Refresh authorization by refetching current user
  const refreshAuthorization = React.useCallback(async () => {
    if (isAuthenticated) {
      await refetch();
    }
  }, [isAuthenticated, refetch]);

  // Permission check methods
  const has = React.useCallback(
    (permission: string): boolean => {
      return permissions.has(permission);
    },
    [permissions]
  );

  const hasAny = React.useCallback(
    (permissionList: string[]): boolean => {
      return permissionList.some((p) => permissions.has(p));
    },
    [permissions]
  );

  const hasAll = React.useCallback(
    (permissionList: string[]): boolean => {
      return permissionList.every((p) => permissions.has(p));
    },
    [permissions]
  );

  const value: AuthorizationContextType = {
    permissions,
    roles,
    loading,
    has,
    hasAny,
    hasAll,
    refreshAuthorization,
  };

  return <AuthorizationContext.Provider value={value}>{children}</AuthorizationContext.Provider>;
};
