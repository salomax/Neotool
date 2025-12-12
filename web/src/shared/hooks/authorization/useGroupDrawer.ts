"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useGetGroupWithRelationshipsQuery, GetGroupWithRelationshipsDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";
import {
  useAssignRoleToGroupMutation,
  useRemoveRoleFromGroupMutation,
} from "@/lib/graphql/operations/authorization-management/mutations.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

type Role = {
  id: string;
  name: string;
};

type Group = {
  id: string;
  name: string;
  description: string | null;
  roles: Role[];
};

export interface UseGroupDrawerReturn {
  // Data
  group: Group | null;
  loading: boolean;
  error: Error | undefined;
  
  // Form state
  selectedRoles: Role[];
  hasChanges: boolean;
  saving: boolean;
  
  // Handlers
  updateSelectedRoles: (roles: Role[]) => void;
  handleSave: () => Promise<void>;
  resetChanges: () => void;
  refetch: () => Promise<any>;
}

export function useGroupDrawer(
  groupId: string | null,
  open: boolean
): UseGroupDrawerReturn {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Query group with relationships (only in edit mode)
  const { data, loading, error, refetch } = useGetGroupWithRelationshipsQuery({
    skip: !open || !groupId,
    variables: { id: groupId! },
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Extract group from query result
  const group = useMemo(() => {
    if (!data?.group || !groupId) return null;
    return {
      id: data.group.id,
      name: data.group.name,
      description: data.group.description,
      roles: (data.group.roles || []).map(r => ({
        id: r.id,
        name: r.name,
      })),
    };
  }, [data?.group, groupId]);

  // Local form state for roles
  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [saving, setSaving] = useState(false);

  // Mutation hooks
  const [assignRoleToGroupMutation] = useAssignRoleToGroupMutation();
  const [removeRoleFromGroupMutation] = useRemoveRoleFromGroupMutation();

  // Initialize form state when group data loads
  useEffect(() => {
    if (group) {
      setSelectedRoles(group.roles.map(r => ({
        id: r.id,
        name: r.name,
      })));
    }
  }, [group]);

  // Calculate hasChanges
  const hasChanges = useMemo(() => {
    if (!group) return false;
    
    const originalRoleIds = new Set(group.roles.map(r => r.id));
    const currentRoleIds = new Set(selectedRoles.map(r => r.id));
    const rolesChanged = 
      selectedRoles.length !== group.roles.length ||
      selectedRoles.some(r => !originalRoleIds.has(r.id)) ||
      group.roles.some(r => !currentRoleIds.has(r.id));
    
    return rolesChanged;
  }, [group, selectedRoles]);

  // Handler for updating local state
  const updateSelectedRoles = useCallback((roles: Role[]) => {
    setSelectedRoles(roles);
  }, []);

  // Reset changes to original state
  const resetChanges = useCallback(() => {
    if (group) {
      setSelectedRoles(group.roles.map(r => ({
        id: r.id,
        name: r.name,
      })));
    }
  }, [group]);

  // Handle save - calculate differences and execute mutations
  const handleSave = useCallback(async () => {
    if (!group || !groupId || saving) return;
    
    setSaving(true);
    
    try {
      // Calculate differences for roles
      const originalRoleIds = new Set(group.roles.map(r => r.id));
      const currentRoleIds = new Set(selectedRoles.map(r => r.id));
      const rolesToAdd = selectedRoles.filter(r => !originalRoleIds.has(r.id)).map(r => r.id);
      const rolesToRemove = Array.from(originalRoleIds).filter(id => !currentRoleIds.has(id));

      // Execute all mutations in parallel
      const mutationPromises: Promise<any>[] = [];
      
      // Role mutations
      for (const roleId of rolesToAdd) {
        mutationPromises.push(
          assignRoleToGroupMutation({
            variables: { groupId, roleId },
            refetchQueries: [GetGroupWithRelationshipsDocument],
          })
        );
      }
      
      for (const roleId of rolesToRemove) {
        mutationPromises.push(
          removeRoleFromGroupMutation({
            variables: { groupId, roleId },
            refetchQueries: [GetGroupWithRelationshipsDocument],
          })
        );
      }

      // Execute all mutations
      if (mutationPromises.length > 0) {
        await Promise.all(mutationPromises);
      }

      // Refetch to get updated data
      await refetch();
      
      // Show success message
      toast.success(t("groupManagement.toast.groupUpdated", { name: group.name }));
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        t("groupManagement.toast.groupUpdateError")
      );
      toast.error(errorMessage);
      throw err;
    } finally {
      setSaving(false);
    }
  }, [
    group,
    groupId,
    selectedRoles,
    saving,
    assignRoleToGroupMutation,
    removeRoleFromGroupMutation,
    refetch,
    toast,
    t,
  ]);

  return {
    // Data
    group,
    loading,
    error,
    
    // Form state
    selectedRoles,
    hasChanges,
    saving,
    
    // Handlers
    updateSelectedRoles,
    handleSave,
    resetChanges,
    refetch,
  };
}
