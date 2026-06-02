"use client";

import * as React from "react";
import { Box } from "@mui/material";
import type { SxProps, Theme } from "@mui/material/styles";
import { Label, type LabelProps } from "@/shared/components/ui/primitives/Label";

export interface SectionDividerProps {
  label: React.ReactNode;
  labelProps?: Omit<LabelProps, "children">;
  sx?: SxProps<Theme>;
  lineSx?: SxProps<Theme>;
}

export const SectionDivider: React.FC<SectionDividerProps> = ({ label, labelProps, sx, lineSx }) => {
  const { sx: labelSx, ...labelPropsRest } = labelProps ?? {};

  const labelSxMerged: SxProps<Theme> = [
    { mb: 0, textAlign: "left", flexShrink: 0 },
    ...(labelSx == null ? [] : Array.isArray(labelSx) ? labelSx : [labelSx]),
  ];

  const containerSx: SxProps<Theme> = [
    {
      display: "flex",
      alignItems: "flex-end",
      columnGap: 2,
      width: "100%",
      minWidth: 0,
    },
    ...(sx == null ? [] : Array.isArray(sx) ? sx : [sx]),
  ];

  const lineSxMerged: SxProps<Theme> = [
    {
      flex: 1,
      minWidth: 0,
      height: 0,
      borderBottom: "1px solid",
      borderColor: "divider",
    },
    ...(lineSx == null ? [] : Array.isArray(lineSx) ? lineSx : [lineSx]),
  ];

  return (
    <Box sx={containerSx}>
      <Label {...labelPropsRest} sx={labelSxMerged}>
        {label}
      </Label>
      <Box aria-hidden sx={lineSxMerged} />
    </Box>
  );
};

export default SectionDivider;
