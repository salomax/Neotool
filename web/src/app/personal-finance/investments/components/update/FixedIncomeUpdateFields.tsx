'use client';

import * as React from 'react';
import { Box } from '@mui/material';
import { useFormContext, useWatch } from 'react-hook-form';
import { useTranslation } from '@/shared/i18n';
import { FormRowStack, FormStack } from '@/shared/components/ui/forms/components';
import { MoneyInput, NumberField } from '@/shared/components/ui/forms/form';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { personalFinanceTranslations } from '../../../i18n';
import type { UpdateHoldingFormValues } from './types';
import { PositionSummaryStrip } from './PositionSummaryStrip';
import {
  getFundFixedIncomeProjectedValue,
  getProjectedQuantity,
} from './positionSummaryCalculations';
import { UpdateFormSectionLabel } from './UpdateFormSectionLabel';
import { InvestmentTradeAndSettlementDateRow } from '../InvestmentTradeAndSettlementDateRow';

interface FixedIncomeUpdateFieldsProps {
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
  settlementDateLabel: string;
  grossValueLabel: string;
  netValueLabel: string;
  quantityLabel: string;
  incomeTaxLabel: string;
  iofLabel: string;
}

export function FixedIncomeUpdateFields({
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
  settlementDateLabel,
  grossValueLabel,
  netValueLabel,
  quantityLabel,
  incomeTaxLabel,
  iofLabel,
}: FixedIncomeUpdateFieldsProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control, setValue, getFieldState } = useFormContext<UpdateHoldingFormValues>();
  const grossValue = useWatch({ control, name: 'grossValue' });
  const netValue = useWatch({ control, name: 'netValue' });
  const quantity = useWatch({ control, name: 'quantity' });

  const halfRowFieldSx = React.useMemo(
    () => ({ flex: 1, minWidth: 0, maxWidth: { xs: '100%', sm: '50%' } }),
    [],
  );

  React.useEffect(() => {
    if (direction !== InvestmentTransactionDirection.Outflow) return;
    if (typeof grossValue !== 'number' || typeof netValue !== 'number') return;
    const incomeTaxState = getFieldState('incomeTax');
    if (incomeTaxState.isDirty) return;
    setValue('incomeTax', Math.max(grossValue - netValue, 0), { shouldDirty: false, shouldValidate: true });
  }, [direction, getFieldState, grossValue, netValue, setValue]);

  const projectedValue = React.useMemo(
    () =>
      getFundFixedIncomeProjectedValue({
        currentValue,
        currentQuantity,
        direction,
        quantity,
        grossValue,
        netValue,
      }),
    [currentQuantity, currentValue, direction, grossValue, netValue, quantity],
  );

  const projectedQuantity = React.useMemo(
    () => getProjectedQuantity(quantity, currentQuantity, direction),
    [currentQuantity, direction, quantity],
  );

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
      <InvestmentTradeAndSettlementDateRow<UpdateHoldingFormValues>
        tradeDateLabel={tradeDateLabel}
        settlementDateLabel={settlementDateLabel}
        minDate={minDate}
        tradeMaxDate={maxDate}
        size="medium"
      />

      {direction === InvestmentTransactionDirection.Inflow ? (
        <>
          <UpdateFormSectionLabel title={t('investments.updateHolding.sections.amounts')} />
          <FormRowStack>
            <Box sx={halfRowFieldSx}>
              <MoneyInput name="grossValue" label={grossValueLabel} currency="BRL" size="medium" />
            </Box>
          </FormRowStack>
        </>
      ) : (
        <>
          <UpdateFormSectionLabel title={t('investments.updateHolding.sections.amounts')} />
          <FormRowStack>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput name="grossValue" label={grossValueLabel} currency="BRL" size="medium" />
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput name="netValue" label={netValueLabel} currency="BRL" size="medium" />
            </Box>
          </FormRowStack>
        </>
      )}

      <UpdateFormSectionLabel title={t('investments.updateHolding.sections.trade')} />
      <FormRowStack>
        <Box sx={halfRowFieldSx}>
          <NumberField
            name="quantity"
            label={quantityLabel}
            locale={locale}
            fractionDigits={6}
            fixedDecimalScale={false}
            size="medium"
          />
        </Box>
      </FormRowStack>

      {direction === InvestmentTransactionDirection.Outflow ? (
        <>
          <UpdateFormSectionLabel title={t('investments.updateHolding.sections.taxes')} />
          <FormRowStack>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput name="incomeTax" label={incomeTaxLabel} currency="BRL" size="medium" />
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput name="iof" label={iofLabel} currency="BRL" size="medium" />
            </Box>
          </FormRowStack>
        </>
      ) : null}
    </FormStack>
  );
}
