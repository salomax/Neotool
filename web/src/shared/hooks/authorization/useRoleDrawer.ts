"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useGetRoleWithUsersAndGroupsQuery, useGetRolesWithPermissionsQuery } from "@/lib/graphql/operations/authorization-management/queries.generated";
import {
  useAssignRoleToUserMutation,
  useRemoveRoleFromUserMutation,
  useAssignRoleToGroupMutation,
  useRemoveRoleFromGroupMutation,
  useAssignPermissionToRoleMutation,
  useRemovePermissionFromRoleMutation,
} from "@/lib/graphql/operations/authorization-management/mutations.generated";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

type User = {
  id: string;
  email: string;
  displayName: string | null;
  enabled: boolean;
};

type Group = {
  id: string;
  name: string;
  description: string | null;
};

type Permission = {
  id: string;
  name: string;
};

type Role = {
  id: string;
  name: string;
};

export interface UseRoleDrawerReturn {
  // Data
  role: Role | null;
  loading: boolean;
  error: Error | undefined;
  
  // Form state
  selectedUsers: User[];
  selectedGroups: Group[];
  selectedPermissions: Permission[];
  hasChanges: boolean;
  saving: boolean;
  
  // Handlers
  updateSelectedUsers: (users: User[]) => void;
  updateSelectedGroups: (groups: Group[]) => void;
  updateSelectedPermissions: (permissions: Permission[]) => void;
  handleSave: () => Promise<void>;
  resetChanges: () => void;
  refetch: () => Promise<any>;
}

export function useRoleDrawer(
  roleId: string | null,
  open: boolean
): UseRoleDrawerReturn {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Query users and groups with their roles to find which users/groups have this role
  const { data, loading, error, refetch } = useGetRoleWithUsersAndGroupsQuery({
    skip: !open,
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Query roles with permissions to get current role's permissions
  const { data: permissionsData, loading: permissionsLoading, refetch: refetchPermissions } = useGetRolesWithPermissionsQuery({
    skip: !open || !roleId,
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: true,
  });

  // Extract role from roleId by finding it in the users/groups data
  // The role name can be found in any user or group that has this role
  const role = useMemo(() => {
    if (!roleId) return null;
    
    // Try to find role name from users
    const userWithRole = data?.users?.edges
      ?.map(e => e.node)
      .find((user) => user.roles.some((r) => r.id === roleId));
    
    if (userWithRole) {
      const roleData = userWithRole.roles.find((r) => r.id === roleId);
      if (roleData) {
        return {
          id: roleId,
          name: roleData.name,
        };
      }
    }
    
    // Try to find role name from groups
    const groupWithRole = data?.groups?.edges
      ?.map(e => e.node)
      .find((group) => group.roles.some((r) => r.id === roleId));
    
    if (groupWithRole) {
      const roleData = groupWithRole.roles.find((r) => r.id === roleId);
      if (roleData) {
        return {
          id: roleId,
          name: roleData.name,
        };
      }
    }
    
    // Fallback: return role with empty name (shouldn't happen in normal flow)
    return {
      id: roleId,
      name: "",
    };
  }, [roleId, data]);

  // Extract original users and groups that have this role
  const originalUsers = useMemo(() => {
    if (!role || !data?.users?.edges) return [];
    return data.users.edges
      .map(e => e.node)
      .filter((user) => user.roles.some((r) => r.id === role.id))
      .map((user) => ({
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        enabled: user.enabled,
      }));
  }, [data?.users, role]);

  const originalGroups = useMemo(() => {
    if (!role || !data?.groups?.edges) return [];
    return data.groups.edges
      .map(e => e.node)
      .filter((group) => group.roles.some((r) => r.id === role.id))
      .map((group) => ({
        id: group.id,
        name: group.name,
        description: group.description,
      }));
  }, [data?.groups, role]);

  // Extract original permissions for this role
  const originalPermissions = useMemo(() => {
    if (!role || !permissionsData?.roles?.edges) return [];
    const roleWithPermissions = permissionsData.roles.edges
      .map(e => e.node)
      .find((r) => r.id === role.id);
    return (roleWithPermissions?.permissions || []).map((p) => ({
      id: p.id,
      name: p.name,
    }));
  }, [role, permissionsData]);

  // Local form state
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [selectedGroups, setSelectedGroups] = useState<Group[]>([]);
  const [selectedPermissions, setSelectedPermissions] = useState<Permission[]>([]);
  const [saving, setSaving] = useState(false);

  // Mutation hooks
  const [assignRoleToUserMutation] = useAssignRoleToUserMutation();
  const [removeRoleFromUserMutation] = useRemoveRoleFromUserMutation();
  const [assignRoleToGroupMutation] = useAssignRoleToGroupMutation();
  const [removeRoleFromGroupMutation] = useRemoveRoleFromGroupMutation();
  const [assignPermissionToRoleMutation] = useAssignPermissionToRoleMutation();
  const [removePermissionFromRoleMutation] = useRemovePermissionFromRoleMutation();

  // Initialize form state when role data loads
  useEffect(() => {
    if (role) {
      setSelectedUsers(originalUsers.map(u => ({
        id: u.id,
        email: u.email,
        displayName: u.displayName,
        enabled: u.enabled,
      })));
      setSelectedGroups(originalGroups.map(g => ({
        id: g.id,
        name: g.name,
        description: g.description,
      })));
      setSelectedPermissions(originalPermissions.map(p => ({
        id: p.id,
        name: p.name,
      })));
    }
  }, [role, originalUsers, originalGroups, originalPermissions]);

  // Calculate hasChanges
  const hasChanges = useMemo(() => {
    if (!role) return false;
    
    const originalUserIds = new Set(originalUsers.map(u => u.id));
    const currentUserIds = new Set(selectedUsers.map(u => u.id));
    const usersChanged = 
      selectedUsers.length !== originalUsers.length ||
      selectedUsers.some(u => !originalUserIds.has(u.id)) ||
      originalUsers.some(u => !currentUserIds.has(u.id));
    
    const originalGroupIds = new Set(originalGroups.map(g => g.id));
    const currentGroupIds = new Set(selectedGroups.map(g => g.id));
    const groupsChanged = 
      selectedGroups.length !== originalGroups.length ||
      selectedGroups.some(g => !originalGroupIds.has(g.id)) ||
      originalGroups.some(g => !currentGroupIds.has(g.id));
    
    const originalPermissionIds = new Set(originalPermissions.map(p => p.id));
    const currentPermissionIds = new Set(selectedPermissions.map(p => p.id));
    const permissionsChanged = 
      selectedPermissions.length !== originalPermissions.length ||
      selectedPermissions.some(p => !originalPermissionIds.has(p.id)) ||
      originalPermissions.some(p => !currentPermissionIds.has(p.id));
    
    return usersChanged || groupsChanged || permissionsChanged;
  }, [role, originalUsers, originalGroups, originalPermissions, selectedUsers, selectedGroups, selectedPermissions]);

  // Handlers for updating local state
  const updateSelectedUsers = useCallback((users: User[]) => {
    setSelectedUsers(users);
  }, []);

  const updateSelectedGroups = useCallback((groups: Group[]) => {
    setSelectedGroups(groups);
  }, []);

  const updateSelectedPermissions = useCallback((permissions: Permission[]) => {
    setSelectedPermissions(permissions);
  }, []);

  // Reset changes to original state
  const resetChanges = useCallback(() => {
    if (role) {
      setSelectedUsers(originalUsers.map(u => ({
        id: u.id,
        email: u.email,
        displayName: u.displayName,
        enabled: u.enabled,
      })));
      setSelectedGroups(originalGroups.map(g => ({
        id: g.id,
        name: g.name,
        description: g.description,
      })));
      setSelectedPermissions(originalPermissions.map(p => ({
        id: p.id,
        name: p.name,
      })));
    }
  }, [role, originalUsers, originalGroups, originalPermissions]);

  // Handle save - calculate differences and execute mutations
  const handleSave = useCallback(async () => {
    if (!role || !roleId || saving) return;
    
    setSaving(true);
    
    try {
      // Calculate differences for users
      const originalUserIds = new Set(originalUsers.map(u => u.id));
      const currentUserIds = new Set(selectedUsers.map(u => u.id));
      const usersToAdd = selectedUsers.filter(u => !originalUserIds.has(u.id)).map(u => u.id);
      const usersToRemove = Array.from(originalUserIds).filter(id => !currentUserIds.has(id));

      // Calculate differences for groups
      const originalGroupIds = new Set(originalGroups.map(g => g.id));
      const currentGroupIds = new Set(selectedGroups.map(g => g.id));
      const groupsToAdd = selectedGroups.filter(g => !originalGroupIds.has(g.id)).map(g => g.id);
      const groupsToRemove = Array.from(originalGroupIds).filter(id => !currentGroupIds.has(id));

      // Calculate differences for permissions
      const originalPermissionIds = new Set(originalPermissions.map(p => p.id));
      const currentPermissionIds = new Set(selectedPermissions.map(p => p.id));
      const permissionsToAdd = selectedPermissions.filter(p => !originalPermissionIds.has(p.id)).map(p => p.id);
      const permissionsToRemove = Array.from(originalPermissionIds).filter(id => !currentPermissionIds.has(id));

      // Execute all mutations in parallel
      const mutationPromises: Promise<any>[] = [];
      
      // User mutations
      for (const userId of usersToAdd) {
        mutationPromises.push(
          assignRoleToUserMutation({
            variables: { userId, roleId },
          })
        );
      }
      
      for (const userId of usersToRemove) {
        mutationPromises.push(
          removeRoleFromUserMutation({
            variables: { userId, roleId },
          })
        );
      }

      // Group mutations
      for (const groupId of groupsToAdd) {
        mutationPromises.push(
          assignRoleToGroupMutation({
            variables: { groupId, roleId },
          })
        );
      }
      
      for (const groupId of groupsToRemove) {
        mutationPromises.push(
          removeRoleFromGroupMutation({
            variables: { groupId, roleId },
          })
        );
      }

      // Permission mutations
      for (const permissionId of permissionsToAdd) {
        mutationPromises.push(
          assignPermissionToRoleMutation({
            variables: { roleId, permissionId },
          })
        );
      }
      
      for (const permissionId of permissionsToRemove) {
        mutationPromises.push(
          removePermissionFromRoleMutation({
            variables: { roleId, permissionId },
          })
        );
      }

      // Execute all mutations
      if (mutationPromises.length > 0) {
        await Promise.all(mutationPromises);
      }

      // Refetch to get updated data
      await Promise.all([refetch(), refetchPermissions()]);
      
      // Show success message
      toast.success(t("roleManagement.toast.roleUpdated", { name: role.name || roleId }));
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        t("roleManagement.toast.roleUpdateError")
      );
      toast.error(errorMessage);
      throw err;
    } finally {
      setSaving(false);
    }
  }, [
    role,
    roleId,
    originalUsers,
    originalGroups,
    originalPermissions,
    selectedUsers,
    selectedGroups,
    selectedPermissions,
    saving,
    assignRoleToUserMutation,
    removeRoleFromUserMutation,
    assignRoleToGroupMutation,
    removeRoleFromGroupMutation,
    assignPermissionToRoleMutation,
    removePermissionFromRoleMutation,
    refetch,
    refetchPermissions,
    toast,
    t,
  ]);

  return {
    // Data
    role,
    loading: loading || permissionsLoading,
    error,
    
    // Form state
    selectedUsers,
    selectedGroups,
    selectedPermissions,
    hasChanges,
    saving,
    
    // Handlers
    updateSelectedUsers,
    updateSelectedGroups,
    updateSelectedPermissions,
    handleSave,
    resetChanges,
    refetch,
  };
}
