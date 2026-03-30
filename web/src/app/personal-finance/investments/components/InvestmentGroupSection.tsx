'use client';

import React from 'react';
import { Box, type SxProps, type Theme } from '@mui/material';
import { AssetGroupCard } from './AssetGroupCard';
import { Label } from '@/shared/components/ui/primitives/Label';

interface HeaderCell {
  key: string;
  sx?: SxProps<Theme>;
  align?: 'left' | 'right';
  content: React.ReactNode;
}

interface InvestmentGroupSectionProps {
  expanded: boolean;
  onToggle: (expanded: boolean) => void;
  title: string;
  summary: string;
  leadingIcon?: React.ReactNode;
  summaryAdornment?: React.ReactNode;
  gainPercent?: number | null;
  allocationPercent?: number | null;
  appliedLabel?: string;
  appliedValue?: string;
  headerCells: HeaderCell[];
  actionColWidth: number;
  tableRadius: number;
  children: React.ReactNode;
}

export function InvestmentGroupSection({
  expanded,
  onToggle,
  title,
  summary,
  leadingIcon,
  summaryAdornment,
  gainPercent,
  allocationPercent,
  appliedLabel,
  appliedValue,
  headerCells,
  actionColWidth,
  tableRadius,
  children,
}: InvestmentGroupSectionProps) {
  return (
    <AssetGroupCard
      expanded={expanded}
      onToggle={onToggle}
      title={title}
      summary={summary}
      leadingIcon={leadingIcon}
      summaryAdornment={summaryAdornment}
      gainPercent={gainPercent}
      allocationPercent={allocationPercent}
      appliedLabel={appliedLabel}
      appliedValue={appliedValue}
    >
      <Box
        sx={{
          borderRadius: `0 0 ${tableRadius}px ${tableRadius}px`,
          overflow: 'hidden',
        }}
      >
        {/* Column header row */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            px: 2,
            py: 0.75,
          }}
        >
          {headerCells.map((cell) => (
            <Box
              key={cell.key}
              sx={{
                textAlign: cell.align ?? 'left',
                ...cell.sx,
              }}
            >
              <Label>{cell.content}</Label>
            </Box>
          ))}
          {/* Spacer matching action column */}
          <Box sx={{ width: actionColWidth, flexShrink: 0 }} />
        </Box>

        {children}
      </Box>
    </AssetGroupCard>
  );
}
