'use client';

import { Box, Stack, Typography } from '@mui/material';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display/CurrencyAmountParts';
import { Label } from '@/shared/components/ui/primitives/Label';
import { i18n } from '@/shared/i18n';

export function TaxPercentBar({ percent }: { percent: number }) {
  const safePercent = Math.max(0, Math.min(100, percent));

  return (
    <Box sx={{ position: 'relative', width: '100%', mt: 0.25 }}>
      <Typography
        variant="caption"
        sx={(theme) => ({
          position: 'absolute',
          right: 0,
          top: 0,
          transform: 'translateY(-100%)',
          color: (theme as any).custom?.palette?.tax ?? theme.palette.warning.main,
          fontWeight: 700,
          lineHeight: 1,
          fontVariantNumeric: 'tabular-nums',
        })}
      >
        {`${Math.round(safePercent)}%`}
      </Typography>
      <Box
        sx={{
          width: '100%',
          height: 6,
          borderRadius: 999,
          overflow: 'hidden',
          display: 'flex',
        }}
      >
        <Box sx={{ width: `${100 - safePercent}%`, bgcolor: 'action.disabledBackground' }} />
        <Box
          sx={(theme) => ({
            width: `${safePercent}%`,
            bgcolor: (theme as any).custom?.palette?.tax ?? theme.palette.warning.main,
          })}
        />
      </Box>
    </Box>
  );
}

export function TaxLoadSummary({
  percent,
  amount,
  label,
}: {
  percent: number | null;
  amount: number | null;
  label: string;
}) {
  return (
    <Stack spacing={0.25} sx={{ minWidth: 160 }}>
      <Label sx={{ mb: 0 }}>{label}</Label>
      <CurrencyAmountParts
        value={amount}
        currency="BRL"
        locale={i18n.language}
        variant="subtitle1"
        sx={{ fontWeight: 800, whiteSpace: 'nowrap', fontSize: '1.4em', lineHeight: 1 }}
      />
      {percent != null ? <TaxPercentBar percent={percent} /> : null}
    </Stack>
  );
}
