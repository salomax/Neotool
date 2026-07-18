'use client';

import * as React from 'react';
import type { FieldPath, FieldValues, UseFormSetValue } from 'react-hook-form';
import { isValidDate } from '@/shared/utils/date/dateOnly';
import {
  coerceInvestmentSettlementDate,
  type InvestmentSettlementAssetKind,
} from '@/shared/utils/investment/settlementDatePolicy';

type Params<TFieldValues extends FieldValues> = {
  enabled: boolean;
  assetKind: InvestmentSettlementAssetKind;
  tradeDate: Date | undefined;
  settlementField?: FieldPath<TFieldValues>;
  setValue: UseFormSetValue<TFieldValues>;
};

/**
 * Whenever **Data da operação** changes, sets **Data de liquidação** to the product default
 * (D+0 for RF/funds/crypto, D+2 weekdays for stocks/FIIs), clamped to [trade, trade + 1 year].
 */
export function useSyncDefaultSettlementDate<TFieldValues extends FieldValues>({
  enabled,
  assetKind,
  tradeDate,
  settlementField = 'settlementDate' as FieldPath<TFieldValues>,
  setValue,
}: Params<TFieldValues>): void {
  React.useEffect(() => {
    if (!enabled || !isValidDate(tradeDate)) return;
    const next = coerceInvestmentSettlementDate(assetKind, tradeDate, undefined);
    setValue(settlementField, next as never, {
      shouldDirty: false,
      shouldValidate: false,
    });
  }, [assetKind, enabled, settlementField, setValue, tradeDate]);
}
