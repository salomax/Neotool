'use client';

import { Box, Stack, Typography } from '@mui/material';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display/CurrencyAmountParts';
import { Label } from '@/shared/components/ui/primitives/Label';
import { i18n } from '@/shared/i18n';

export function TaxLoadSummary({
  percent,
  amount,
  label,
  size = 48,
}: {
  percent: number | null;
  amount: number | null;
  label: string;
  size?: number;
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
      {percent != null ? (
        <Box sx={{ position: 'relative', width: '100%', mt: 0.25 }}>
          <Typography
            variant="caption"
            sx={{
              position: 'absolute',
              right: 0,
              top: 0,
              transform: 'translateY(-100%)',
              color: 'warning.main',
              fontWeight: 700,
              lineHeight: 1,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {`${Math.round(percent)}%`}
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
            <Box sx={{ width: `${100 - percent}%`, bgcolor: 'action.disabledBackground' }} />
            <Box sx={{ width: `${percent}%`, bgcolor: 'warning.main' }} />
          </Box>
        </Box>
      ) : null}
    </Stack>
  );
}
