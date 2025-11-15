"use client";
import React from "react";
import { Box, Skeleton, Stack } from "@mui/material";

export function PageSkeleton({
  "data-testid": testId,
}: {
  "data-testid"?: string;
}) {
  return (
    <Box data-testid={testId} sx={{ p: 2 }}>
      <Skeleton variant="text" width={220} height={32} sx={{ mb: 1 }} />
      <Skeleton variant="rectangular" height={36} sx={{ mb: 2 }} />
      <Stack spacing={1}>
        <Skeleton variant="rectangular" height={48} />
        <Skeleton variant="rectangular" height={48} />
        <Skeleton variant="rectangular" height={48} />
      </Stack>
    </Box>
  );
}

export default PageSkeleton;
