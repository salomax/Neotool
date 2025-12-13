"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useGetGroupWithRelationshipsQuery, GetGroupWithRelationshipsDocument } from "@/lib/graphql/operations/authorization-management/queries.generated";
import {
  useAssignRoleToGroupMutation,
  useRemoveRoleFromGroupMutation,
  useAssignGroupToUserMutation,
  useRemoveGroupFromUserMutation,
} from "@/lib/graphql/operations/authorization-management/mutations.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

type Role = {
  id: string;
  name: string;
};

type Member = {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

type Group = {
  id: string;
  name: string;
  description: string | null;
  roles: Role[];
  members: Member[];
};

export interface UseGroupDrawerReturn {
  // Data
  group: Group | null;
  loading: boolean;
  error: Error | undefined;
  
  // Form state
  selectedRoles: Role[];
  selectedUsers: Member[];
  hasChanges: boolean;
  saving: boolean;
  
  // Handlers
  updateSelectedRoles: (roles: Role[]) => void;
  updateSelectedUsers: (users: Member[]) => void;
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
      members: (data.group.members || []).map(m => ({
        id: m.id,
        email: m.email,
        displayName: m.displayName,
        enabled: m.enabled,
      })),
    };
  }, [data?.group, groupId]);

  // Local form state for roles and users
  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [selectedUsers, setSelectedUsers] = useState<Member[]>([]);
  const [saving, setSaving] = useState(false);

  // Mutation hooks
  const [assignRoleToGroupMutation] = useAssignRoleToGroupMutation();
  const [removeRoleFromGroupMutation] = useRemoveRoleFromGroupMutation();
  const [assignGroupToUserMutation] = useAssignGroupToUserMutation();
  const [removeGroupFromUserMutation] = useRemoveGroupFromUserMutation();

  // Initialize form state when group data loads
  useEffect(() => {
    if (group) {
      setSelectedRoles(group.roles.map(r => ({
        id: r.id,
        name: r.name,
      })));
      setSelectedUsers(group.members.map(m => ({
        id: m.id,
        email: m.email,
        displayName: m.displayName,
        enabled: m.enabled,
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
    
    const originalUserIds = new Set(group.members.map(m => m.id));
    const currentUserIds = new Set(selectedUsers.map(u => u.id));
    const usersChanged = 
      selectedUsers.length !== group.members.length ||
      selectedUsers.some(u => !originalUserIds.has(u.id)) ||
      group.members.some(m => !currentUserIds.has(m.id));
    
    return rolesChanged || usersChanged;
  }, [group, selectedRoles, selectedUsers]);

  // Handler for updating local state
  const updateSelectedRoles = useCallback((roles: Role[]) => {
    setSelectedRoles(roles);
  }, []);

  const updateSelectedUsers = useCallback((users: Member[]) => {
    setSelectedUsers(users);
  }, []);

  // Reset changes to original state
  const resetChanges = useCallback(() => {
    if (group) {
      setSelectedRoles(group.roles.map(r => ({
        id: r.id,
        name: r.name,
      })));
      setSelectedUsers(group.members.map(m => ({
        id: m.id,
        email: m.email,
        displayName: m.displayName,
        enabled: m.enabled,
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

      // Calculate differences for users
      const originalUserIds = new Set(group.members.map(m => m.id));
      const currentUserIds = new Set(selectedUsers.map(u => u.id));
      const usersToAdd = selectedUsers.filter(u => !originalUserIds.has(u.id)).map(u => u.id);
      const usersToRemove = Array.from(originalUserIds).filter(id => !currentUserIds.has(id));

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

      // User mutations
      for (const userId of usersToAdd) {
        mutationPromises.push(
          assignGroupToUserMutation({
            variables: { userId, groupId },
            refetchQueries: [GetGroupWithRelationshipsDocument],
          })
        );
      }
      
      for (const userId of usersToRemove) {
        mutationPromises.push(
          removeGroupFromUserMutation({
            variables: { userId, groupId },
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
    selectedUsers,
    saving,
    assignRoleToGroupMutation,
    removeRoleFromGroupMutation,
    assignGroupToUserMutation,
    removeGroupFromUserMutation,
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
    selectedUsers,
    hasChanges,
    saving,
    
    // Handlers
    updateSelectedRoles,
    updateSelectedUsers,
    handleSave,
    resetChanges,
    refetch,
  };
}
