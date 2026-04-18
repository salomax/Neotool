"use client";

import React from "react";
import { Box, type SxProps, type Theme } from "@mui/material";
import { InvestmentGroupSection } from "../InvestmentGroupSection";
import type { AssetGroupKey, InvestmentHoldingGroup } from "./investmentTableHelpers";

export interface GroupTableHeaderCell {
  key: string;
  sx?: SxProps<Theme>;
  align?: "left" | "center" | "right";
  content: React.ReactNode;
}

export interface InvestmentGroupTableShellProps {
  hideGroupHeader: boolean;
  group: InvestmentHoldingGroup;
  expanded: boolean;
  onToggleGroup: (groupKey: AssetGroupKey, expanded: boolean) => void;
  title: string;
  summary: string;
  leadingIcon?: React.ReactNode;
  summaryAdornment?: React.ReactNode;
  gainPercent?: number | null;
  allocationPercent?: number | null;
  headerCells: GroupTableHeaderCell[];
  actionColWidth: number;
  tableRadius: number;
  children: React.ReactNode;
}

export function InvestmentGroupTableShell({
  hideGroupHeader,
  group,
  expanded,
  onToggleGroup,
  title,
  summary,
  leadingIcon,
  summaryAdornment,
  gainPercent,
  allocationPercent,
  headerCells,
  actionColWidth,
  tableRadius,
  children,
}: InvestmentGroupTableShellProps) {
  if (hideGroupHeader) {
    return (
      <Box sx={{ borderRadius: `${tableRadius}px`, overflow: "hidden" }}>
        <Box sx={{ display: "flex", alignItems: "center", px: 2, py: 0.75, position: "relative" }}>
          {headerCells.map((cell) => (
            <Box key={cell.key} sx={{ textAlign: cell.align ?? "left", ...cell.sx }}>
              {cell.content}
            </Box>
          ))}
          <Box sx={{ width: actionColWidth, flexShrink: 0, ml: "auto" }} />
        </Box>
        {children}
      </Box>
    );
  }

  return (
    <InvestmentGroupSection
      expanded={expanded}
      onToggle={(isExpanded) => onToggleGroup(group.key, isExpanded)}
      title={title}
      summary={summary}
      leadingIcon={leadingIcon}
      summaryAdornment={summaryAdornment}
      gainPercent={gainPercent}
      allocationPercent={allocationPercent}
      headerCells={headerCells}
      actionColWidth={actionColWidth}
      tableRadius={tableRadius}
    >
      {children}
    </InvestmentGroupSection>
  );
}
