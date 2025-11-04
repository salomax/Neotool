"use client";

import { useState, useMemo, useCallback } from "react";
import {
  useGetCustomersQuery
} from '@/lib/graphql/operations/customer/queries.generated';
import {
  useCreateCustomerMutation,
  useUpdateCustomerMutation,
  useDeleteCustomerMutation
} from '@/lib/graphql/operations/customer/mutations.generated';
import { CustomerInput } from '@/lib/graphql/types/__generated__/graphql';
import type { ApolloError } from '@apollo/client';
import { extractErrorMessage } from './utils';
import { CUSTOMER_STATUSES } from '@/app/(neotool)/examples/customers/constants';

export type Customer = {
  id: string;
  name: string;
  email: string;
  status: string;
  createdAt: string | null;
  updatedAt: string | null;
};

export type CustomerFormData = {
  name: string;
  email: string;
  status: string;
};

export type UseCustomersOptions = {
  initialSearchTerm?: string;
  initialStatusFilter?: string;
};

export type UseCustomersReturn = {
  // Data
  customers: Customer[];
  filteredCustomers: Customer[];
  
  // Search and filtering
  searchTerm: string;
  statusFilter: string;
  setSearchTerm: (term: string) => void;
  setStatusFilter: (filter: string) => void;
  
  // Dialog state
  dialogOpen: boolean;
  editingCustomer: Customer | null;
  deleteConfirm: Customer | null;
  openCreateDialog: () => void;
  openEditDialog: (customer: Customer) => void;
  closeDialog: () => void;
  setDeleteConfirm: (customer: Customer | null) => void;
  
  // CRUD operations
  createCustomer: (data: CustomerFormData) => Promise<void>;
  updateCustomer: (id: string, data: CustomerFormData) => Promise<void>;
  deleteCustomer: (id: string) => Promise<void>;
  
  // Loading states
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  
  // Error handling
  error: ApolloError | undefined;
  
  // Utilities
  refetch: () => void;
  getStatusColor: (status: string) => "success" | "error" | "warning" | "default";
};

/**
 * Custom hook for managing customer data and operations
 * 
 * This hook encapsulates all customer-related business logic including:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Search and filtering functionality
 * - Dialog state management
 * - Loading states and error handling
 * 
 * @param options - Configuration options for the hook
 * @returns Object containing all customer management functionality
 * 
 * @example
 * ```tsx
 * function CustomerPage() {
 *   const {
 *     filteredCustomers,
 *     searchTerm,
 *     setSearchTerm,
 *     createCustomer,
 *     updateCustomer,
 *     deleteCustomer,
 *     loading,
 *     error
 *   } = useCustomers();
 * 
 *   return (
 *     <div>
 *       <input 
 *         value={searchTerm} 
 *         onChange={(e) => setSearchTerm(e.target.value)} 
 *       />
 *       {filteredCustomers.map(customer => (
 *         <div key={customer.id}>{customer.name}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useCustomers(options: UseCustomersOptions = {}): UseCustomersReturn {
  // Note: Translation function should be passed from the component using this hook
  
  // Local state
  const [searchTerm, setSearchTerm] = useState(options.initialSearchTerm || "");
  const [statusFilter, setStatusFilter] = useState(options.initialStatusFilter || CUSTOMER_STATUSES.ALL);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Customer | null>(null);

  // GraphQL hooks
  const { data: customersData, loading, error, refetch } = useGetCustomersQuery();
  const [createCustomerMutation, { loading: createLoading }] = useCreateCustomerMutation();
  const [updateCustomerMutation, { loading: updateLoading }] = useUpdateCustomerMutation();
  const [deleteCustomerMutation, { loading: deleteLoading }] = useDeleteCustomerMutation();

  // Derived data
  const customers = customersData?.customers || [];

  // Filtered customers
  const filteredCustomers = useMemo(() => {
    return customers.filter((customer: Customer) => {
      const matchesSearch = customer.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                           customer.email.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesStatus = statusFilter === CUSTOMER_STATUSES.ALL || customer.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [customers, searchTerm, statusFilter]);

  // CRUD operations
  const createCustomer = useCallback(async (data: CustomerFormData) => {
    try {
      const input: CustomerInput = {
        name: data.name.trim(),
        email: data.email.trim().toLowerCase(),
        status: data.status,
      };

      const result = await createCustomerMutation({
        variables: { input },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error creating customer:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to create customer');
      throw new Error(errorMessage);
    }
  }, [createCustomerMutation, refetch]);

  const updateCustomer = useCallback(async (id: string, data: CustomerFormData) => {
    try {
      const input: CustomerInput = {
        name: data.name.trim(),
        email: data.email.trim().toLowerCase(),
        status: data.status,
      };

      const result = await updateCustomerMutation({
        variables: {
          id,
          input,
        },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error updating customer:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to update customer');
      throw new Error(errorMessage);
    }
  }, [updateCustomerMutation, refetch]);

  const deleteCustomer = useCallback(async (id: string) => {
    try {
      const result = await deleteCustomerMutation({
        variables: { id },
      });

      // Only refetch if mutation was successful
      if (result.data) {
        refetch();
      }
    } catch (err) {
      console.error('Error deleting customer:', err);
      const errorMessage = extractErrorMessage(err, 'Failed to delete customer');
      throw new Error(errorMessage);
    }
  }, [deleteCustomerMutation, refetch]);

  // Dialog management
  const openCreateDialog = useCallback(() => {
    setEditingCustomer(null);
    setDialogOpen(true);
  }, []);

  const openEditDialog = useCallback((customer: Customer) => {
    setEditingCustomer(customer);
    setDialogOpen(true);
  }, []);

  const closeDialog = useCallback(() => {
    setDialogOpen(false);
    setEditingCustomer(null);
  }, []);

  // Utility functions
  const getStatusColor = useCallback((status: string): "success" | "error" | "warning" | "default" => {
    switch (status) {
      case CUSTOMER_STATUSES.ACTIVE: return "success";
      case CUSTOMER_STATUSES.INACTIVE: return "error";
      case CUSTOMER_STATUSES.PENDING: return "warning";
      default: return "default";
    }
  }, []);

  return {
    // Data
    customers,
    filteredCustomers,
    
    // Search and filtering
    searchTerm,
    statusFilter,
    setSearchTerm,
    setStatusFilter,
    
    // Dialog state
    dialogOpen,
    editingCustomer,
    deleteConfirm,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    setDeleteConfirm,
    
    // CRUD operations
    createCustomer,
    updateCustomer,
    deleteCustomer,
    
    // Loading states
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
    
    // Error handling
    error,
    
    // Utilities
    refetch,
    getStatusColor,
  };
}
