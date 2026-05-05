'use client';

import * as React from 'react';
import type { DefaultValues, FieldValues } from 'react-hook-form';
import type { ZodType } from 'zod';
import {
  INVESTMENT_TRANSACTIONS,
} from '@/lib/graphql/operations/banking/queries';
import {
  InvestmentTransactionDirection,
  type RecordInvestmentTransactionInput,
  type UpdateInvestmentTransactionInput,
} from '@/lib/graphql/types/__generated__/graphql';
import { useRecordHoldingForm, type UseRecordHoldingFormReturn } from './useRecordHoldingForm';
import type { UpdateHolding, UpdateHoldingEditTransaction } from '../components/update/types';

interface UseUpdateHoldingFormOptions<TFields extends FieldValues> {
  holding: UpdateHolding;
  direction: InvestmentTransactionDirection;
  schema: ZodType<TFields, any, any>;
  defaultValues: DefaultValues<TFields>;
  onBuildInput: (
    data: TFields,
    context: { holding: UpdateHolding; direction: InvestmentTransactionDirection },
  ) => RecordInvestmentTransactionInput;
  editTransaction?: UpdateHoldingEditTransaction | null;
  onSuccess?: () => void;
  successToastMessage?: string;
}

export function useUpdateHoldingForm<TFields extends FieldValues>(
  options: UseUpdateHoldingFormOptions<TFields>,
): UseRecordHoldingFormReturn<TFields> {
  const { holding, direction, schema, defaultValues, onBuildInput, editTransaction, onSuccess, successToastMessage } = options;

  const buildRecordInput = React.useCallback(
    (data: TFields): RecordInvestmentTransactionInput => {
      const input = onBuildInput(data, { holding, direction });
      return {
        ...input,
        assetId: holding.asset.id,
      };
    },
    [direction, holding, onBuildInput],
  );

  const buildUpdateInput = React.useCallback(
    (data: TFields): UpdateInvestmentTransactionInput => {
      const recordInput = buildRecordInput(data);
      const {
        assetId: _assetId,
        financialAccountId: _financialAccountId,
        ...updateInput
      } = recordInput;
      return updateInput;
    },
    [buildRecordInput],
  );

  return useRecordHoldingForm<TFields>({
    schema,
    financialAccountId: holding.financialAccountId,
    editTransaction,
    editHoldingId: holding.id,
    defaultValues,
    onBuildInput: buildRecordInput,
    onBuildUpdateInput: buildUpdateInput,
    onSuccess,
    successToastMessage,
    additionalRefetchQueries: [
      {
        query: INVESTMENT_TRANSACTIONS,
        variables: { holdingId: holding.id },
      },
    ],
  });
}
