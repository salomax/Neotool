'use client';

import * as React from 'react';
import { Box } from '@mui/material';
import { useWatch, useFormContext } from 'react-hook-form';
import { useTranslation } from '@/shared/i18n';
import { FormRowStack, FormStack } from '@/shared/components/ui/forms/components';
import { MoneyInput, NumberField } from '@/shared/components/ui/forms/form';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { personalFinanceTranslations } from '../../../i18n';
import type { UpdateHoldingFormValues } from './types';
import { PositionSummaryStrip } from './PositionSummaryStrip';
import {
  getProjectedPositionValue,
  getProjectedQuantity,
  getTradeGrossValue,
} from './positionSummaryCalculations';
import { UpdateFormSectionLabel } from './UpdateFormSectionLabel';
import { InvestmentTradeAndSettlementDateRow } from '../InvestmentTradeAndSettlementDateRow';

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
  totalFeesLabel: string;
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
  totalFeesLabel,
  feesSectionLabel,
}: EquityUpdateFieldsProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control } = useFormContext<UpdateHoldingFormValues>();
  const quantity = useWatch({ control, name: 'quantity' });
  const unitPrice = useWatch({ control, name: 'unitPrice' });

  const grossTradeValue = React.useMemo(
    () => getTradeGrossValue(quantity, unitPrice, currentValue, currentQuantity),
    [quantity, unitPrice, currentValue, currentQuantity],
  );

  const projectedValue = React.useMemo(
    () => getProjectedPositionValue(currentValue, grossTradeValue, direction),
    [currentValue, direction, grossTradeValue],
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
      <FormRowStack>
        <Box sx={{ flex: 1, minWidth: 0, maxWidth: { xs: '100%', sm: '50%' } }}>
          <MoneyInput name="totalFees" label={totalFeesLabel} currency="BRL" size="medium" />
        </Box>
      </FormRowStack>
    </FormStack>
  );
}
