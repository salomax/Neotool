"use client";

import * as React from "react";
import { Box, Typography, Button, Stack, CircularProgress } from "@mui/material";
import SearchOffIcon from "@mui/icons-material/SearchOff";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import { useTranslation } from "react-i18next";
import { extractErrorMessage } from "@/shared/utils/error";

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
        <Box>
          {illustration ?? (
            <SearchOffIcon
              sx={{
                fontSize: 48,
                color: "text.disabled",
              }}
            />
          )}
        </Box>
        <Typography variant="h6">{title}</Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" maxWidth={420}>
            {description}
          </Typography>
        )}
        {actionText && (
          <Button variant="contained" onClick={() => onAction?.()}>
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
        <Box>{illustration ?? <ErrorOutlineIcon sx={{ fontSize: 44, color: "text.disabled" }} />}</Box>
        <Typography variant="h6">{title}</Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" maxWidth={420}>
            {description}
          </Typography>
        )}
        {onRetry && (
          <Button variant="contained" color="error" onClick={() => onRetry()}>
            {retryText}
          </Button>
        )}
      </Stack>
    </Box>
  );
};

export interface QueryErrorStateProps {
  error: unknown;
  title?: string;
  description?: string;
  retryText?: string;
  onRetry?: () => void;
  illustration?: React.ReactNode;
}

export const QueryErrorState: React.FC<QueryErrorStateProps> = ({
  error,
  title,
  description,
  retryText,
  onRetry,
  illustration,
}) => {
  const { t } = useTranslation("common");
  const friendlyDescription = description ?? extractErrorMessage(error, t("errors.default"));

  return (
    <ErrorState
      title={title ?? t("cardGrid.unableToLoadResults")}
      description={friendlyDescription}
      retryText={retryText ?? t("cardGrid.retry")}
      onRetry={onRetry}
      illustration={illustration}
    />
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
