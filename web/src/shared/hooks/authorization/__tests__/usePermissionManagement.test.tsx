"use client";

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useQuery } from '@apollo/client/react';
import { usePermissionManagement } from '../usePermissionManagement';

const mockUseQuery = vi.fn();

vi.mock('@apollo/client/react', () => ({
  useQuery: (document: any, options: any) => mockUseQuery(document, options),
}));

describe('usePermissionManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseQuery.mockReturnValue({
      data: {
        permissions: {
          edges: [
            { node: { id: 'perm-1', name: 'Permission 1' } },
            { node: { id: 'perm-2', name: 'Permission 2' } },
          ],
          pageInfo: {
            hasNextPage: false,
            hasPreviousPage: false,
            startCursor: null,
            endCursor: null,
          },
        },
      },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });
  });

  it('should skip query execution when skip option is true', () => {
    const { result } = renderHook(() => usePermissionManagement({ skip: true, initialFirst: 500 }));

    expect(mockUseQuery).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ skip: true })
    );
    expect(result.current.permissions).toEqual([]);
    expect(result.current.loading).toBe(true);
    expect(result.current.error).toBeUndefined();
  });

  it('should return permissions when not skipped', () => {
    const { result } = renderHook(() => usePermissionManagement({ initialFirst: 20 }));

    expect(mockUseQuery).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        variables: expect.objectContaining({ first: 20 }),
        skip: false,
      })
    );
    expect(result.current.permissions).toHaveLength(2);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeUndefined();
  });
});
