'use client';

import * as React from 'react';
import { Box, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { Label } from '@/shared/components/ui/primitives/Label';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';

export interface PositionSummaryStripProps {
  currentValue: number | null;
  currentQuantity: number;
  projectedValue: number | null;
  projectedQuantity: number | null;
  currencyCode: string;
  quantityUnit: string;
  locale: string;
  projectedSecondaryValue?: number | null;
  projectedSecondaryLabel?: string;
}

const currencyFormatOptions: Intl.NumberFormatOptions = {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
};

function formatQuantity(value: number | null, quantityUnit: string, locale: string): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return `${new Intl.NumberFormat(locale, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 5,
  }).format(value)} ${quantityUnit}`;
}

export function PositionSummaryStrip({
  currentValue,
  currentQuantity,
  projectedValue,
  projectedQuantity,
  currencyCode,
  quantityUnit,
  locale,
  projectedSecondaryValue,
  projectedSecondaryLabel,
}: PositionSummaryStripProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
    <Box
      sx={{
        borderRadius: 1,
        p: 1.5,
        bgcolor: (theme) =>
          theme.palette.mode === 'dark'
            ? alpha(theme.palette.common.white, 0.06)
            : alpha(theme.palette.common.black, 0.04),
        display: 'grid',
        gridTemplateColumns: '1fr auto 1fr',
        gap: 1,
        alignItems: 'start',
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Label sx={{ mb: 0 }}>
          {t('investments.updateHolding.summary.current')}
        </Label>
        <Box sx={{ mt: 0.5 }}>
          <CurrencyAmountParts
            value={currentValue}
            currency={currencyCode}
            locale={locale}
            formatOptions={currencyFormatOptions}
            variant="h5"
          />
        </Box>
        <Typography variant="body2" sx={{ fontWeight: 400, mt: 0.5 }} noWrap>
          {formatQuantity(currentQuantity, quantityUnit, locale)}
        </Typography>
      </Box>

      <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'start', lineHeight: 1.2 }}>
        →
      </Typography>

      <Box sx={{ minWidth: 0 }}>
        <Label sx={{ mb: 0 }}>
          {t('investments.updateHolding.summary.afterOperation')}
        </Label>
        <Box sx={{ mt: 0.5 }}>
          <CurrencyAmountParts
            value={projectedValue}
            currency={currencyCode}
            locale={locale}
            formatOptions={currencyFormatOptions}
            variant="h5"
          />
        </Box>

        {projectedSecondaryLabel ? (
          <>
            <Label sx={{ display: 'block', mb: 0, mt: 0.5 }}>
              {projectedSecondaryLabel}
            </Label>
            <Box sx={{ mt: 0.5 }}>
              <CurrencyAmountParts
                value={projectedSecondaryValue ?? null}
                currency={currencyCode}
                locale={locale}
                formatOptions={currencyFormatOptions}
                variant="body2"
                sx={{ fontWeight: 600 }}
              />
            </Box>
          </>
        ) : null}

        <Typography variant="body2" sx={{ fontWeight: 400, mt: 0.5 }} noWrap>
          {formatQuantity(projectedQuantity, quantityUnit, locale)}
        </Typography>
      </Box>
    </Box>
  );
}

export default PositionSummaryStrip;
