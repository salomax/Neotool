import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useMutation } from '@apollo/client/react';
import { useRoleMutations } from '../useRoleMutations';

vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(),
}));

vi.mock('@/shared/hooks/mutations', () => ({
  useMutationWithRefetch: vi.fn(),
}));

import { useMutationWithRefetch } from '@/shared/hooks/mutations';

// Run sequentially to avoid overlapping hook executions across threads
describe.sequential('useRoleMutations', () => {
  const mockExecuteMutation = vi.fn();
  const mockOnRefetch = vi.fn();
  const mockOnRoleSaved = vi.fn();
  const mockOnRoleDeleted = vi.fn();

  let mockCreateRoleMutation: any;
  let mockUpdateRoleMutation: any;
  let mockDeleteRoleMutation: any;
  let mockAssignPermissionMutation: any;
  let mockRemovePermissionMutation: any;
  let mockAssignRoleToGroupMutation: any;
  let mockRemoveRoleFromGroupMutation: any;

  const setupUseMutationChain = (overrides?: {
    createLoading?: boolean;
    updateLoading?: boolean;
    deleteLoading?: boolean;
    assignPermissionLoading?: boolean;
    removePermissionLoading?: boolean;
    assignRoleToGroupLoading?: boolean;
    removeRoleFromGroupLoading?: boolean;
  }) => {
    mockCreateRoleMutation = vi.fn();
    mockUpdateRoleMutation = vi.fn();
    mockDeleteRoleMutation = vi.fn();
    mockAssignPermissionMutation = vi.fn();
    mockRemovePermissionMutation = vi.fn();
    mockAssignRoleToGroupMutation = vi.fn();
    mockRemoveRoleFromGroupMutation = vi.fn();

    (useMutation as any)
      .mockReturnValueOnce([
        mockCreateRoleMutation,
        { loading: overrides?.createLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockUpdateRoleMutation,
        { loading: overrides?.updateLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockDeleteRoleMutation,
        { loading: overrides?.deleteLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockAssignPermissionMutation,
        { loading: overrides?.assignPermissionLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockRemovePermissionMutation,
        { loading: overrides?.removePermissionLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockAssignRoleToGroupMutation,
        { loading: overrides?.assignRoleToGroupLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockRemoveRoleFromGroupMutation,
        { loading: overrides?.removeRoleFromGroupLoading ?? false },
      ]);
  };

  beforeEach(() => {
    vi.clearAllMocks();
    setupUseMutationChain();

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
      await expect(result.current.createRole({ name: 'Test Role' })).rejects.toThrow();
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
    (useMutation as any).mockReset();
    setupUseMutationChain({ createLoading: true });

    const { result } = renderHook(() => useRoleMutations());

    expect(result.current.createLoading).toBe(true);
  });
});
