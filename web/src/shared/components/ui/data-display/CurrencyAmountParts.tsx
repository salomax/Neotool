'use client';

import * as React from 'react';
import { Box, Typography, type TypographyProps } from '@mui/material';

export interface CurrencyAmountPartsProps extends Omit<TypographyProps, 'children'> {
  value: number | null | undefined;
  currency: string;
  locale?: string;
  formatOptions?: Intl.NumberFormatOptions;
  placeholder?: React.ReactNode;
  secondaryScale?: number;
  secondaryOpacity?: number;
}

export function CurrencyAmountParts({
  value,
  currency,
  locale,
  formatOptions,
  placeholder = '—',
  secondaryScale = 0.6,
  secondaryOpacity = 0.7,
  sx,
  ...typographyProps
}: CurrencyAmountPartsProps) {
  const formattersRef = React.useRef<Map<string, Intl.NumberFormat>>(new Map());

  const hasNumber = typeof value === 'number' && Number.isFinite(value);

  const resolvedLocale =
    locale ||
    (typeof navigator !== 'undefined' && navigator.language) ||
    Intl.DateTimeFormat().resolvedOptions().locale ||
    'pt-BR';

  const formatter = React.useMemo(() => {
    const key = `${resolvedLocale}:${currency}:${JSON.stringify(formatOptions ?? {})}`;
    let cached = formattersRef.current.get(key);
    if (!cached) {
      cached = new Intl.NumberFormat(resolvedLocale, { style: 'currency', currency, ...(formatOptions ?? {}) });
      formattersRef.current.set(key, cached);
    }
    return cached;
  }, [currency, formatOptions, resolvedLocale]);

  const parts = React.useMemo(() => {
    if (!hasNumber) return null;
    try {
      return formatter.formatToParts(value as number);
    } catch {
      return null;
    }
  }, [formatter, hasNumber, value]);

  if (!hasNumber || !parts) {
    return (
      <Typography sx={{ fontVariantNumeric: 'tabular-nums', ...sx }} {...typographyProps}>
        {placeholder}
      </Typography>
    );
  }

  return (
    <Typography sx={{ fontVariantNumeric: 'tabular-nums', ...sx }} {...typographyProps}>
      {parts.map((part, index) => {
        const isMain = part.type === 'integer' || part.type === 'group';
        return (
          <Box
            key={`${part.type}-${index}`}
            component="span"
            sx={{
              fontSize: isMain ? 'inherit' : `${secondaryScale}em`,
              opacity: isMain ? 1 : secondaryOpacity,
              mr: part.type === 'currency' ? 0.5 : 0,
            }}
          >
            {part.value}
          </Box>
        );
      })}
    </Typography>
  );
}

