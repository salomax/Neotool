'use client';

import * as React from 'react';
import { alpha, useTheme } from '@mui/material';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { SegmentedControl } from '@/shared/components/ui/inputs/SegmentedControl';

interface DirectionToggleProps {
  value: InvestmentTransactionDirection;
  inflowLabel: string;
  outflowLabel: string;
  onChange: (value: InvestmentTransactionDirection) => void;
  disabled?: boolean;
}

export function DirectionToggle({ value, inflowLabel, outflowLabel, onChange, disabled = false }: DirectionToggleProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const inflowValue = InvestmentTransactionDirection.Inflow;
  const outflowValue = InvestmentTransactionDirection.Outflow;

  const inflowIdleColor = alpha(theme.palette.success.dark, isDark ? 0.9 : 0.75);
  const inflowActiveColor = theme.palette.success.main;
  const inflowActiveBg = alpha(theme.palette.success.main, isDark ? 0.26 : 0.16);

  const outflowIdleColor = theme.palette.error.main;
  const outflowActiveColor = theme.palette.error.main;
  const outflowActiveBg = alpha(theme.palette.error.main, isDark ? 0.26 : 0.16);
  const inputBorderColor = theme.custom.palette.inputBorder;

  const options = React.useMemo(
    () => [
      { value: inflowValue, label: inflowLabel, activeColor: inflowActiveColor },
      { value: outflowValue, label: outflowLabel, activeColor: outflowActiveColor },
    ],
    [inflowActiveColor, inflowLabel, inflowValue, outflowActiveColor, outflowLabel, outflowValue],
  );

  return (
    <SegmentedControl
      fullWidth
      size="medium"
      value={value}
      disabled={disabled}
      onChange={(nextValue) => {
        if (disabled) return;
        onChange(nextValue as InvestmentTransactionDirection);
      }}
      options={options}
      aria-label="holding-update-direction"
      sx={{
        border: `1px solid ${inputBorderColor} !important`,
        bgcolor: 'transparent',
        '&::before': {
          backgroundColor: value === inflowValue ? inflowActiveBg : outflowActiveBg,
          boxShadow: 'none',
        },
        [`& .MuiToggleButton-root[value="${inflowValue}"]`]: {
          color: inflowIdleColor,
        },
        [`& .MuiToggleButton-root[value="${outflowValue}"]`]: {
          color: outflowIdleColor,
        },
        [`& .MuiToggleButton-root[value="${inflowValue}"].Mui-selected`]: {
          color: `${inflowActiveColor} !important`,
        },
        [`& .MuiToggleButton-root[value="${outflowValue}"].Mui-selected`]: {
          color: `${outflowActiveColor} !important`,
        },
      }}
    />
  );
}
