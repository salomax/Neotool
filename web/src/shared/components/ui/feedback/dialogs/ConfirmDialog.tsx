"use client";

import * as React from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Typography,
} from "@mui/material";
import { Button } from "../../primitives/Button";

export interface ConfirmDialogProps {
  open: boolean;
  title: React.ReactNode;
  description?: React.ReactNode;
  confirmText: React.ReactNode;
  cancelText?: React.ReactNode;
  confirmColor?: "primary" | "secondary" | "error" | "warning" | "info" | "success";
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  "data-testid"?: string;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  open,
  title,
  description,
  confirmText,
  cancelText = "Cancel",
  confirmColor = "primary",
  loading = false,
  onConfirm,
  onCancel,
  "data-testid": dataTestId,
}) => {
  return (
    <Dialog
      open={open}
      onClose={() => {
        if (!loading) onCancel();
      }}
      aria-labelledby="confirm-title"
      data-testid={dataTestId}
    >
      <DialogTitle id="confirm-title">{title}</DialogTitle>
      {description ? (
        <DialogContent>
          {typeof description === "string" ? (
            <Typography variant="body2" color="text.secondary">
              {description}
            </Typography>
          ) : (
            description
          )}
        </DialogContent>
      ) : null}
      <DialogActions>
        <Button
          onClick={onCancel}
          variant="outlined"
          disabled={loading}
        >
          {cancelText}
        </Button>
        <Button onClick={onConfirm} color={confirmColor} variant="contained" loading={loading}>
          {confirmText}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
