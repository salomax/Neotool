"use client";

import { useState, useMemo, useCallback } from "react";
import { useQuery, useMutation } from "@apollo/client/react";
import {
  GetCustomersDocument,
  type GetCustomersQuery,
} from "@/lib/graphql/operations/customer/queries.generated";
import {
  CreateCustomerDocument,
  UpdateCustomerDocument,
  DeleteCustomerDocument,
} from "@/lib/graphql/operations/customer/mutations.generated";
import type { Customer as GraphQLCustomer } from "@/lib/graphql/types/__generated__/graphql";
import { CUSTOMER_STATUSES } from "@/app/(neotool)/examples/customers/constants";

export type Customer = Pick<
  GraphQLCustomer,
  "id" | "name" | "email" | "status" | "createdAt" | "updatedAt"
>;

export type CustomerFormData = {
  name: string;
  email: string;
  status: string;
};

const STATUS_COLORS: Record<string, "default" | "primary" | "success" | "error" | "warning"> = {
  [CUSTOMER_STATUSES.ACTIVE]: "success",
  [CUSTOMER_STATUSES.INACTIVE]: "default",
  [CUSTOMER_STATUSES.PENDING]: "warning",
};

export function useCustomers() {
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>(CUSTOMER_STATUSES.ALL);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Customer | null>(null);

  const { data, loading, error, refetch } = useQuery<GetCustomersQuery>(GetCustomersDocument);
  const [createCustomerMutation, { loading: createLoading }] = useMutation(CreateCustomerDocument, {
    refetchQueries: [{ query: GetCustomersDocument }],
  });
  const [updateCustomerMutation, { loading: updateLoading }] = useMutation(UpdateCustomerDocument, {
    refetchQueries: [{ query: GetCustomersDocument }],
  });
  const [deleteCustomerMutation, { loading: deleteLoading }] = useMutation(DeleteCustomerDocument, {
    refetchQueries: [{ query: GetCustomersDocument }],
  });

  const customers: Customer[] = useMemo(() => {
    const list = data?.customers ?? [];
    return list.map((c) => ({
      id: c.id,
      name: c.name,
      email: c.email,
      status: c.status,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt,
    }));
  }, [data]);

  const filteredCustomers = useMemo(() => {
    return customers.filter((c) => {
      const matchesSearch =
        !searchTerm.trim() ||
        c.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        c.email.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesStatus =
        statusFilter === CUSTOMER_STATUSES.ALL || c.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [customers, searchTerm, statusFilter]);

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

  const createCustomer = useCallback(
    async (input: CustomerFormData) => {
      const result = await createCustomerMutation({
        variables: {
          input: {
            name: input.name,
            email: input.email,
            status: input.status,
          },
        },
      });
      if (!result.data?.createCustomer) throw new Error("Failed to create customer");
      return result.data.createCustomer as Customer;
    },
    [createCustomerMutation]
  );

  const updateCustomer = useCallback(
    async (id: string, input: CustomerFormData) => {
      await updateCustomerMutation({
        variables: {
          id,
          input: {
            name: input.name,
            email: input.email,
            status: input.status,
          },
        },
      });
    },
    [updateCustomerMutation]
  );

  const deleteCustomer = useCallback(
    async (id: string) => {
      await deleteCustomerMutation({ variables: { id } });
    },
    [deleteCustomerMutation]
  );

  const getStatusColor = useCallback((status: string) => {
    return STATUS_COLORS[status] ?? "default";
  }, []);

  return {
    filteredCustomers,
    searchTerm,
    statusFilter,
    setSearchTerm,
    setStatusFilter,
    dialogOpen,
    editingCustomer,
    deleteConfirm,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    setDeleteConfirm,
    createCustomer,
    updateCustomer,
    deleteCustomer,
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
    error: error ?? undefined,
    refetch,
    getStatusColor,
  };
}
