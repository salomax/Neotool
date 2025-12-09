"use client";

import React from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
} from "@mui/material";

export interface DeleteConfirmationDialogProps<T extends { id: string; name: string }> {
  /**
   * Whether the dialog is open
   */
  open: boolean;
  /**
   * Item to be deleted. If null, the message will not include the item name.
   */
  item: T | null;
  /**
   * Whether the delete operation is in progress
   */
  loading: boolean;
  /**
   * Callback invoked when user confirms deletion
   */
  onConfirm: () => void;
  /**
   * Callback invoked when user cancels deletion
   */
  onCancel: () => void;
  /**
   * Translation key for dialog title
   */
  titleKey: string;
  /**
   * Translation key for dialog message. Should contain "{name}" placeholder if item name should be included.
   */
  messageKey: string;
  /**
   * Translation key for cancel button text
   */
  cancelKey: string;
  /**
   * Translation key for delete button text
   */
  deleteKey: string;
  /**
   * Translation key for delete button text when loading
   */
  deletingKey: string;
  /**
   * Translation function (from useTranslation hook)
   */
  t: (key: string, params?: Record<string, string>) => string;
}

/**
 * DeleteConfirmationDialog component - Generic delete confirmation dialog
 * 
 * A reusable dialog component for confirming deletion of items. Supports i18n
 * through translation keys and replaces {name} placeholder in messages.
 * 
 * @example
 * ```tsx
 * <DeleteConfirmationDialog
 *   open={deleteConfirmOpen}
 *   item={roleToDelete}
 *   loading={deleteLoading}
 *   onConfirm={handleDeleteConfirm}
 *   onCancel={handleDeleteCancel}
 *   titleKey="roleManagement.deleteDialog.title"
 *   messageKey="roleManagement.deleteDialog.message"
 *   cancelKey="roleManagement.deleteDialog.cancel"
 *   deleteKey="roleManagement.deleteDialog.delete"
 *   deletingKey="roleManagement.deleteDialog.deleting"
 *   t={t}
 * />
 * ```
 */
export function DeleteConfirmationDialog<T extends { id: string; name: string }>({
  open,
  item,
  loading,
  onConfirm,
  onCancel,
  titleKey,
  messageKey,
  cancelKey,
  deleteKey,
  deletingKey,
  t,
}: DeleteConfirmationDialogProps<T>) {
  const message = item
    ? t(messageKey).replace("{name}", item.name)
    : t(messageKey);

  return (
    <Dialog
      open={open}
      onClose={onCancel}
      aria-labelledby="delete-dialog-title"
      aria-describedby="delete-dialog-description"
    >
      <DialogTitle id="delete-dialog-title">
        {t(titleKey)}
      </DialogTitle>
      <DialogContent>
        <DialogContentText id="delete-dialog-description">
          {message}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={onCancel}
          disabled={loading}
          data-testid="delete-dialog-cancel"
        >
          {t(cancelKey)}
        </Button>
        <Button
          onClick={onConfirm}
          color="error"
          variant="contained"
          disabled={loading}
          data-testid="delete-dialog-confirm"
        >
          {loading ? t(deletingKey) : t(deleteKey)}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default DeleteConfirmationDialog;
