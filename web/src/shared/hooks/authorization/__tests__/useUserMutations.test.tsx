import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useUserMutations } from '../useUserMutations';

// Mock the GraphQL operations
vi.mock('@/lib/graphql/operations/authorization-management/mutations.generated', () => ({
  useEnableUserMutation: vi.fn(),
  useDisableUserMutation: vi.fn(),
  useAssignGroupToUserMutation: vi.fn(),
  useRemoveGroupFromUserMutation: vi.fn(),
  useAssignRoleToUserMutation: vi.fn(),
  useRemoveRoleFromUserMutation: vi.fn(),
}));

vi.mock('@/shared/hooks/mutations', () => ({
  useMutationWithRefetch: vi.fn(),
}));

import {
  useEnableUserMutation,
  useDisableUserMutation,
  useAssignGroupToUserMutation,
  useRemoveGroupFromUserMutation,
} from '@/lib/graphql/operations/authorization-management/mutations.generated';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';

// Run sequentially to avoid overlapping hook executions across threads
describe.sequential('useUserMutations', () => {
  const mockExecuteMutation = vi.fn();
  const mockOnRefetch = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementations
    (useEnableUserMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDisableUserMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useAssignGroupToUserMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useRemoveGroupFromUserMutation as any).mockReturnValue([
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
    const { result } = renderHook(() => useUserMutations());

    expect(result.current.enableUser).toBeDefined();
    expect(result.current.disableUser).toBeDefined();
    expect(result.current.assignGroupToUser).toBeDefined();
    expect(result.current.removeGroupFromUser).toBeDefined();

    expect(result.current.enableLoading).toBe(false);
    expect(result.current.disableLoading).toBe(false);
    expect(result.current.assignGroupLoading).toBe(false);
    expect(result.current.removeGroupLoading).toBe(false);
  });

  it('should enable user successfully', async () => {
    const mockEnableUserMutation = vi.fn();
    (useEnableUserMutation as any).mockReturnValue([
      mockEnableUserMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        enableUser: {
          id: 'user-1',
        },
      },
    });

    const { result } = renderHook(() => useUserMutations());

    await act(async () => {
      await result.current.enableUser('user-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockEnableUserMutation,
      { userId: 'user-1' },
      'user-1'
    );
  });

  it('should disable user successfully', async () => {
    const mockDisableUserMutation = vi.fn();
    (useDisableUserMutation as any).mockReturnValue([
      mockDisableUserMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        disableUser: {
          id: 'user-1',
        },
      },
    });

    const { result } = renderHook(() => useUserMutations());

    await act(async () => {
      await result.current.disableUser('user-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockDisableUserMutation,
      { userId: 'user-1' },
      'user-1'
    );
  });

  it('should assign group to user', async () => {
    const mockAssignGroupMutation = vi.fn();
    (useAssignGroupToUserMutation as any).mockReturnValue([
      mockAssignGroupMutation,
      { loading: false },
    ]);

    mockExecuteMutation.mockResolvedValue({
      data: {
        assignGroupToUser: {
          id: 'user-1',
        },
      },
    });

    const { result } = renderHook(() => useUserMutations());

    await act(async () => {
      await result.current.assignGroupToUser('user-1', 'group-1');
    });

    expect(mockExecuteMutation).toHaveBeenCalledWith(
      mockAssignGroupMutation,
      { userId: 'user-1', groupId: 'group-1' },
      'assign-group-user-1-group-1'
    );
  });

  it('should call onRefetch when provided', async () => {
    mockExecuteMutation.mockResolvedValue({
      data: {
        enableUser: {
          id: 'user-1',
        },
      },
    });

    const { result } = renderHook(() =>
      useUserMutations({
        onRefetch: mockOnRefetch,
      })
    );

    await act(async () => {
      await result.current.enableUser('user-1');
    });

    expect(mockOnRefetch).toHaveBeenCalled();
  });
});
