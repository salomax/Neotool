"use client";

import React, { useMemo, useEffect } from "react";
import { IconButton, Chip } from "@/shared/components/ui/primitives";
import { Add as AddIcon, Edit as EditIcon, Delete as DeleteIcon } from "@mui/icons-material";
import { DataTable, type ColDef } from '@/shared/components/ui/data-display';
import { PageLayout, PageHeader, Paper, Stack } from '@/shared/components/ui/layout';
import { SearchFilters } from '@/shared/components/ui/forms';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import { CustomerForm } from './components';
import { z } from "zod";
import { useTranslation } from '@/shared/i18n';
import { customersTranslations } from './i18n';
import { useCustomers, CustomerFormData, Customer } from '@/shared/hooks/customer';
import { useToast } from '@/shared/providers';
import { Button, ErrorBoundary, Skeleton } from '@/shared/components/ui/primitives';
import { formatDateTime, getCurrentLocale } from '@/shared/utils/date';
import { CUSTOMER_STATUSES, CUSTOMER_STATUS_OPTIONS, DEFAULT_CUSTOMER_STATUS } from './constants';
import { extractErrorMessage } from '@/shared/utils/error';


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
  const handleFormSubmit = async (data: CustomerFormData) => {
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
      // Extract and display specific error message
      const errorMessage = extractErrorMessage(
        err,
        editingCustomer ? t('toast.customerUpdateError') : t('toast.customerCreateError')
      );
      toast.error(errorMessage);
    }
  };

  // Delete customer
  const handleDeleteCustomer = async () => {
    if (!deleteConfirm) return;
    
    try {
      await deleteCustomerMutation(deleteConfirm.id);
      toast.success(t('toast.customerDeleted', { name: deleteConfirm.name }));
      setDeleteConfirm(null);
    } catch (err) {
      const errorMessage = extractErrorMessage(err, t('toast.customerDeleteError'));
      toast.error(errorMessage);
    }
  };

  // Get current locale for date formatting
  const currentLocale = getCurrentLocale();

  // Define column definitions for DataTable - optimized with useMemo
  const columns = useMemo<ColDef<Customer>[]>(() => [
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
            color={getStatusColor(status)}
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
      field: 'id', // ag-grid requires a valid field; we use id but render actions
      colId: 'actions',
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
              onClick={() => openEditDialog(customer)}
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
  ], [t, getStatusColor, openEditDialog, setDeleteConfirm, currentLocale]);

  // Show error toast when there's a query error (not mutation errors)
  useEffect(() => {
    if (error && !createLoading && !updateLoading && !deleteLoading) {
      const errorMessage = extractErrorMessage(error, t('errorLoadingCustomers'));
      toast.error(errorMessage);
    }
  }, [error, createLoading, updateLoading, deleteLoading, toast, t]);


  // Prepare filter options for SearchFilters component using constants
  const statusFilterOptions = useMemo(() => 
    CUSTOMER_STATUS_OPTIONS.map(option => ({
      value: option.value,
      label: t(option.labelKey),
    })),
    [t]
  );

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
              onClick={openCreateDialog}
            >
              {t('addCustomer')}
            </Button>
          }
        />
      }
      >
      <Stack style={{ flex: 1 }}>
        {/* Search and Filter Controls */}
        <Paper name="search-filters">
          <SearchFilters
            searchTerm={searchTerm}
            onSearchChange={setSearchTerm}
            searchPlaceholder={t('searchPlaceholder')}
            filters={filters}
            onRefresh={refetch}
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
            />
          )}
        </Paper>
      </Stack>

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
      {deleteConfirm && (
        <ConfirmationDialog
          open={true}
          onClose={() => setDeleteConfirm(null)}
          onConfirm={handleDeleteCustomer}
          title={t('confirmDelete')}
          message={t('deleteMessage', { name: deleteConfirm.name })}
          confirmText={t('delete')}
          cancelText={t('cancel')}
          confirmColor="error"
          loading={deleteLoading}
        />
      )}
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