'use client';

import * as React from 'react';
import { Box } from '@mui/material';
import type { FieldPath, FieldValues } from 'react-hook-form';
import { useFormContext, useWatch } from 'react-hook-form';
import { FormRowStack } from '@/shared/components/ui/forms/components';
import { DatePickerField } from '@/shared/components/ui/forms/form';
import { getInvestmentSettlementBounds } from '@/shared/utils/investment/settlementDatePolicy';

export type InvestmentTradeAndSettlementDateRowProps<TFieldValues extends FieldValues> = {
  tradeDateField?: FieldPath<TFieldValues>;
  settlementDateField?: FieldPath<TFieldValues>;
  tradeDateLabel: string;
  settlementDateLabel: string;
  minDate: Date;
  /** Upper bound for **Data da operação** (typically today). */
  tradeMaxDate: Date;
  size?: 'medium' | 'small';
  tradeDateTestId?: string;
  settlementDateTestId?: string;
};

/**
 * Standard row: **Data da operação** + **Data de liquidação**.
 * Settlement is constrained to [trade date, trade date + 1 year] once trade is set.
 */
export function InvestmentTradeAndSettlementDateRow<TFieldValues extends FieldValues>({
  tradeDateField = 'tradeDate' as FieldPath<TFieldValues>,
  settlementDateField = 'settlementDate' as FieldPath<TFieldValues>,
  tradeDateLabel,
  settlementDateLabel,
  minDate,
  tradeMaxDate,
  size = 'medium',
  tradeDateTestId,
  settlementDateTestId,
}: InvestmentTradeAndSettlementDateRowProps<TFieldValues>) {
  const { control } = useFormContext<TFieldValues>();
  const tradeDateRaw = useWatch({ control, name: tradeDateField }) as unknown;

  const tradeDate =
    tradeDateRaw && typeof tradeDateRaw === 'object' && tradeDateRaw instanceof Date && !Number.isNaN(tradeDateRaw.getTime())
      ? tradeDateRaw
      : undefined;

  const settlementBounds = React.useMemo(
    () => getInvestmentSettlementBounds(tradeDate, minDate),
    [tradeDate, minDate],
  );

  const tradeName = tradeDateField as string;
  const settleName = settlementDateField as string;

  return (
    <FormRowStack>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <DatePickerField
          name={tradeName}
          label={tradeDateLabel}
          minDate={minDate}
          maxDate={tradeMaxDate}
          size={size}
          data-testid={tradeDateTestId}
        />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <DatePickerField
          name={settleName}
          label={settlementDateLabel}
          minDate={settlementBounds.min}
          maxDate={settlementBounds.max}
          size={size}
          data-testid={settlementDateTestId}
        />
      </Box>
    </FormRowStack>
  );
}
