import { useMemo } from "react";
import { useAuth } from "@/shared/providers";
import { useCheckPermissionQuery } from "@/lib/graphql/operations/authorization/queries.generated";

/**
 * Hook to check a specific permission for the current user
 * Results are cached by Apollo Client per permission/resourceId combination
 * 
 * @param permission - The permission name to check (e.g., "authorization:user:edit")
 * @param resourceId - Optional resource ID for resource-specific permissions
 * @returns Object with allowed status, loading state, and error
 */
export function useCheckPermission(permission: string, resourceId?: string | null) {
  const { user } = useAuth();

  const { data, loading, error } = useCheckPermissionQuery({
    skip: !user?.id || !permission,
    variables: {
      userId: user?.id || "",
      permission,
      resourceId: resourceId || null,
    },
    fetchPolicy: "cache-first", // Use cache to avoid redundant requests
  });

  const allowed = useMemo(() => {
    return data?.checkPermission?.allowed ?? false;
  }, [data]);

  return {
    allowed,
    loading,
    error: error || null,
  };
}
