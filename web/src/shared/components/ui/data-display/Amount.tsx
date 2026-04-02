'use client';

import * as React from 'react';
import { Typography, type TypographyProps } from '@mui/material';
import { useTheme } from '@mui/material/styles';

export type AmountTone = 'auto' | 'positive' | 'negative' | 'neutral' | 'transfer';

export interface AmountProps extends Omit<TypographyProps, 'children'> {
  value: number | null | undefined;
  currency: string;
  locale?: string;
  tone?: AmountTone;
  noWrap?: boolean;
}

export const Amount: React.FC<AmountProps> = ({
  value,
  currency,
  locale,
  tone = 'auto',
  variant = 'body2',
  fontWeight = 'medium',
  noWrap = true,
  sx,
  ...typographyProps
}) => {
  const theme = useTheme();
  const formattersRef = React.useRef<Map<string, Intl.NumberFormat>>(new Map());

  const hasNumber = typeof value === 'number' && Number.isFinite(value);

  const resolvedLocale =
    locale ||
    (typeof navigator !== 'undefined' && navigator.language) ||
    Intl.DateTimeFormat().resolvedOptions().locale ||
    'pt-BR';

  const color =
    tone === 'transfer'
      ? theme.palette.info.main
      : tone === 'positive'
      ? theme.palette.success.main
      : tone === 'negative'
      ? theme.palette.error.main
      : tone === 'neutral'
      ? theme.palette.text.primary
      : hasNumber && (value as number) > 0
      ? theme.palette.success.main
      : hasNumber && (value as number) < 0
      ? theme.palette.error.main
      : theme.palette.text.primary;

  const formatted = React.useMemo(() => {
    if (!hasNumber) return '—';
    const key = `${resolvedLocale}:${currency}`;
    let formatter = formattersRef.current.get(key);
    if (!formatter) {
      try {
        formatter = new Intl.NumberFormat(resolvedLocale, { style: 'currency', currency });
        formattersRef.current.set(key, formatter);
      } catch {
        return (value as number).toLocaleString(resolvedLocale);
      }
    }
    try {
      return formatter.format(value as number);
    } catch {
      return (value as number).toLocaleString(resolvedLocale);
    }
  }, [currency, hasNumber, resolvedLocale, value]);

  return (
    <Typography
      variant={variant}
      fontWeight={fontWeight}
      noWrap={noWrap}
      color={color}
      sx={{ fontVariantNumeric: 'tabular-nums', ...sx }}
      {...typographyProps}
    >
      {formatted}
    </Typography>
  );
};

export default Amount;

