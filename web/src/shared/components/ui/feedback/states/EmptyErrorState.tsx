"use client";

import * as React from "react";
import { Box, Typography, Button, Stack, CircularProgress } from "@mui/material";

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

export interface LoadingStateProps {
  /**
   * Whether to show the loading state
   */
  isLoading: boolean;
  /**
   * Minimum height for the loading container
   * @default "400px"
   */
  minHeight?: string | number;
  /**
   * Size of the CircularProgress spinner
   * @default undefined (uses MUI default)
   */
  size?: number;
}

/**
 * LoadingState component - Displays a centered loading spinner
 * 
 * Provides a consistent loading state UI with:
 * - Centered spinner (both horizontally and vertically)
 * - Configurable minimum height
 * - Configurable spinner size
 * - Handles conditional rendering internally based on isLoading prop
 * 
 * @example
 * ```tsx
 * <LoadingState isLoading={loading} />
 * <LoadingState isLoading={loading} minHeight="300px" size={32} />
 * ```
 */
export const LoadingState: React.FC<LoadingStateProps> = ({
  isLoading,
  minHeight = "400px",
  size,
}) => {
  if (!isLoading) {
    return null;
  }

  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight,
      }}
    >
      <CircularProgress size={size} />
    </Box>
  );
};
