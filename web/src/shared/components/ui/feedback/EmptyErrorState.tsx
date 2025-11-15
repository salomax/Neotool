"use client";

import * as React from "react";
import { Box, Typography, Button, Stack } from "@mui/material";

export interface EmptyStateProps {
  title: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
  illustration?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  actionText,
  onAction,
  illustration,
}) => {
  return (
    <Box textAlign="center" p={4}>
      <Stack spacing={2} alignItems="center">
        <Box>{illustration ?? <span style={{ fontSize: 40 }}>🗂️</span>}</Box>
        <Typography variant="h6">{title}</Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" maxWidth={420}>
            {description}
          </Typography>
        )}
        {actionText && (
          <Button variant="contained" onClick={onAction}>
            {actionText}
          </Button>
        )}
      </Stack>
    </Box>
  );
};

export interface ErrorStateProps {
  title?: string;
  description?: string;
  retryText?: string;
  onRetry?: () => void;
  illustration?: React.ReactNode;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = "Something went wrong",
  description,
  retryText = "Retry",
  onRetry,
  illustration,
}) => {
  return (
    <Box textAlign="center" p={4}>
      <Stack spacing={2} alignItems="center">
        <Box>{illustration ?? <span style={{ fontSize: 40 }}>⚠️</span>}</Box>
        <Typography variant="h6">{title}</Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" maxWidth={420}>
            {description}
          </Typography>
        )}
        {onRetry && (
          <Button variant="contained" color="error" onClick={onRetry}>
            {retryText}
          </Button>
        )}
      </Stack>
    </Box>
  );
};
