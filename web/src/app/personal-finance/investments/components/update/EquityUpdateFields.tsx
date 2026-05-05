'use client';

import * as React from 'react';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { Accordion, AccordionDetails, AccordionSummary, Box, Typography } from '@mui/material';
import { useWatch, useFormContext } from 'react-hook-form';
import { useTranslation } from '@/shared/i18n';
import { FormRowStack, FormStack } from '@/shared/components/ui/forms/components';
import { DatePickerField, MoneyInput, NumberField } from '@/shared/components/ui/forms/form';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { personalFinanceTranslations } from '../../../i18n';
import type { UpdateHoldingFormValues } from './types';
import { PositionSummaryStrip } from './PositionSummaryStrip';
import { UpdateFormSectionLabel } from './UpdateFormSectionLabel';

interface EquityUpdateFieldsProps {
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
  quantityLabel: string;
  unitPriceLabel: string;
  brokerageFeeLabel: string;
  settlementFeeLabel: string;
  otherFeesLabel: string;
  incomeTaxLabel: string;
  feesSectionLabel: string;
}

export function EquityUpdateFields({
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
  quantityLabel,
  unitPriceLabel,
  brokerageFeeLabel,
  settlementFeeLabel,
  otherFeesLabel,
  incomeTaxLabel,
  feesSectionLabel,
}: EquityUpdateFieldsProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control } = useFormContext<UpdateHoldingFormValues>();
  const quantity = useWatch({ control, name: 'quantity' });
  const unitPrice = useWatch({ control, name: 'unitPrice' });
  const brokerageFee = useWatch({ control, name: 'brokerageFee' }) ?? 0;
  const settlementFee = useWatch({ control, name: 'settlementFee' }) ?? 0;
  const otherFees = useWatch({ control, name: 'otherFees' }) ?? 0;

  const grossTradeValue = React.useMemo(() => {
    if (typeof quantity !== 'number' || typeof unitPrice !== 'number') return null;
    if (!Number.isFinite(quantity) || !Number.isFinite(unitPrice)) return null;
    return quantity * unitPrice;
  }, [quantity, unitPrice]);

  const totalFees = React.useMemo(() => {
    return (Number.isFinite(brokerageFee) ? brokerageFee : 0) + (Number.isFinite(settlementFee) ? settlementFee : 0) + (Number.isFinite(otherFees) ? otherFees : 0);
  }, [brokerageFee, otherFees, settlementFee]);

  const netTradeValue = React.useMemo(() => {
    if (grossTradeValue == null) return null;
    return direction === InvestmentTransactionDirection.Inflow ? grossTradeValue + totalFees : grossTradeValue - totalFees;
  }, [direction, grossTradeValue, totalFees]);

  const projectedGrossPositionValue = React.useMemo(() => {
    if (currentValue == null || !Number.isFinite(currentValue)) return null;
    if (grossTradeValue == null) return null;
    return direction === InvestmentTransactionDirection.Inflow
      ? currentValue + grossTradeValue
      : currentValue - grossTradeValue;
  }, [currentValue, direction, grossTradeValue]);

  const projectedNetPositionValue = React.useMemo(() => {
    if (currentValue == null || !Number.isFinite(currentValue)) return null;
    if (netTradeValue == null) return null;
    return direction === InvestmentTransactionDirection.Inflow
      ? currentValue + netTradeValue
      : currentValue - netTradeValue;
  }, [currentValue, direction, netTradeValue]);

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
          projectedValue={projectedGrossPositionValue}
          projectedSecondaryValue={projectedNetPositionValue}
          projectedSecondaryLabel={t('investments.updateHolding.summary.netValue')}
          projectedQuantity={projectedQuantity}
          currencyCode={currencyCode}
          quantityUnit={quantityUnit}
          locale={locale}
        />
      ) : null}

      <UpdateFormSectionLabel title={t('investments.updateHolding.sections.dates')} />
      <FormRowStack>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <DatePickerField name="tradeDate" label={tradeDateLabel} minDate={minDate} maxDate={maxDate} size="medium" />
        </Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <DatePickerField
            name="settlementDate"
            label={settlementDateLabel}
            minDate={minDate}
            maxDate={maxDate}
            size="medium"
          />
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
            fractionDigits={0}
            fixedDecimalScale={false}
          />
        </Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <MoneyInput name="unitPrice" label={unitPriceLabel} currency="BRL" size="medium" />
        </Box>
      </FormRowStack>

      <UpdateFormSectionLabel title={feesSectionLabel} />
      <Accordion disableGutters elevation={0} square>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{feesSectionLabel}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <FormStack>
            <FormRowStack>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput name="brokerageFee" label={brokerageFeeLabel} currency="BRL" size="medium" />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput name="settlementFee" label={settlementFeeLabel} currency="BRL" size="medium" />
              </Box>
            </FormRowStack>
            <FormRowStack>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput name="otherFees" label={otherFeesLabel} currency="BRL" size="medium" />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput name="incomeTax" label={incomeTaxLabel} currency="BRL" size="medium" />
              </Box>
            </FormRowStack>
          </FormStack>
        </AccordionDetails>
      </Accordion>
    </FormStack>
  );
}
