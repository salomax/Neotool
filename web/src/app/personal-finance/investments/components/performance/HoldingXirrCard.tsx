"use client";

import * as React from "react";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { Box, Paper, Tooltip, Typography } from "@mui/material";
import { GainPercent } from "@/shared/components/ui/data-display";
import { Label } from "@/shared/components/ui/primitives/Label";

interface HoldingXirrCardProps {
  title: string;
  tooltip: string;
  sinceLabel: string | null;
  sincePrefix: string;
  xirr: number | null;
  locale: string;
}

export function HoldingXirrCard({
  title,
  tooltip,
  sinceLabel,
  sincePrefix,
  xirr,
  locale,
}: HoldingXirrCardProps) {
  if (xirr == null) return null;

  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 0.75, mb: 0.75 }}>
        <Label sx={{ mb: 0 }}>{title}</Label>
        <Tooltip title={tooltip}>
          <InfoOutlinedIcon sx={{ fontSize: 16, color: "text.secondary" }} />
        </Tooltip>
      </Box>

      <GainPercent value={xirr} locale={locale} variant="h6" textSx={{ fontWeight: 700 }} iconSize={14} />

      {sinceLabel ? (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
          {`${sincePrefix} ${sinceLabel}`}
        </Typography>
      ) : null}
    </Paper>
  );
}

export default HoldingXirrCard;
