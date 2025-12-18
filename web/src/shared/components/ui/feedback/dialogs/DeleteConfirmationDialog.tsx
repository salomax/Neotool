"use client";

import React from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
} from "@mui/material";
import { WarningIcon } from "@/shared/ui/mui-imports";
import { Button } from "@/shared/components/ui/primitives/Button";

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
  // Parse message to extract the item name and make it bold
  const message = item
    ? t(messageKey).replace("{name}", item.name)
    : t(messageKey);
  
  // Split message to find where the item name appears and make it bold
  const renderMessage = () => {
    if (!item) {
      return message;
    }
    
    // Find the item name in the message (handle both quoted and unquoted versions)
    const nameWithQuotes = `"${item.name}"`;
    const hasQuotes = message.includes(nameWithQuotes);
    const searchName = hasQuotes ? nameWithQuotes : item.name;
    
    const parts = message.split(searchName);
    if (parts.length === 2) {
      return (
        <>
          {parts[0]}
          <Typography component="span" fontWeight="bold">
            {hasQuotes ? `"${item.name}"` : item.name}
          </Typography>
          {parts[1]}
        </>
      );
    }
    
    return message;
  };

  return (
    <Dialog
      open={open}
      onClose={onCancel}
      aria-labelledby="delete-dialog-title"
      aria-describedby="delete-dialog-description"
      PaperProps={{
        sx: {
          borderRadius: 2,
        },
      }}
    >
      <DialogContent
        sx={{
          display: "flex",
          gap: 3,
          padding: 4,
          pt: 4,
        }}
      >
        {/* Warning Icon */}
        <Box
          sx={{
            display: "flex",
            alignItems: "flex-start",
            flexShrink: 0,
          }}
        >
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: "50%",
              backgroundColor: (theme) => 
                (theme as any).custom?.palette?.errorLightBg || 
                (theme.palette.mode === 'light' 
                  ? 'rgba(220, 38, 38, 0.1)'
                  : 'rgba(239, 68, 68, 0.15)'),
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <WarningIcon
              sx={{
                color: "error.main",
                fontSize: 28,
              }}
            />
          </Box>
        </Box>

        {/* Content */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <DialogTitle
            id="delete-dialog-title"
            sx={{
              padding: 0,
              marginBottom: 1.5,
              fontWeight: 600,
            }}
          >
            {t(titleKey)}
            {item && `: ${item.name}`}
          </DialogTitle>
          <Typography
            id="delete-dialog-description"
            variant="body1"
            sx={{
              margin: 0,
              color: "text.primary",
            }}
          >
            {renderMessage()}
          </Typography>
        </Box>
      </DialogContent>
      <DialogActions
        sx={{
          padding: 3,
          paddingTop: 2,
          gap: 2,
        }}
      >
        <Button
          onClick={onCancel}
          disabled={loading}
          variant="outlined"
          data-testid="delete-dialog-cancel"
        >
          {t(cancelKey)}
        </Button>
        <Button
          onClick={onConfirm}
          color="error"
          variant="contained"
          disabled={loading}
          loading={loading}
          loadingText={t(deletingKey)}
          data-testid="delete-dialog-confirm"
        >
          {t(deleteKey)}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default DeleteConfirmationDialog;
