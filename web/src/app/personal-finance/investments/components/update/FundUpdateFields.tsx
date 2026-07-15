'use client';

import * as React from 'react';
import { Box } from '@mui/material';
import { useFormContext, useWatch } from 'react-hook-form';
import { useTranslation } from '@/shared/i18n';
import { FormRowStack, FormStack } from '@/shared/components/ui/forms/components';
import { DatePickerField, MoneyInput, NumberField } from '@/shared/components/ui/forms/form';
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

interface FundUpdateFieldsProps {
  hideSummaryStrip?: boolean;
  direction: InvestmentTransactionDirection;
  currentValue: number | null;
  currentQuantity: number;
  currencyCode: string;
  quantityUnit: string;
  locale: string;
  minDate: Date;
  maxDate: Date;
  isManualPricingMode: boolean;
  tradeDateLabel: string;
  settlementDateLabel: string;
  grossValueLabel: string;
  netValueLabel: string;
  quantityLabel: string;
  unitPriceLabel: string;
  valuedAtLabel: string;
  incomeTaxLabel: string;
  iofLabel: string;
}

export function FundUpdateFields({
  hideSummaryStrip = false,
  direction,
  currentValue,
  currentQuantity,
  currencyCode,
  quantityUnit,
  locale,
  minDate,
  maxDate,
  isManualPricingMode,
  tradeDateLabel,
  settlementDateLabel,
  grossValueLabel,
  netValueLabel,
  quantityLabel,
  unitPriceLabel,
  valuedAtLabel,
  incomeTaxLabel,
  iofLabel,
}: FundUpdateFieldsProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control } = useFormContext<UpdateHoldingFormValues>();
  const grossValue = useWatch({ control, name: 'grossValue' });
  const netValue = useWatch({ control, name: 'netValue' });
  const quantity = useWatch({ control, name: 'quantity' });
  const unitPrice = useWatch({ control, name: 'unitPrice' });

  const halfRowFieldSx = React.useMemo(
    () => ({ flex: 1, minWidth: 0, maxWidth: { xs: '100%', sm: '50%' } }),
    [],
  );

  const projectedValue = React.useMemo(
    () =>
      getFundFixedIncomeProjectedValue({
        currentValue,
        currentQuantity,
        direction,
        quantity,
        unitPrice,
        grossValue,
        netValue,
      }),
    [currentQuantity, currentValue, direction, grossValue, netValue, quantity, unitPrice],
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
      {isManualPricingMode ? (
        <FormRowStack>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <NumberField
              name="quantity"
              label={quantityLabel}
              locale={locale}
              fractionDigits={6}
              fixedDecimalScale={false}
              size="medium"
            />
          </Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <MoneyInput
              name="unitPrice"
              label={unitPriceLabel}
              currency="BRL"
              fractionDigits={8}
              fixedDecimalScale={false}
              size="medium"
            />
          </Box>
        </FormRowStack>
      ) : (
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
      )}

      {direction === InvestmentTransactionDirection.Inflow && isManualPricingMode ? (
        <>
          <UpdateFormSectionLabel title={t('investments.updateHolding.sections.pricing')} />
          <FormRowStack>
            <Box sx={halfRowFieldSx}>
              <DatePickerField
                name="valuedAt"
                label={valuedAtLabel}
                minDate={minDate}
                maxDate={maxDate}
                size="medium"
              />
            </Box>
          </FormRowStack>
        </>
      ) : null}

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
