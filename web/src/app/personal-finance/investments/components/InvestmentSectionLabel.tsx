"use client";

import * as React from "react";
import { Stack } from "@mui/material";
import type { SxProps, Theme } from "@mui/material/styles";
import { Label } from "@/shared/components/ui/primitives/Label";

interface InvestmentSectionLabelProps {
  title: React.ReactNode;
  icon?: React.ReactNode;
  sx?: SxProps<Theme>;
  labelSx?: SxProps<Theme>;
}

export function InvestmentSectionLabel({
  title,
  icon,
  sx,
  labelSx,
}: InvestmentSectionLabelProps) {
  const stackSx: SxProps<Theme> = [
    { minWidth: 0 },
    ...(sx == null ? [] : Array.isArray(sx) ? sx : [sx]),
  ];
  const mergedLabelSx: SxProps<Theme> = [
    { fontSize: "0.8rem", fontWeight: 500, mb: 0 },
    ...(labelSx == null ? [] : Array.isArray(labelSx) ? labelSx : [labelSx]),
  ];

  return (
    <Stack direction="row" spacing={1} alignItems="center" sx={stackSx}>
      <Label icon={icon} sx={mergedLabelSx}>
        {title}
      </Label>
    </Stack>
  );
}

export default InvestmentSectionLabel;
