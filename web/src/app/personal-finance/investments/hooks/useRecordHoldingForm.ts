"use client";

import * as React from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, type FieldValues, type UseFormReturn } from "react-hook-form";
import type { ZodSchema } from "zod";
import {
  RECORD_INVESTMENT_TRANSACTION,
  UPDATE_INVESTMENT_TRANSACTION,
} from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import type {
  InvestmentTransaction,
  RecordInvestmentTransactionInput,
  UpdateInvestmentTransactionInput,
} from "@/lib/graphql/types/__generated__/graphql";
import type {
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";

type ApolloError = Error;

interface UseRecordHoldingFormConfig<TFields extends FieldValues> {
  schema: ZodSchema<TFields>;
  financialAccountId: string | null;
  editTransaction?: InvestmentTransaction | null;
  editHoldingId?: string | null;
  onBuildInput: (data: TFields) => RecordInvestmentTransactionInput;
  onBuildUpdateInput?: (data: TFields) => UpdateInvestmentTransactionInput;
  onSuccess?: () => void;
}

interface UseRecordHoldingFormReturn<TFields extends FieldValues> {
  form: UseFormReturn<TFields>;
  onSubmit: (e: React.FormEvent) => void;
  isLoading: boolean;
  mutationError: ApolloError | undefined;
}

export function useRecordHoldingForm<TFields extends FieldValues>(
  config: UseRecordHoldingFormConfig<TFields>,
): UseRecordHoldingFormReturn<TFields> {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();

  const form = useForm<TFields>({
    resolver: zodResolver(config.schema),
  });

  const [recordMutation, { loading: recording, error: recordError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION);

  const [updateMutation, { loading: updating, error: updateError }] = useMutation<
    { updateInvestmentTransaction: { id: string } },
    { id: string; input: UpdateInvestmentTransactionInput }
  >(UPDATE_INVESTMENT_TRANSACTION);

  const onValidSubmit = React.useMemo(
    () =>
      form.handleSubmit(async (data) => {
        if (config.editTransaction != null) {
          const updateInput =
            config.onBuildUpdateInput?.(data) ??
            ({
              ...config.onBuildInput(data),
            } as UpdateInvestmentTransactionInput);

          await updateMutation({
            variables: {
              id: config.editTransaction.id,
              input: updateInput,
            },
            refetchQueries: [
              INVESTMENT_HOLDINGS,
              INVESTMENT_CASH_BALANCE,
              INVESTMENT_PORTFOLIO_SUMMARY,
            ],
          });
        } else {
          if (!config.financialAccountId) return;
          const recordInput = config.onBuildInput(data);
          await recordMutation({
            variables: {
              input: {
                ...recordInput,
                financialAccountId: config.financialAccountId,
              },
            },
            refetchQueries: [
              INVESTMENT_HOLDINGS,
              INVESTMENT_CASH_BALANCE,
              INVESTMENT_PORTFOLIO_SUMMARY,
            ],
          });
        }

        toast.success(t("actions.save"));
        config.onSuccess?.();
      }),
    [
      config,
      form,
      recordMutation,
      toast,
      t,
      updateMutation,
    ],
  );

  const onSubmit = React.useCallback(
    (e: React.FormEvent) => {
      void onValidSubmit(e);
    },
    [onValidSubmit],
  );

  return {
    form,
    onSubmit,
    isLoading: recording || updating,
    mutationError: recordError ?? updateError ?? undefined,
  };
}

export type { UseRecordHoldingFormConfig, UseRecordHoldingFormReturn };
