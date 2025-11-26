import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useDataTableQuery, type PageResult } from '../useDataTableQuery';

// Helper to create a test wrapper with QueryClient
const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  };
  
  Wrapper.displayName = 'TestWrapper';
  
  return Wrapper;
};

describe('useDataTableQuery', () => {
  it('should initialize with default values', () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    expect(result.current.page).toBe(0);
    expect(result.current.pageSize).toBe(25);
    expect(result.current.sort).toBeUndefined();
    expect(result.current.filter).toBeUndefined();
  });

  it('should initialize with custom values', () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
          initialPage: 2,
          initialPageSize: 50,
          initialSort: 'name:asc',
          initialFilter: { status: 'active' },
        }),
      { wrapper: createWrapper() }
    );

    expect(result.current.page).toBe(2);
    expect(result.current.pageSize).toBe(50);
    expect(result.current.sort).toBe('name:asc');
    expect(result.current.filter).toEqual({ status: 'active' });
  });

  it('should call fetcher with correct query params', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(fetcher).toHaveBeenCalledWith({
        page: 0,
        pageSize: 25,
      });
    });
  });

  it('should call fetcher with sort and filter when provided', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
          initialSort: 'name:asc',
          initialFilter: { status: 'active' },
        }),
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(fetcher).toHaveBeenCalledWith({
        page: 0,
        pageSize: 25,
        sort: 'name:asc',
        filter: { status: 'active' },
      });
    });
  });

  it('should update page when setPage is called', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    expect(result.current.page).toBe(0);

    result.current.setPage(2);

    await waitFor(() => {
      expect(result.current.page).toBe(2);
    });
  });

  it('should update pageSize when setPageSize is called', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    expect(result.current.pageSize).toBe(25);

    result.current.setPageSize(50);

    await waitFor(() => {
      expect(result.current.pageSize).toBe(50);
    });
  });

  it('should update sort when setSort is called', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    result.current.setSort('name:desc');

    await waitFor(() => {
      expect(result.current.sort).toBe('name:desc');
    });
  });

  it('should update filter when setFilter is called', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    const newFilter = { status: 'inactive', category: 'test' };
    result.current.setFilter(newFilter);

    await waitFor(() => {
      expect(result.current.filter).toEqual(newFilter);
    });
  });

  it('should update both page and pageSize with onPageChange', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    result.current.onPageChange(3, 100);

    await waitFor(() => {
      expect(result.current.page).toBe(3);
      expect(result.current.pageSize).toBe(100);
    });
  });

  it('should not update pageSize if not provided to onPageChange', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
          initialPageSize: 50,
        }),
      { wrapper: createWrapper() }
    );

    result.current.onPageChange(2);

    await waitFor(() => {
      expect(result.current.page).toBe(2);
      expect(result.current.pageSize).toBe(50);
    });
  });

  it('should return query result properties', async () => {
    const mockData = { rows: [{ id: 1, name: 'Test' }], total: 1 };
    const fetcher = vi.fn().mockResolvedValue(mockData);

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(result.current.data).toEqual(mockData);
    });
  });

  it('should handle query loading state', () => {
    const fetcher = vi.fn(
      (): Promise<PageResult<unknown>> =>
        new Promise((resolve) => {
          setTimeout(() => resolve({ rows: [], total: 0 }), 100);
        })
    );

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    expect(result.current.isLoading).toBe(true);
  });

  it('should handle query error state', async () => {
    const error = new Error('Fetch failed');
    const fetcher = vi.fn().mockRejectedValue(error);

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
      expect(result.current.error).toEqual(error);
    });
  });

  it('should refetch when query params change', async () => {
    const fetcher = vi.fn().mockResolvedValue({ rows: [], total: 0 });

    const { result } = renderHook(
      () =>
        useDataTableQuery({
          key: 'test',
          fetcher,
        }),
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(fetcher).toHaveBeenCalledTimes(1);
    });

    result.current.setPage(1);

    await waitFor(() => {
      expect(fetcher).toHaveBeenCalledTimes(2);
      expect(fetcher).toHaveBeenLastCalledWith({
        page: 1,
        pageSize: 25,
      });
    });
  });
});

