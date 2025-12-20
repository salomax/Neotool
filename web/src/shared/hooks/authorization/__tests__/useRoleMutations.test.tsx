import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useRoleMutations } from '../useRoleMutations';

// Mock the GraphQL operations
vi.mock('@/lib/graphql/operations/authorization-management/mutations.generated', () => ({
  useCreateRoleMutation: vi.fn(),
  useUpdateRoleMutation: vi.fn(),
  useDeleteRoleMutation: vi.fn(),
  useAssignPermissionToRoleMutation: vi.fn(),
  useRemovePermissionFromRoleMutation: vi.fn(),
  useAssignRoleToUserMutation: vi.fn(),
  useRemoveRoleFromUserMutation: vi.fn(),
  useAssignRoleToGroupMutation: vi.fn(),
  useRemoveRoleFromGroupMutation: vi.fn(),
}));

vi.mock('@/shared/hooks/mutations', () => ({
  useMutationWithRefetch: vi.fn(),
}));

import {
  useCreateRoleMutation,
  useUpdateRoleMutation,
  useDeleteRoleMutation,
  useAssignPermissionToRoleMutation,
  useRemovePermissionFromRoleMutation,
  useAssignRoleToGroupMutation,
  useRemoveRoleFromGroupMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';

describe('useRoleMutations', () => {
  const mockExecuteMutation = vi.fn();
  const mockOnRefetch = vi.fn();
  const mockOnRoleSaved = vi.fn();
  const mockOnRoleDeleted = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementations
    (useCreateRoleMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateRoleMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteRoleMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useAssignPermissionToRoleMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useRemovePermissionFromRoleMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);


    (useAssignRoleToGroupMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useRemoveRoleFromGroupMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useMutationWithRefetch as any).mockImplementation((options: any) => {
      const executeMutation = async (...args: any[]) => {
        const result = await mockExecuteMutation(...args);
        // Call onRefetch if provided and result has data (matching real implementation)
        if (result?.data && options?.onRefetch) {
          options.onRefetch();
        }
        return result;
      };
      return {
        executeMutation,
        isMutationInFlight: vi.fn(() => false),
      };
    });
  });

  it('should return all mutation functions and loading states', () => {
    const { result } = renderHook(() => useRoleMutations());

    expect(result.current.createRole).toBeDefined();
    expect(result.current.updateRole).toBeDefined();
    expect(result.current.deleteRole).toBeDefined();
    expect(result.current.assignPermissionToRole).toBeDefined();
    expect(result.current.removePermissionFromRole).toBeDefined();
    expect(result.current.assignRoleToGroup).toBeDefined();
    expect(result.current.removeRoleFromGroup).toBeDefined();

    expect(result.current.createLoading).toBe(false);
    expect(result.current.updateLoading).toBe(false);
    expect(result.current.deleteLoading).toBe(false);
  });

  it('should create role successfully', async () => {
    const mockCreateRoleMutation = vi.fn();
    (useCreateRoleMutation as any).mockReturnValue([
      mockCreateRoleMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        createRole: {
          id: 'role-1',
          name: 'Test Role',
        },
      },
    });

    const { result } = renderHook(() =>
      useRoleMutations({
        onRoleSaved: mockOnRoleSaved,
      })
    );

    await act(async () => {
      const role = await result.current.createRole({ name: 'Test Role' });
      expect(role).toEqual({
        id: 'role-1',
        name: 'Test Role',
      });
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockCreateRoleMutation,
      { input: { name: 'Test Role' } },
      'create-role'
    );
    expect(mockOnRoleSaved).toHaveBeenCalled();
  });

  it('should update role successfully', async () => {
    const mockUpdateRoleMutation = vi.fn();
    (useUpdateRoleMutation as any).mockReturnValue([
      mockUpdateRoleMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        updateRole: {
          id: 'role-1',
          name: 'Updated Role',
        },
      },
    });

    const { result } = renderHook(() =>
      useRoleMutations({
        onRoleSaved: mockOnRoleSaved,
      })
    );

    await act(async () => {
      await result.current.updateRole('role-1', { name: 'Updated Role' });
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockUpdateRoleMutation,
      { roleId: 'role-1', input: { name: 'Updated Role' } },
      'update-role-role-1'
    );
    expect(mockOnRoleSaved).toHaveBeenCalled();
  });

  it('should delete role successfully', async () => {
    const mockDeleteRoleMutation = vi.fn();
    (useDeleteRoleMutation as any).mockReturnValue([
      mockDeleteRoleMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        deleteRole: {
          id: 'role-1',
        },
      },
    });

    const { result } = renderHook(() =>
      useRoleMutations({
        onRoleDeleted: mockOnRoleDeleted,
      })
    );

    await act(async () => {
      await result.current.deleteRole('role-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockDeleteRoleMutation,
      { roleId: 'role-1' },
      'delete-role-role-1'
    );
    expect(mockOnRoleDeleted).toHaveBeenCalled();
  });

  it('should assign permission to role', async () => {
    const mockAssignPermissionMutation = vi.fn();
    (useAssignPermissionToRoleMutation as any).mockReturnValue([
      mockAssignPermissionMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        assignPermissionToRole: {
          id: 'role-1',
        },
      },
    });

    const { result } = renderHook(() => useRoleMutations());

    await act(async () => {
      await result.current.assignPermissionToRole('role-1', 'perm-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockAssignPermissionMutation,
      { roleId: 'role-1', permissionId: 'perm-1' },
      'assign-permission-role-1-perm-1'
    );
  });

  it('should remove permission from role', async () => {
    const mockRemovePermissionMutation = vi.fn();
    (useRemovePermissionFromRoleMutation as any).mockReturnValue([
      mockRemovePermissionMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        removePermissionFromRole: {
          id: 'role-1',
        },
      },
    });

    const { result } = renderHook(() => useRoleMutations());

    await act(async () => {
      await result.current.removePermissionFromRole('role-1', 'perm-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockRemovePermissionMutation,
      { roleId: 'role-1', permissionId: 'perm-1' },
      'remove-permission-role-1-perm-1'
    );
  });

  it('should handle errors correctly', async () => {
    mockExecuteMutation.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useRoleMutations());

    await act(async () => {
      // extractErrorMessage might return the original error message or the fallback
      await expect(
        result.current.createRole({ name: 'Test Role' })
      ).rejects.toThrow();
    });
  });

  it('should call onRefetch when provided', async () => {
    mockExecuteMutation.mockResolvedValue({
      data: {
        createRole: {
          id: 'role-1',
          name: 'Test Role',
        },
      },
    });

    const { result } = renderHook(() =>
      useRoleMutations({
        onRefetch: mockOnRefetch,
      })
    );

    await act(async () => {
      await result.current.createRole({ name: 'Test Role' });
    });

    expect(mockOnRefetch).toHaveBeenCalled();
  });

  it('should return correct loading states', () => {
    (useCreateRoleMutation as any).mockReturnValue([
      vi.fn(),
      { loading: true },
    ]);

    const { result } = renderHook(() => useRoleMutations());

    expect(result.current.createLoading).toBe(true);
  });
});

