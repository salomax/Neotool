'use client';

import * as React from 'react';
import { Box } from '@mui/material';
import { alpha } from '@mui/material/styles';
import type { SxProps, Theme } from '@mui/material/styles';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { Label } from '@/shared/components/ui/primitives/Label';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import {
  resolveDisplayedProjectedQuantity,
  resolveDisplayedProjectedValue,
} from './positionSummaryCalculations';

export interface PositionSummaryStripProps {
  currentValue: number | null;
  currentQuantity: number;
  projectedValue: number | null;
  projectedQuantity: number | null;
  currencyCode: string;
  quantityUnit: string;
  locale: string;
}

const currencyFormatOptions: Intl.NumberFormatOptions = {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
};

const quantityLabelSx: SxProps<Theme> = {
  mb: 0,
  mt: 0.5,
  textTransform: 'none',
  fontSize: '0.875rem',
  fontWeight: 400,
  color: 'text.primary',
  letterSpacing: 0,
};

function formatQuantity(value: number | null, quantityUnit: string, locale: string): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return `${new Intl.NumberFormat(locale, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 5,
  }).format(value)} ${quantityUnit}`;
}

interface PositionSummaryColumnProps {
  title: string;
  value: number | null;
  quantity: string;
  currencyCode: string;
  locale: string;
}

function PositionSummaryColumn({
  title,
  value,
  quantity,
  currencyCode,
  locale,
}: PositionSummaryColumnProps) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Label sx={{ mb: 0 }}>{title}</Label>
      <Box sx={{ mt: 0.5 }}>
        <CurrencyAmountParts
          value={value}
          currency={currencyCode}
          locale={locale}
          formatOptions={currencyFormatOptions}
          variant="h5"
        />
      </Box>
      <Label sx={quantityLabelSx} noWrap>
        {quantity}
      </Label>
    </Box>
  );
}

export function PositionSummaryStrip({
  currentValue,
  currentQuantity,
  projectedValue,
  projectedQuantity,
  currencyCode,
  quantityUnit,
  locale,
}: PositionSummaryStripProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const displayProjectedValue = resolveDisplayedProjectedValue(projectedValue, currentValue);
  const displayProjectedQuantity = resolveDisplayedProjectedQuantity(
    projectedQuantity,
    currentQuantity,
  );

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
        gridTemplateColumns: '1fr 1fr',
        gap: 1,
        alignItems: 'start',
      }}
    >
      <PositionSummaryColumn
        title={t('investments.updateHolding.summary.current')}
        value={currentValue}
        quantity={formatQuantity(currentQuantity, quantityUnit, locale)}
        currencyCode={currencyCode}
        locale={locale}
      />

      <PositionSummaryColumn
        title={t('investments.updateHolding.summary.afterOperation')}
        value={displayProjectedValue}
        quantity={formatQuantity(displayProjectedQuantity, quantityUnit, locale)}
        currencyCode={currencyCode}
        locale={locale}
      />
    </Box>
  );
}

export default PositionSummaryStrip;
