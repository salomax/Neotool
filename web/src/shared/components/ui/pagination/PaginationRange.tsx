"use client";

import React from "react";
import { Typography } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { authorizationManagementTranslations } from "@/app/(settings)/settings/i18n";

export interface PaginationRangeProps {
  start: number;
  end: number;
  total: number | null;
}

/**
 * PaginationRange component - Displays pagination range information
 * 
 * Shows different formats based on available data:
 * - "1 to 10 of 100 results" when totalCount is available (always provided by backend)
 * - "1 to 10" when totalCount is null (defensive fallback, should not occur in practice)
 * - "1 item" or "1 of 100 results" for single item
 * - "No items" for empty results
 * 
 * Note: The backend always calculates totalCount, so it should always be available.
 * The null handling is kept for defensive coding and type compatibility.
 * 
 * @param start - Starting item number (1-based)
 * @param end - Ending item number (1-based)
 * @param total - Total count of items (always calculated by backend, null only as defensive fallback)
 */
export const PaginationRange: React.FC<PaginationRangeProps> = ({
  start,
  end,
  total,
}) => {
  const { t } = useTranslation(authorizationManagementTranslations);

  // Handle empty results
  if (start === 0 && end === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t("pagination.empty")}
      </Typography>
    );
  }

  // Handle single item
  if (start === end) {
    if (total !== null) {
      return (
        <Typography variant="body2" color="text.secondary">
          {t("pagination.singleItemTotal", { count: start, total })}
        </Typography>
      );
    } else {
      return (
        <Typography variant="body2" color="text.secondary">
          {t("pagination.singleItem", { count: start })}
        </Typography>
      );
    }
  }

  // Handle range with total count
  if (total !== null) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t("pagination.range", { start, end, total })}
      </Typography>
    );
  }

  // Handle range without total count
  return (
    <Typography variant="body2" color="text.secondary">
      {t("pagination.rangeNoTotal", { start, end })}
    </Typography>
  );
};

