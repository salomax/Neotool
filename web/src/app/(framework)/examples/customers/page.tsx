"use client";

import React, { useMemo, useCallback, useEffect } from "react";
import { IconButton, Chip } from "@/shared/components/ui/primitives";
import { Add as AddIcon, Edit as EditIcon, Delete as DeleteIcon } from "@mui/icons-material";
import { DataTable } from '@/shared/components/ui/data-display';
import { PageLayout, PageHeader, Paper } from '@/shared/components/ui/layout';
import { SearchFilters } from '@/shared/components/ui/forms';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { CustomerForm } from './components';
import type { ColDef } from 'ag-grid-community';
import { z } from "zod";
import { useTranslation } from '@/shared/i18n';
import { customersTranslations } from './i18n';
import { useCustomers, CustomerFormData, Customer } from '@/lib/hooks/customer/useCustomers';
import { useToast } from '@/shared/providers';
import { Button, ErrorBoundary, Skeleton } from '@/shared/components/ui/primitives';
import { formatDateTime, getCurrentLocale } from '@/shared/utils/date';


function CustomersPageContent() {
  const { t } = useTranslation(customersTranslations);
  const toast = useToast();
  
  // Use the custom hook for all customer data management
  const {
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
    createCustomer: createCustomerMutation,
    updateCustomer: updateCustomerMutation,
    deleteCustomer: deleteCustomerMutation,
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
    error,
    refetch,
    getStatusColor,
  } = useCustomers();

  const customerSchema = useMemo(() => z.object({
    name: z.string().min(2, t('nameRequired')),
    email: z.string().email(t('emailInvalid')),
    status: z.string().min(1, t('statusRequired')),
  }), [t]);

  // Create or update customer
  const handleFormSubmit = useCallback(async (data: CustomerFormData) => {
    try {
      if (editingCustomer) {
        await updateCustomerMutation(editingCustomer.id, data);
        toast.success(t('toast.customerUpdated', { name: data.name }));
      } else {
        await createCustomerMutation(data);
        toast.success(t('toast.customerCreated', { name: data.name }));
      }
      closeDialog();
    } catch (err) {
      console.error(t('errorSaving'), err);
      
      // Show error toast with specific message
      if (editingCustomer) {
        toast.error(t('toast.customerUpdateError'));
      } else {
        toast.error(t('toast.customerCreateError'));
      }
    }
  }, [editingCustomer, updateCustomerMutation, createCustomerMutation, closeDialog, t, toast]);

  // Delete customer
  const handleDeleteCustomer = useCallback(async () => {
    if (!deleteConfirm) return;
    
    try {
      await deleteCustomerMutation(deleteConfirm.id);
      toast.success(t('toast.customerDeleted', { name: deleteConfirm.name }));
      setDeleteConfirm(null);
    } catch (err) {
      console.error(t('errorDeleting'), err);
      toast.error(t('toast.customerDeleteError'));
    }
  }, [deleteConfirm, deleteCustomerMutation, setDeleteConfirm, t, toast]);

  // Enhanced dialog handlers
  const handleOpenEditDialog = useCallback((customer: Customer) => {
    openEditDialog(customer);
  }, [openEditDialog]);

  const handleOpenCreateDialog = useCallback(() => {
    openCreateDialog();
  }, [openCreateDialog]);

  // Get current locale for date formatting
  const currentLocale = getCurrentLocale();

  // Create column definitions inside component to have access to imported functions
  const createColumns = useCallback((): ColDef<Customer>[] => [
    {
      field: 'id',
      headerName: t('id'),
      width: 80,
      sortable: true,
    },
    {
      field: 'name',
      headerName: t('name'),
      flex: 1,
      sortable: true,
    },
    {
      field: 'email',
      headerName: t('email'),
      flex: 1,
      sortable: true,
    },
    {
      field: 'status',
      headerName: t('status'),
      sortable: true,
      cellRenderer: (params: { value: string }) => {
        const status = params.value;
        return (
          <Chip
            name={`status-${status.toLowerCase()}`}
            label={status}
            color={getStatusColor(status) as "success" | "error" | "warning" | "default"}
            size="small"
          />
        );
      },
    },
    {
      field: 'createdAt',
      headerName: t('created'),
      width: 120,
      sortable: true,
      valueFormatter: (params: { value: string | null }) => {
        return formatDateTime(params.value, currentLocale);
      },
    },
    {
      field: 'actions' as keyof Customer, // Actions column doesn't map to a real field
      headerName: t('actions'),
      width: 120,
      sortable: false,
      filter: false,
      cellRenderer: (params: { data: Customer }) => {
        const customer = params.data;
        return (
          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
            <IconButton
              name={`edit-customer-${customer.id}`}
              color="primary"
              onClick={() => handleOpenEditDialog(customer)}
              size="small"
              aria-label={`Edit customer ${customer.name}`}
            >
              <EditIcon />
            </IconButton>
            <IconButton
              name={`delete-customer-${customer.id}`}
              color="error"
              onClick={() => setDeleteConfirm(customer)}
              size="small"
              aria-label={`Delete customer ${customer.name}`}
            >
              <DeleteIcon />
            </IconButton>
          </div>
        );
      },
    },
  ], [t, getStatusColor, handleOpenEditDialog, setDeleteConfirm, currentLocale]);

  // Define column definitions for DataTable
  const columns: ColDef<Customer>[] = useMemo(() => createColumns(), [createColumns]);

  // Enhanced refresh handler with toast feedback
  const handleRefresh = useCallback(() => {
    refetch();
  }, [refetch]);

  // Show error toast when there's an initial error
  useEffect(() => {
    if (error) {
      toast.error(t('errorSaving'));
    }
  }, [error, toast, t]);


  // Prepare filter options for SearchFilters component
  const statusFilterOptions = [
    { value: 'ALL', label: t('all') },
    { value: 'ACTIVE', label: t('active') },
    { value: 'INACTIVE', label: t('inactive') },
    { value: 'PENDING', label: t('pending') },
  ];

  const filters = [
    {
      key: 'status',
      label: t('status'),
      value: statusFilter,
      options: statusFilterOptions,
      onChange: setStatusFilter,
    },
  ];

  return (
    <PageLayout
      header={
        <PageHeader
          title={t('title')}
          actions={
            <Button
              name="add-customer"
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleOpenCreateDialog}
            >
              {t('addCustomer')}
            </Button>
          }
        />
      }
      loading={loading}
      error={error?.message}
    >
      {/* Search and Filter Controls */}
      <Paper name="search-filters">
        <SearchFilters
          searchTerm={searchTerm}
          onSearchChange={setSearchTerm}
          searchPlaceholder={t('searchPlaceholder')}
          filters={filters}
          onRefresh={handleRefresh}
          refreshLoading={loading}
        />
      </Paper>

      {/* Main Content */}
      <Paper 
        name="customers-table-container"
        sx={{ 
          flex: 1, // take up all the remaining free space in the container
        }}
      >
        {loading && !filteredCustomers.length ? (
          <Skeleton 
            variant="rectangular" 
            sx={{ borderRadius: 1, height: '100%' }}
          />
        ) : (
          <DataTable
            columns={columns}
            rows={filteredCustomers}
            loading={loading}
            error={error?.message}
            showToolbar={true}
            enableDensity={true}
            enableExport={true}
            enableFilterBar={true}
            tableId="customers-table"
          />
        )}
      </Paper>

      {/* Customer Form Dialog */}
      <CustomerForm
        open={dialogOpen}
        onClose={closeDialog}
        onSubmit={handleFormSubmit}
        initialData={editingCustomer}
        isEditing={!!editingCustomer}
        loading={createLoading || updateLoading}
        t={t}
        validationSchema={customerSchema}
      />

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={handleDeleteCustomer}
        title={t('confirmDelete')}
        message={t('deleteMessage', { name: deleteConfirm?.name })}
        confirmText={t('delete')}
        cancelText={t('cancel')}
        confirmColor="error"
        loading={deleteLoading}
      />
    </PageLayout>
  );
}

export default function CustomersPage() {
  return (
    <ErrorBoundary>
      <CustomersPageContent />
    </ErrorBoundary>
  );
}