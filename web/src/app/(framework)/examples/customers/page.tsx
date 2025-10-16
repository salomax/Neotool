"use client";

import React, { useEffect, useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Table from "@mui/material/Table";
import TableHead from "@mui/material/TableHead";
import TableBody from "@mui/material/TableBody";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import Paper from "@mui/material/Paper";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import IconButton from "@mui/material/IconButton";
import Chip from "@mui/material/Chip";
import Alert from "@mui/material/Alert";
import CircularProgress from "@mui/material/CircularProgress";
import InputAdornment from "@mui/material/InputAdornment";
import SearchIcon from "@mui/icons-material/Search";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import RefreshIcon from "@mui/icons-material/Refresh";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery, useMutation } from '@apollo/client/react';
import { useI18n } from '@/shared/i18n/hooks/useI18n';
import { customersTranslations } from './i18n';
import { 
  GET_CUSTOMERS, 
  CREATE_CUSTOMER, 
  UPDATE_CUSTOMER, 
  DELETE_CUSTOMER 
} from '@/lib/graphql/operations';
import { CustomerInput } from '@/lib/graphql/types';

type Customer = {
  id: string;
  name: string;
  email: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
};

type CustomerFormData = {
  name: string;
  email: string;
  status: string;
};

export default function CustomersPage() {
  const { t } = useI18n(customersTranslations);
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<Customer | null>(null);

  // Dynamic schema validation with translations
  const customerSchema = z.object({
    name: z.string().min(2, t('customers.nameRequired')),
    email: z.string().email(t('customers.emailInvalid')),
    status: z.string().min(1, t('customers.statusRequired')),
  });

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CustomerFormData>({
    resolver: zodResolver(customerSchema),
    defaultValues: {
      name: "",
      email: "",
      status: "ACTIVE",
    },
  });

  // GraphQL queries and mutations
  const { data: customersData, loading, error, refetch } = useQuery(GET_CUSTOMERS);
  const [createCustomer] = useMutation(CREATE_CUSTOMER);
  const [updateCustomer] = useMutation(UPDATE_CUSTOMER);
  const [deleteCustomer] = useMutation(DELETE_CUSTOMER);

  const customers = (customersData as any)?.customers || [];

  // Create or update customer
  const onSubmit = async (data: CustomerFormData) => {
    try {
      const input: CustomerInput = {
        name: data.name,
        email: data.email,
        status: data.status,
      };

      if (editingCustomer) {
        await updateCustomer({
          variables: {
            id: editingCustomer.id,
            input,
          },
        });
      } else {
        await createCustomer({
          variables: { input },
        });
      }

      reset();
      setDialogOpen(false);
      setEditingCustomer(null);
      refetch();
    } catch (err) {
      console.error(t('customers.errorSaving'), err);
    }
  };

  // Delete customer
  const handleDeleteCustomer = async (customer: Customer) => {
    try {
      await deleteCustomer({
        variables: { id: customer.id },
      });
      setDeleteConfirm(null);
      refetch();
    } catch (err) {
      console.error(t('customers.errorDeleting'), err);
    }
  };

  // Open edit dialog
  const openEditDialog = (customer: Customer) => {
    setEditingCustomer(customer);
    reset({
      name: customer.name,
      email: customer.email,
      status: customer.status,
    });
    setDialogOpen(true);
  };

  // Open create dialog
  const openCreateDialog = () => {
    setEditingCustomer(null);
    reset({
      name: "",
      email: "",
      status: "ACTIVE",
    });
    setDialogOpen(true);
  };

  // Filter customers
  const filteredCustomers = customers.filter((customer: Customer) => {
    const matchesSearch = customer.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         customer.email.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === "ALL" || customer.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  // Get status color
  const getStatusColor = (status: string) => {
    switch (status) {
      case "ACTIVE": return "success";
      case "INACTIVE": return "error";
      case "PENDING": return "warning";
      default: return "default";
    }
  };


  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h4" component="h1">
          {t('customers.title')}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={openCreateDialog}
        >
          {t('customers.addCustomer')}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error.message}
        </Alert>
      )}

      {/* Search and Filter Controls */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
          <TextField
            placeholder={t('customers.searchPlaceholder')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 200 }}
          />
          <TextField
            select
            label={t('customers.status')}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            SelectProps={{ native: true }}
            sx={{ minWidth: 120 }}
          >
            <option value="ALL">{t('customers.all')}</option>
            <option value="ACTIVE">{t('customers.active')}</option>
            <option value="INACTIVE">{t('customers.inactive')}</option>
            <option value="PENDING">{t('customers.pending')}</option>
          </TextField>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => refetch()}
            disabled={loading}
          >
            {t('customers.refresh')}
          </Button>
        </Box>
      </Paper>

      {/* Customers Table */}
      <Paper>
        {loading ? (
          <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>{t('customers.id')}</TableCell>
                <TableCell>{t('customers.name')}</TableCell>
                <TableCell>{t('customers.email')}</TableCell>
                <TableCell>{t('customers.status')}</TableCell>
                <TableCell>{t('customers.created')}</TableCell>
                <TableCell align="right">{t('customers.actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredCustomers.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">
                      {customers.length === 0 ? t('customers.noCustomers') : t('customers.noMatches')}
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                filteredCustomers.map((customer: Customer) => (
                  <TableRow key={customer.id} hover>
                    <TableCell>{customer.id}</TableCell>
                    <TableCell>{customer.name}</TableCell>
                    <TableCell>{customer.email}</TableCell>
                    <TableCell>
                      <Chip
                        label={customer.status}
                        color={getStatusColor(customer.status) as any}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      {customer.createdAt ? new Date(customer.createdAt).toLocaleDateString() : "-"}
                    </TableCell>
                    <TableCell align="right">
                      <IconButton
                        color="primary"
                        onClick={() => openEditDialog(customer)}
                        size="small"
                      >
                        <EditIcon />
                      </IconButton>
                      <IconButton
                        color="error"
                        onClick={() => setDeleteConfirm(customer)}
                        size="small"
                      >
                        <DeleteIcon />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        )}
      </Paper>

      {/* Create/Edit Dialog */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {editingCustomer ? t('customers.editCustomer') : t('customers.createCustomer')}
        </DialogTitle>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogContent>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
              <Controller
                name="name"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('customers.name')}
                    error={!!errors.name}
                    helperText={errors.name?.message}
                    fullWidth
                  />
                )}
              />
              <Controller
                name="email"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('customers.email')}
                    type="email"
                    error={!!errors.email}
                    helperText={errors.email?.message}
                    fullWidth
                  />
                )}
              />
              <Controller
                name="status"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    select
                    label={t('customers.status')}
                    error={!!errors.status}
                    helperText={errors.status?.message}
                    SelectProps={{ native: true }}
                    fullWidth
                  >
                    <option value="ACTIVE">{t('customers.active')}</option>
                    <option value="INACTIVE">{t('customers.inactive')}</option>
                    <option value="PENDING">{t('customers.pending')}</option>
                  </TextField>
                )}
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)}>{t('customers.cancel')}</Button>
            <Button type="submit" variant="contained" disabled={isSubmitting}>
              {isSubmitting ? <CircularProgress size={20} /> : (editingCustomer ? t('customers.update') : t('customers.create'))}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={!!deleteConfirm} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>{t('customers.confirmDelete')}</DialogTitle>
        <DialogContent>
          <Typography>
            {t('customers.deleteMessage', { name: deleteConfirm?.name })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm(null)}>{t('customers.cancel')}</Button>
          <Button
            onClick={() => deleteConfirm && handleDeleteCustomer(deleteConfirm)}
            color="error"
            variant="contained"
          >
            {t('customers.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
