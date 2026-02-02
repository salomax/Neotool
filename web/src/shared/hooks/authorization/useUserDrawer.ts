"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useQuery, useMutation } from "@apollo/client/react";
import {
  GetUserWithRelationshipsDocument,
  GetUsersDocument,
  type GetUserWithRelationshipsQuery,
  type GetUserWithRelationshipsQueryVariables,
} from "@/lib/graphql/operations/authorization-management/queries.generated";
import {
  AssignGroupToUserDocument,
  RemoveGroupFromUserDocument,
  UpdateUserDocument,
  type AssignGroupToUserMutation,
  type AssignGroupToUserMutationVariables,
  type RemoveGroupFromUserMutation,
  type RemoveGroupFromUserMutationVariables,
  type UpdateUserMutation,
  type UpdateUserMutationVariables,
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
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
  groups: Array<{ id: string; name: string; description: string | null }>;
  roles: Array<{ id: string; name: string }>;
};

export interface UseUserDrawerReturn {
  // Data
  user: User | null;
  loading: boolean;
  error: Error | undefined;
  
  // Form state
  displayName: string;
  email: string;
  selectedGroups: Array<{ id: string; name: string; description: string | null }>;
  selectedRoles: Array<{ id: string; name: string }>;
  hasChanges: boolean;
  saving: boolean;
  
  // Handlers
  updateDisplayName: (value: string) => void;
  updateEmail: (value: string) => void;
  updateSelectedGroups: (groups: Array<{ id: string; name: string; description: string | null }>) => void;
  updateSelectedRoles: (roles: Array<{ id: string; name: string }>) => void;
  handleSave: () => Promise<void>;
  resetChanges: () => void;
  refetch: () => Promise<any>;
}

export function useUserDrawer(
  userId: string | null,
  open: boolean
): UseUserDrawerReturn {
  const { t } = useTranslation(authorizationManagementTranslations);
  const toast = useToast();

  // Query user with relationships
  const { data, loading, error, refetch } = useQuery<
    GetUserWithRelationshipsQuery,
    GetUserWithRelationshipsQueryVariables
  >(GetUserWithRelationshipsDocument, {
    skip: !open || !userId,
    variables: { id: userId! },
    fetchPolicy: 'network-only',
    notifyOnNetworkStatusChange: false, // Prevent loading state during refetches to avoid drawer blink
  });

  // Extract user from query result
  const user = useMemo(() => {
    if (!data?.user || !userId) return null;
    return {
      id: data.user.id,
      email: data.user.email,
      displayName: data.user.displayName,
      enabled: data.user.enabled,
      avatarUrl: data.user.avatarUrl,
      createdAt: data.user.createdAt,
      updatedAt: data.user.updatedAt,
      groups: data.user.groups.map(g => ({
        id: g.id,
        name: g.name,
        description: g.description,
      })),
      roles: data.user.roles.map(r => ({
        id: r.id,
        name: r.name,
      })),
    };
  }, [data?.user, userId]);

  // Local form state
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [selectedGroups, setSelectedGroups] = useState<Array<{ id: string; name: string; description: string | null }>>([]);
  const [selectedRoles, setSelectedRoles] = useState<Array<{ id: string; name: string }>>([]);
  const [saving, setSaving] = useState(false);

  // Mutation hooks
  const [assignGroupToUserMutation] = useMutation<
    AssignGroupToUserMutation,
    AssignGroupToUserMutationVariables
  >(AssignGroupToUserDocument);
  const [removeGroupFromUserMutation] = useMutation<
    RemoveGroupFromUserMutation,
    RemoveGroupFromUserMutationVariables
  >(RemoveGroupFromUserDocument);
  const [updateUserMutation] = useMutation<
    UpdateUserMutation,
    UpdateUserMutationVariables
  >(UpdateUserDocument);

  // Initialize form state when user data loads
  useEffect(() => {
    if (user) {
      setDisplayName(user.displayName || "");
      setEmail(user.email);
      setSelectedGroups(user.groups.map(g => ({
        id: g.id,
        name: g.name,
        description: g.description,
      })));
      setSelectedRoles(user.roles.map(r => ({
        id: r.id,
        name: r.name,
      })));
    }
  }, [user]);

  // Calculate hasChanges
  const hasChanges = useMemo(() => {
    if (!user) return false;
    
    const displayNameChanged = displayName !== (user.displayName || "");
    // Email is immutable, so don't check for email changes
    
    const originalGroupIds = new Set(user.groups.map(g => g.id));
    const currentGroupIds = new Set(selectedGroups.map(g => g.id));
    const groupsChanged = 
      selectedGroups.length !== user.groups.length ||
      selectedGroups.some(g => !originalGroupIds.has(g.id)) ||
      user.groups.some(g => !currentGroupIds.has(g.id));
    
    // Roles are readonly (assigned through groups only), so don't include in hasChanges
    return displayNameChanged || groupsChanged;
  }, [user, displayName, selectedGroups]);

  // Handlers for updating local state
  const updateDisplayName = useCallback((value: string) => {
    setDisplayName(value);
  }, []);

  const updateEmail = useCallback((value: string) => {
    setEmail(value);
  }, []);

  const updateSelectedGroups = useCallback((groups: Array<{ id: string; name: string; description: string | null }>) => {
    setSelectedGroups(groups);
  }, []);

  const updateSelectedRoles = useCallback((roles: Array<{ id: string; name: string }>) => {
    setSelectedRoles(roles);
  }, []);

  // Reset changes to original state
  const resetChanges = useCallback(() => {
    if (user) {
      setDisplayName(user.displayName || "");
      setEmail(user.email);
      setSelectedGroups(user.groups.map(g => ({
        id: g.id,
        name: g.name,
        description: g.description,
      })));
      setSelectedRoles(user.roles.map(r => ({
        id: r.id,
        name: r.name,
      })));
    }
  }, [user]);

  // Handle save - calculate differences and execute mutations
  const handleSave = useCallback(async () => {
    if (!user || !userId || saving) return;
    
    setSaving(true);
    
    try {
      // Calculate differences for groups
      const originalGroupIds = new Set(user.groups.map(g => g.id));
      const currentGroupIds = new Set(selectedGroups.map(g => g.id));
      const groupsToAdd = selectedGroups.filter(g => !originalGroupIds.has(g.id)).map(g => g.id);
      const groupsToRemove = Array.from(originalGroupIds).filter(id => !currentGroupIds.has(id));

      // Roles are readonly (assigned through groups only), so no role mutations

      // Execute all mutations in parallel
      const mutationPromises: Promise<any>[] = [];
      
      // Group mutations
      for (const groupId of groupsToAdd) {
        mutationPromises.push(
          assignGroupToUserMutation({
            variables: { userId, groupId },
            // Only refetch list query - drawer query will be refetched when drawer reopens
            // Removing drawer query from refetchQueries prevents drawer blink during save
            refetchQueries: ['GetUsers'],
            awaitRefetchQueries: true,
          })
        );
      }
      
      for (const groupId of groupsToRemove) {
        mutationPromises.push(
          removeGroupFromUserMutation({
            variables: { userId, groupId },
            // Only refetch list query - drawer query will be refetched when drawer reopens
            // Removing drawer query from refetchQueries prevents drawer blink during save
            refetchQueries: ['GetUsers'],
            awaitRefetchQueries: true,
          })
        );
      }

      // Check if displayName changed (email is immutable)
      const displayNameChanged = displayName !== (user.displayName || "");
      
      if (displayNameChanged) {
        mutationPromises.push(
          updateUserMutation({
            variables: {
              userId,
              input: {
                displayName: displayName,
              },
            },
            // Only refetch list query - drawer query will be refetched when drawer reopens
            // Removing drawer query from refetchQueries prevents drawer blink during save
            refetchQueries: ['GetUsers'],
            awaitRefetchQueries: true,
          })
        );
      }

      // Execute all mutations
      if (mutationPromises.length > 0) {
        await Promise.all(mutationPromises);
      }

      // Mutations already refetch queries via refetchQueries with awaitRefetchQueries: true
      // No need for additional refetch call that causes drawer to blink
      
      // Show success message
      toast.success(t("userManagement.drawer.saveSuccess"));
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        t("userManagement.drawer.saveError")
      );
      toast.error(errorMessage);
      throw err;
    } finally {
      setSaving(false);
    }
  }, [
    user,
    userId,
    displayName,
    selectedGroups,
    saving,
    assignGroupToUserMutation,
    removeGroupFromUserMutation,
    updateUserMutation,
    toast,
    t,
  ]);

  return {
    // Data
    user,
    loading,
    error,
    
    // Form state
    displayName,
    email,
    selectedGroups,
    selectedRoles,
    hasChanges,
    saving,
    
    // Handlers
    updateDisplayName,
    updateEmail,
    updateSelectedGroups,
    updateSelectedRoles,
    handleSave,
    resetChanges,
    refetch,
  };
}
