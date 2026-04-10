"use client";

import React from "react";
import { Box, Skeleton, Stack, alpha, useTheme } from "@mui/material";

const SKELETON_CARD_COUNT = 6;

export function InvestmentGridSkeleton() {
  const theme = useTheme();
  return (
    <Box
      sx={{
        mt: 2,
        display: "grid",
        gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" },
        gap: 2,
      }}
    >
      {Array.from({ length: SKELETON_CARD_COUNT }).map((_, idx) => (
        <Box
          key={`investment-grid-skeleton-${idx}`}
          sx={{
            border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
            borderRadius: 2,
            minHeight: 180,
            p: 2,
            display: "flex",
            flexDirection: "column",
            justifyContent: "space-between",
            bgcolor: "background.paper",
          }}
        >
          <Stack direction="row" alignItems="center" spacing={1}>
            <Skeleton variant="rounded" width={26} height={26} />
            <Skeleton variant="text" width="48%" height={24} />
          </Stack>
          <Box sx={{ mt: 2 }}>
            <Skeleton variant="rectangular" width="100%" height={32} sx={{ borderRadius: 1, mb: 1 }} />
            <Stack direction="row" justifyContent="space-between" alignItems="flex-end">
              <Box>
                <Skeleton variant="text" width={76} height={14} />
                <Skeleton variant="text" width={124} height={30} />
              </Box>
              <Skeleton variant="text" width={64} height={20} />
            </Stack>
          </Box>
          <Box sx={{ mt: 1.5, pt: 1, borderTop: `1px solid ${alpha(theme.palette.divider, 0.6)}` }}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <Skeleton variant="text" width="38%" height={24} />
              <Skeleton variant="rectangular" width={1} height={18} />
              <Skeleton variant="text" width="38%" height={24} />
              <Skeleton variant="rectangular" width={1} height={18} />
              <Skeleton variant="circular" width={22} height={22} />
            </Stack>
          </Box>
        </Box>
      ))}
    </Box>
  );
}
