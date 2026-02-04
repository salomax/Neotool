import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useMutation } from '@apollo/client/react';
import { useGroupMutations } from '../useGroupMutations';

vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(),
}));

vi.mock('@/shared/hooks/mutations', () => ({
  useMutationWithRefetch: vi.fn(),
}));

import { useMutationWithRefetch } from '@/shared/hooks/mutations';

// Run sequentially to avoid overlapping hook executions across threads
describe.sequential('useGroupMutations', () => {
  const mockExecuteMutation = vi.fn();
  const mockOnRefetch = vi.fn();
  const mockOnGroupSaved = vi.fn();
  const mockOnGroupDeleted = vi.fn();

  let mockCreateGroupMutation: any;
  let mockUpdateGroupMutation: any;
  let mockDeleteGroupMutation: any;
  let mockAssignRoleToGroupMutation: any;
  let mockRemoveRoleFromGroupMutation: any;

  const setupUseMutationChain = (overrides?: {
    createLoading?: boolean;
    updateLoading?: boolean;
    deleteLoading?: boolean;
    assignRoleLoading?: boolean;
    removeRoleLoading?: boolean;
  }) => {
    mockCreateGroupMutation = vi.fn();
    mockUpdateGroupMutation = vi.fn();
    mockDeleteGroupMutation = vi.fn();
    mockAssignRoleToGroupMutation = vi.fn();
    mockRemoveRoleFromGroupMutation = vi.fn();

    (useMutation as any)
      .mockReturnValueOnce([
        mockCreateGroupMutation,
        { loading: overrides?.createLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockUpdateGroupMutation,
        { loading: overrides?.updateLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockDeleteGroupMutation,
        { loading: overrides?.deleteLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockAssignRoleToGroupMutation,
        { loading: overrides?.assignRoleLoading ?? false },
      ])
      .mockReturnValueOnce([
        mockRemoveRoleFromGroupMutation,
        { loading: overrides?.removeRoleLoading ?? false },
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
    const { result } = renderHook(() => useGroupMutations());

    expect(result.current.createGroup).toBeDefined();
    expect(result.current.updateGroup).toBeDefined();
    expect(result.current.deleteGroup).toBeDefined();
    expect(result.current.assignRoleToGroup).toBeDefined();
    expect(result.current.removeRoleFromGroup).toBeDefined();

    expect(result.current.createLoading).toBe(false);
    expect(result.current.updateLoading).toBe(false);
    expect(result.current.deleteLoading).toBe(false);
    expect(result.current.assignRoleLoading).toBe(false);
    expect(result.current.removeRoleLoading).toBe(false);
  });

  it('should create group successfully', async () => {
    mockExecuteMutation.mockResolvedValue({
      data: {
        createGroup: {
          id: 'group-1',
          name: 'Test Group',
        },
      },
    });

    const { result } = renderHook(() =>
      useGroupMutations({
        onGroupSaved: mockOnGroupSaved,
      })
    );

    await act(async () => {
      await result.current.createGroup({
        name: 'Test Group',
        description: 'Test Description',
      });
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockCreateGroupMutation,
      {
        input: {
          name: 'Test Group',
          description: 'Test Description',
          userIds: [],
        },
      },
      'create-group'
    );
    expect(mockOnGroupSaved).toHaveBeenCalled();
  });

  it('should update group successfully', async () => {
    mockExecuteMutation.mockResolvedValue({
      data: {
        updateGroup: {
          id: 'group-1',
          name: 'Updated Group',
        },
      },
    });

    const { result } = renderHook(() =>
      useGroupMutations({
        onGroupSaved: mockOnGroupSaved,
      })
    );

    await act(async () => {
      await result.current.updateGroup('group-1', {
        name: 'Updated Group',
        description: 'Updated Description',
        userIds: ['user-1'],
      });
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockUpdateGroupMutation,
      {
        groupId: 'group-1',
        input: {
          name: 'Updated Group',
          description: 'Updated Description',
          userIds: ['user-1'],
        },
      },
      'update-group-group-1'
    );
    expect(mockOnGroupSaved).toHaveBeenCalled();
  });

  it('should delete group successfully', async () => {
    mockExecuteMutation.mockResolvedValue({
      data: {
        deleteGroup: {
          id: 'group-1',
        },
      },
    });

    const { result } = renderHook(() =>
      useGroupMutations({
        onGroupDeleted: mockOnGroupDeleted,
      })
    );

    await act(async () => {
      await result.current.deleteGroup('group-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockDeleteGroupMutation,
      { groupId: 'group-1' },
      'delete-group-group-1'
    );
    expect(mockOnGroupDeleted).toHaveBeenCalled();
  });

  it('should assign role to group', async () => {
    mockExecuteMutation.mockResolvedValue({
      data: {
        assignRoleToGroup: {
          id: 'group-1',
        },
      },
    });

    const { result } = renderHook(() => useGroupMutations());

    await act(async () => {
      await result.current.assignRoleToGroup('group-1', 'role-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockAssignRoleToGroupMutation,
      { groupId: 'group-1', roleId: 'role-1' },
      'assign-role-group-1-role-1'
    );
  });

  it('should handle errors correctly', async () => {
    mockExecuteMutation.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useGroupMutations());

    await act(async () => {
      // extractErrorMessage might return the original error message or the fallback
      await expect(result.current.createGroup({ name: 'Test Group' })).rejects.toThrow();
    });
  });

  it('should call onRefetch when provided', async () => {
    mockExecuteMutation.mockResolvedValue({
      data: {
        createGroup: {
          id: 'group-1',
          name: 'Test Group',
        },
      },
    });

    const { result } = renderHook(() =>
      useGroupMutations({
        onRefetch: mockOnRefetch,
      })
    );

    await act(async () => {
      await result.current.createGroup({ name: 'Test Group' });
    });

    expect(mockOnRefetch).toHaveBeenCalled();
  });
});
