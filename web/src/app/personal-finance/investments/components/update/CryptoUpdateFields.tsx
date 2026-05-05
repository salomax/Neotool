'use client';

import * as React from 'react';
import { Box } from '@mui/material';
import { useWatch, useFormContext } from 'react-hook-form';
import { useTranslation } from '@/shared/i18n';
import { FormRowStack, FormStack } from '@/shared/components/ui/forms/components';
import { DatePickerField, MoneyInput, NumberField } from '@/shared/components/ui/forms/form';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { personalFinanceTranslations } from '../../../i18n';
import type { UpdateHoldingFormValues } from './types';
import { PositionSummaryStrip } from './PositionSummaryStrip';
import { UpdateFormSectionLabel } from './UpdateFormSectionLabel';

interface CryptoUpdateFieldsProps {
  hideSummaryStrip?: boolean;
  direction: InvestmentTransactionDirection;
  currentValue: number | null;
  currentQuantity: number;
  currencyCode: string;
  quantityUnit: string;
  locale: string;
  minDate: Date;
  maxDate: Date;
  tradeDateLabel: string;
  quantityLabel: string;
  unitPriceLabel: string;
}

export function CryptoUpdateFields({
  hideSummaryStrip = false,
  direction,
  currentValue,
  currentQuantity,
  currencyCode,
  quantityUnit,
  locale,
  minDate,
  maxDate,
  tradeDateLabel,
  quantityLabel,
  unitPriceLabel,
}: CryptoUpdateFieldsProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control } = useFormContext<UpdateHoldingFormValues>();
  const quantity = useWatch({ control, name: 'quantity' });
  const unitPrice = useWatch({ control, name: 'unitPrice' });
  const halfRowFieldSx = React.useMemo(
    () => ({ flex: 1, minWidth: 0, maxWidth: { xs: '100%', sm: '50%' } }),
    [],
  );

  const grossTradeValue = React.useMemo(() => {
    if (typeof quantity !== 'number' || typeof unitPrice !== 'number') return null;
    if (!Number.isFinite(quantity) || !Number.isFinite(unitPrice)) return null;
    return quantity * unitPrice;
  }, [quantity, unitPrice]);

  const projectedValue = React.useMemo(() => {
    if (currentValue == null || !Number.isFinite(currentValue)) return null;
    if (grossTradeValue == null) return null;
    return direction === InvestmentTransactionDirection.Inflow
      ? currentValue + grossTradeValue
      : currentValue - grossTradeValue;
  }, [currentValue, direction, grossTradeValue]);

  const projectedQuantity = React.useMemo(() => {
    if (typeof quantity !== 'number' || !Number.isFinite(quantity)) return null;
    return direction === InvestmentTransactionDirection.Inflow ? currentQuantity + quantity : currentQuantity - quantity;
  }, [currentQuantity, direction, quantity]);

  return (
    <FormStack>
      {!hideSummaryStrip ? (
        <PositionSummaryStrip
          currentValue={currentValue}
          currentQuantity={currentQuantity}
          projectedValue={projectedValue}
          projectedQuantity={projectedQuantity}
          currencyCode={currencyCode}
          quantityUnit={quantityUnit}
          locale={locale}
        />
      ) : null}

      <UpdateFormSectionLabel title={t('investments.updateHolding.sections.dates')} />
      <FormRowStack>
        <Box sx={halfRowFieldSx}>
          <DatePickerField name="tradeDate" label={tradeDateLabel} minDate={minDate} maxDate={maxDate} size="medium" />
        </Box>
      </FormRowStack>

      <UpdateFormSectionLabel title={t('investments.updateHolding.sections.trade')} />
      <FormRowStack>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <NumberField
            name="quantity"
            label={quantityLabel}
            locale={locale}
            size="medium"
            fractionDigits={8}
            fixedDecimalScale={false}
          />
        </Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <MoneyInput name="unitPrice" label={unitPriceLabel} currency="BRL" size="medium" />
        </Box>
      </FormRowStack>
    </FormStack>
  );
}
