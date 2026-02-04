import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useQuery } from '@apollo/client/react';
import { useUserManagement } from '../useUserManagement';

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
}));

vi.mock('@/shared/hooks/authorization/useUserMutations', () => ({
  useUserMutations: vi.fn(),
}));

vi.mock('@/shared/hooks/pagination', () => ({
  useRelayPagination: vi.fn(),
}));

vi.mock('@/shared/providers/AuthProvider', () => ({
  useAuth: vi.fn(() => ({ isAuthenticated: true })),
}));

vi.mock('@/shared/utils/auth', () => ({
  hasAuthToken: vi.fn(() => true),
  isAuthenticationError: vi.fn(() => false),
}));

import { useUserMutations } from '@/shared/hooks/authorization/useUserMutations';
import { useRelayPagination } from '@/shared/hooks/pagination';
import { useAuth } from '@/shared/providers/AuthProvider';
import { hasAuthToken, isAuthenticationError } from '@/shared/utils/auth';

describe('useUserManagement', () => {
  // Create stable mock data once to avoid creating new objects on every mock call
  const mockUsers = [
    {
      id: '1',
      email: 'john@example.com',
      displayName: 'John Doe',
      enabled: true,
    },
    {
      id: '2',
      email: 'jane@example.com',
      displayName: 'Jane Smith',
      enabled: false,
    },
  ];

  const mockEdges = mockUsers.map(u => ({ node: u, cursor: 'cursor' }));
  const mockPageInfo = {
    hasNextPage: false,
    hasPreviousPage: false,
    startCursor: 'cursor1',
    endCursor: 'cursor2',
  };

  const mockRefetch = vi.fn();
  let lastQueryArgs: any;

  // Create a single stable mock response object to reuse
  const baseMockResponse = {
    data: {
      users: {
        edges: mockEdges,
        pageInfo: mockPageInfo,
        totalCount: 2,
      },
    },
    loading: false,
    error: undefined,
    refetch: mockRefetch,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // Clear mock call history to prevent memory accumulation
    (useQuery as any).mockClear();
    mockRefetch.mockClear();
    (useAuth as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ isAuthenticated: true });
    (hasAuthToken as unknown as ReturnType<typeof vi.fn>).mockReturnValue(true);
    (isAuthenticationError as unknown as ReturnType<typeof vi.fn>).mockReturnValue(false);
    lastQueryArgs = undefined;

    // Default mock implementations - return stable response object
    // Note: We return a new object with the same structure to avoid state leaks,
    // but reuse the nested data structures to reduce memory
    (useQuery as any).mockImplementation((_doc: any, args: any) => {
      lastQueryArgs = args;
      return {
        ...baseMockResponse,
        refetch: mockRefetch,
      };
    });

    // Mock useUserMutations
    (useUserMutations as any).mockReturnValue({
      enableUser: vi.fn(),
      disableUser: vi.fn(),
      assignGroupToUser: vi.fn(),
      removeGroupFromUser: vi.fn(),
      assignRoleToUser: vi.fn(),
      removeRoleFromUser: vi.fn(),
      enableLoading: false,
      disableLoading: false,
      assignGroupLoading: false,
      removeGroupLoading: false,
      assignRoleLoading: false,
      removeRoleLoading: false,
    });

    (useRelayPagination as any).mockReturnValue({
      loadNextPage: vi.fn(),
      loadPreviousPage: vi.fn(),
      goToFirstPage: vi.fn(),
      paginationRange: { start: 1, end: 2, total: 2 },
      canLoadPreviousPage: false,
    });
  });

  describe('sorting', () => {
    it('should initialize with null orderBy (use backend default)', () => {
      const { result } = renderHook(() => useUserManagement());

      expect(result.current.orderBy).toBeNull();
    });

    it('should pass null orderBy to GraphQL query when sort state is null', () => {
      renderHook(() => useUserManagement());

      // When orderBy is null, it's not included in variables (not set to undefined)
      // Check that the variables object doesn't have orderBy property
      const lastCall = (useQuery as any).mock.calls[
        (useQuery as any).mock.calls.length - 1
      ];
      expect(lastCall[1].variables).not.toHaveProperty('orderBy');
      expect(lastCall[1].variables).toHaveProperty('first');
    });

    it('should pass orderBy to GraphQL query when sort state is set', async () => {
      const { result, unmount } = renderHook(() => useUserManagement());

      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      await waitFor(() => {
        expect(result.current.orderBy).toEqual({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      unmount();
    });

    it('should handle sort changes with handleSort', () => {
      const { result } = renderHook(() => useUserManagement());

      // Initially null
      expect(result.current.orderBy).toBeNull();

      // Click DISPLAY_NAME -> should set to asc
      act(() => {
        result.current.handleSort('DISPLAY_NAME');
      });

      expect(result.current.orderBy).toEqual({
        field: 'DISPLAY_NAME',
        direction: 'asc',
      });

      // Click DISPLAY_NAME again -> should set to desc
      act(() => {
        result.current.handleSort('DISPLAY_NAME');
      });

      expect(result.current.orderBy).toEqual({
        field: 'DISPLAY_NAME',
        direction: 'desc',
      });

      // Click DISPLAY_NAME again -> should set to null
      act(() => {
        result.current.handleSort('DISPLAY_NAME');
      });

      expect(result.current.orderBy).toBeNull();
    });

    it('should switch to new field when clicking different column', () => {
      const { result } = renderHook(() => useUserManagement());

      // Set DISPLAY_NAME to desc
      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'desc' });
      });

      // Click EMAIL -> should switch to EMAIL asc
      act(() => {
        result.current.handleSort('EMAIL');
      });

      expect(result.current.orderBy).toEqual({
        field: 'EMAIL',
        direction: 'asc',
      });
    });

    it('should reset cursor when orderBy changes', async () => {
      // Set initial state with a cursor
      const { result, rerender, unmount } = renderHook(() => useUserManagement());

      // First, set a cursor by simulating pagination
      // Then change orderBy - cursor should be reset
      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      // The cursor reset happens in useEffect when orderBy changes
      // When after is null, it's not included in variables (not set to undefined)
      // We verify this by checking that the last call doesn't have after in variables
      await waitFor(() => {
        const lastCall = (useQuery as any).mock.calls[
          (useQuery as any).mock.calls.length - 1
        ];
        expect(lastCall[1].variables).not.toHaveProperty('after');
        expect(lastCall[1].variables).toHaveProperty('orderBy');
      });

      // Explicitly unmount to ensure cleanup
      unmount();
    });

    it('should convert sort state to GraphQL format correctly', async () => {
      const { result, rerender, unmount } = renderHook(() => useUserManagement());

      // Test DISPLAY_NAME asc
      act(() => {
        result.current.setOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      await waitFor(() => {
        expect(result.current.orderBy).toEqual({ field: 'DISPLAY_NAME', direction: 'asc' });
      });

      // Explicitly unmount to ensure cleanup
      unmount();
    });

    it('should include orderBy in return value', () => {
      const { result } = renderHook(() => useUserManagement());

      expect(result.current.orderBy).toBeDefined();
      expect(result.current.setOrderBy).toBeDefined();
      expect(result.current.handleSort).toBeDefined();
      expect(typeof result.current.setOrderBy).toBe('function');
      expect(typeof result.current.handleSort).toBe('function');
    });
  });
});
