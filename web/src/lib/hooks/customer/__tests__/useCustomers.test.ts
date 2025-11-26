import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useCustomers } from '../useCustomers';
import { MockedProvider } from '@apollo/client/testing';
import { CUSTOMER_STATUSES } from '@/app/(neotool)/examples/customers/constants';

// Mock the GraphQL operations
vi.mock('@/lib/graphql/operations/customer/queries.generated', () => ({
  useGetCustomersQuery: vi.fn(),
}));

vi.mock('@/lib/graphql/operations/customer/mutations.generated', () => ({
  useCreateCustomerMutation: vi.fn(),
  useUpdateCustomerMutation: vi.fn(),
  useDeleteCustomerMutation: vi.fn(),
}));

import {
  useGetCustomersQuery,
} from '@/lib/graphql/operations/customer/queries.generated';
import {
  useCreateCustomerMutation,
  useUpdateCustomerMutation,
  useDeleteCustomerMutation,
} from '@/lib/graphql/operations/customer/mutations.generated';

describe('useCustomers', () => {
  const mockCustomers = [
    {
      id: '1',
      name: 'John Doe',
      email: 'john@example.com',
      status: CUSTOMER_STATUSES.ACTIVE,
      createdAt: '2024-01-01',
      updatedAt: '2024-01-01',
    },
    {
      id: '2',
      name: 'Jane Smith',
      email: 'jane@example.com',
      status: CUSTOMER_STATUSES.INACTIVE,
      createdAt: '2024-01-02',
      updatedAt: '2024-01-02',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should initialize with default values', () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    expect(result.current.searchTerm).toBe('');
    expect(result.current.statusFilter).toBe(CUSTOMER_STATUSES.ALL);
    expect(result.current.dialogOpen).toBe(false);
    expect(result.current.editingCustomer).toBe(null);
    expect(result.current.deleteConfirm).toBe(null);
  });

  it('should initialize with custom options', () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() =>
      useCustomers({
        initialSearchTerm: 'test',
        initialStatusFilter: CUSTOMER_STATUSES.ACTIVE,
      })
    );

    expect(result.current.searchTerm).toBe('test');
    expect(result.current.statusFilter).toBe(CUSTOMER_STATUSES.ACTIVE);
  });

  it('should return customers from query', () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    expect(result.current.customers).toEqual(mockCustomers);
  });

  it('should filter customers by search term', async () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    result.current.setSearchTerm('John');

    await waitFor(() => {
      expect(result.current.filteredCustomers).toHaveLength(1);
    });
    expect(result.current.filteredCustomers[0]?.name).toBe('John Doe');
  });

  it('should filter customers by status', async () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    result.current.setStatusFilter(CUSTOMER_STATUSES.ACTIVE);

    await waitFor(() => {
      expect(result.current.filteredCustomers).toHaveLength(1);
    });
    expect(result.current.filteredCustomers[0]?.status).toBe(
      CUSTOMER_STATUSES.ACTIVE
    );
  });

  it('should open create dialog', async () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    result.current.openCreateDialog();

    await waitFor(() => {
      expect(result.current.dialogOpen).toBe(true);
    });
    expect(result.current.editingCustomer).toBe(null);
  });

  it('should open edit dialog', async () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    result.current.openEditDialog(mockCustomers[0]!);

    await waitFor(() => {
      expect(result.current.dialogOpen).toBe(true);
    });
    expect(result.current.editingCustomer).toEqual(mockCustomers[0]);
  });

  it('should close dialog', () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    result.current.openCreateDialog();
    result.current.closeDialog();

    expect(result.current.dialogOpen).toBe(false);
    expect(result.current.editingCustomer).toBe(null);
  });

  it('should return correct status color', () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    expect(result.current.getStatusColor(CUSTOMER_STATUSES.ACTIVE)).toBe(
      'success'
    );
    expect(result.current.getStatusColor(CUSTOMER_STATUSES.INACTIVE)).toBe(
      'error'
    );
    expect(result.current.getStatusColor(CUSTOMER_STATUSES.PENDING)).toBe(
      'warning'
    );
    expect(result.current.getStatusColor('unknown')).toBe('default');
  });

  it('should handle loading states', () => {
    (useGetCustomersQuery as any).mockReturnValue({
      data: { customers: mockCustomers },
      loading: true,
      error: undefined,
      refetch: vi.fn(),
    });

    (useCreateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: true },
    ]);

    (useUpdateCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    (useDeleteCustomerMutation as any).mockReturnValue([
      vi.fn(),
      { loading: false },
    ]);

    const { result } = renderHook(() => useCustomers());

    expect(result.current.loading).toBe(true);
    expect(result.current.createLoading).toBe(true);
  });
});

