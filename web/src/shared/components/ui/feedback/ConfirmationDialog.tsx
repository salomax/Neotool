"use client";

import React from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Typography } from '@mui/material';
import { Button } from '../primitives/Button';

export interface ConfirmationDialogProps {
  /** Whether the dialog is open */
  open: boolean;
  /** Dialog close handler */
  onClose: () => void;
  /** Confirm action handler */
  onConfirm: () => void;
  /** Dialog title */
  title: string;
  /** Dialog message */
  message: string;
  /** Confirm button text */
  confirmText?: string;
  /** Cancel button text */
  cancelText?: string;
  /** Confirm button color */
  confirmColor?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';
  /** Whether confirm action is loading */
  loading?: boolean;
  /** Whether to disable the confirm button */
  disabled?: boolean;
  /** Data test ID for the dialog */
  'data-testid'?: string;
}

export function ConfirmationDialog({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  confirmColor = 'error',
  loading = false,
  disabled = false,
  'data-testid': dataTestId,
}: ConfirmationDialogProps) {
  return (
    <Dialog 
      open={open} 
      onClose={onClose}
      aria-labelledby="confirmation-dialog-title"
      aria-describedby="confirmation-dialog-description"
      data-testid={dataTestId}
    >
      <DialogTitle id="confirmation-dialog-title">
        {title}
      </DialogTitle>
      
      <DialogContent>
        <Typography id="confirmation-dialog-description">
          {message}
        </Typography>
      </DialogContent>
      
      <DialogActions>
        <Button 
          onClick={onClose} 
          variant="outlined"
          disabled={loading}
        >
          {cancelText}
        </Button>
        <Button
          onClick={onConfirm}
          color={confirmColor}
          variant="contained"
          loading={loading}
          disabled={disabled}
        >
          {confirmText}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default ConfirmationDialog;
