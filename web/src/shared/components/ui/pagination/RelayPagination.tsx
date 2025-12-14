"use client";

import React from "react";
import { Box, Button, Stack } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";
import { PaginationRange } from "./PaginationRange";
import type { PageInfo } from "@/shared/hooks/pagination";

export interface PaginationRangeData {
  start: number;
  end: number;
  total: number | null;
}

export interface RelayPaginationProps {
  pageInfo: PageInfo | null;
  paginationRange: PaginationRangeData;
  loading?: boolean;
  onLoadNext: () => void;
  onLoadPrevious: () => void;
  onGoToFirst: () => void;
  canLoadPreviousPage?: boolean;
}

/**
 * RelayPagination component - Displays pagination controls following GraphQL Relay standards
 * 
 * Provides First, Previous, and Next navigation buttons along with a pagination summary.
 * This component is designed to work with Relay-style cursor-based pagination.
 * 
 * @param pageInfo - PageInfo object from Relay connection (hasNextPage, hasPreviousPage, etc.)
 * @param paginationRange - Range object with start, end, and total count
 * @param loading - Loading state to disable buttons during data fetching
 * @param onLoadNext - Callback for loading next page
 * @param onLoadPrevious - Callback for loading previous page
 * @param onGoToFirst - Callback for going to first page
 * @param canLoadPreviousPage - Optional flag to independently control whether the "Previous" action is available
 */
export const RelayPagination: React.FC<RelayPaginationProps> = ({
  pageInfo,
  paginationRange,
  loading = false,
  onLoadNext,
  onLoadPrevious,
  onGoToFirst,
  canLoadPreviousPage,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);

  // Don't render if there's no pagination info and no valid range
  const hasPaginationControls = pageInfo && (pageInfo.hasNextPage || pageInfo.hasPreviousPage);
  const hasValidRange = paginationRange.start > 0 && paginationRange.end > 0;

  if (!hasPaginationControls && !hasValidRange) {
    return null;
  }

  const resolvedCanLoadPrevious =
    typeof canLoadPreviousPage === "boolean"
      ? canLoadPreviousPage
      : Boolean(pageInfo?.hasPreviousPage);

  return (
    <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mt: 1 }}>
      {hasValidRange && (
        <PaginationRange
          start={paginationRange.start}
          end={paginationRange.end}
          total={paginationRange.total}
        />
      )}
      {hasPaginationControls && (
        <Stack direction="row" spacing={2} alignItems="center">
          <Button
            variant="outlined"
            onClick={onGoToFirst}
            disabled={!pageInfo.hasPreviousPage || loading}
            size="small"
            data-testid="pagination-first-button"
          >
            {t("pagination.first")}
          </Button>
          <Button
            variant="outlined"
            onClick={onLoadPrevious}
            disabled={!resolvedCanLoadPrevious || loading}
            size="small"
            data-testid="pagination-previous-button"
          >
            {t("pagination.previous")}
          </Button>
          <Button
            variant="outlined"
            onClick={onLoadNext}
            disabled={!pageInfo.hasNextPage || loading}
            size="small"
            data-testid="pagination-next-button"
          >
            {t("pagination.next")}
          </Button>
        </Stack>
      )}
    </Box>
  );
};
