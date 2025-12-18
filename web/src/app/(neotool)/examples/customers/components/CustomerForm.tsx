"use client";

import React from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from '@/shared/components/ui/primitives';
import { TextField } from '@mui/material';
import { FormLayout, FormRow } from '@/shared/components/ui/forms/components';
import { DEFAULT_CUSTOMER_STATUS, CUSTOMER_STATUSES } from '../constants';

export interface CustomerFormData {
  name: string;
  email: string;
  status: string;
}

export interface CustomerFormProps {
  /** Whether the dialog is open */
  open: boolean;
  /** Dialog close handler */
  onClose: () => void;
  /** Form submit handler */
  onSubmit: (data: CustomerFormData) => Promise<void>;
  /** Initial form data for editing */
  initialData?: Partial<CustomerFormData> | null;
  /** Whether this is editing mode */
  isEditing?: boolean;
  /** Whether form is submitting */
  loading?: boolean;
  /** Translation function */
  t: (key: string, params?: any) => string;
  /** Validation schema */
  validationSchema?: z.ZodSchema;
}

export function CustomerForm({
  open,
  onClose,
  onSubmit,
  initialData,
  isEditing = false,
  loading = false,
  t,
  validationSchema
}: CustomerFormProps) {
  // Create default validation schema if none provided
  const defaultSchema = z.object({
    name: z.string().min(2, 'Name is required'),
    email: z.string().email('Invalid email'),
    status: z.string().min(1, 'Status is required'),
  });

  const schema = validationSchema || defaultSchema;

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CustomerFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      email: "",
      status: DEFAULT_CUSTOMER_STATUS,
      ...initialData,
    },
  });

  // Reset form when dialog opens/closes or initialData changes
  React.useEffect(() => {
    if (open) {
      reset({
        name: "",
        email: "",
        status: DEFAULT_CUSTOMER_STATUS,
        ...(initialData || {}),
      });
    }
  }, [open, initialData, reset]);

  const handleFormSubmit = async (data: CustomerFormData) => {
    try {
      await onSubmit(data);
      // Don't reset here - useEffect will handle it when dialog closes
      onClose();
    } catch (error) {
      // Error handling is done in the parent component
      // Form stays open so user can fix errors
    }
  };

  return (
    <Dialog 
      open={open} 
      onClose={onClose} 
      maxWidth="sm" 
      fullWidth
      aria-labelledby="customer-dialog-title"
    >
      <DialogTitle id="customer-dialog-title">
        {isEditing ? t('editCustomer') : t('createCustomer')}
      </DialogTitle>
      
      <form onSubmit={handleSubmit(handleFormSubmit)}>
        <DialogContent>
          <FormLayout gap={2}>
            <FormRow>
              <Controller
                name="name"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('name')}
                    error={!!errors.name}
                    helperText={errors.name?.message}
                    fullWidth
                    autoFocus
                  />
                )}
              />
            </FormRow>
            
            <FormRow>
              <Controller
                name="email"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label={t('email')}
                    type="email"
                    error={!!errors.email}
                    helperText={errors.email?.message}
                    fullWidth
                  />
                )}
              />
            </FormRow>
            
            <FormRow>
              <Controller
                name="status"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    select
                    label={t('status')}
                    error={!!errors.status}
                    helperText={errors.status?.message}
                    SelectProps={{ native: true }}
                    fullWidth
                  >
                    <option value={CUSTOMER_STATUSES.ACTIVE}>{t('active')}</option>
                    <option value={CUSTOMER_STATUSES.INACTIVE}>{t('inactive')}</option>
                    <option value={CUSTOMER_STATUSES.PENDING}>{t('pending')}</option>
                  </TextField>
                )}
              />
            </FormRow>
          </FormLayout>
        </DialogContent>
        
        <DialogActions>
          <Button onClick={onClose} variant="outlined">
            {t('cancel')}
          </Button>
          <Button 
            type="submit" 
            variant="contained" 
            loading={isSubmitting || loading}
          >
            {isEditing ? t('update') : t('create')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default CustomerForm;
