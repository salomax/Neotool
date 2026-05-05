"use client";

import * as React from "react";
import type { ErrorLike as ApolloError } from "@apollo/client";
import type { DocumentNode } from "@apollo/client";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, type DefaultValues, type FieldValues, type UseFormReturn } from "react-hook-form";
import type { ZodType } from "zod";
import {
  RECORD_INVESTMENT_TRANSACTION,
  UPDATE_INVESTMENT_TRANSACTION,
} from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import type {
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

interface UseRecordHoldingFormConfig<TFields extends FieldValues> {
  schema: ZodType<TFields, any, any>;
  financialAccountId: string | null;
  editTransaction?: { id: string } | null;
  editHoldingId?: string | null;
  onBuildInput: (data: TFields) => RecordInvestmentTransactionInput;
  onBuildUpdateInput?: (data: TFields) => UpdateInvestmentTransactionInput;
  onSuccess?: () => void;
  successToastMessage?: string;
  defaultValues?: DefaultValues<TFields>;
  additionalRefetchQueries?: Array<
    | DocumentNode
    | {
        query: DocumentNode;
        variables?: Record<string, unknown>;
      }
  >;
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
    defaultValues: config.defaultValues,
  });

  const [recordMutation, { loading: recording, error: recordError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION);

  const [updateMutation, { loading: updating, error: updateError }] = useMutation<
    { updateInvestmentTransaction: { id: string } },
    { id: string; input: UpdateInvestmentTransactionInput }
  >(UPDATE_INVESTMENT_TRANSACTION);

  const refetchQueries = React.useMemo(
    () => [
      INVESTMENT_HOLDINGS,
      INVESTMENT_CASH_BALANCE,
      INVESTMENT_CASH_TRANSACTIONS,
      INVESTMENT_PORTFOLIO_SUMMARY,
      ...(config.additionalRefetchQueries ?? []),
    ],
    [config.additionalRefetchQueries],
  );

  const onSubmit = React.useCallback(
    (e: React.FormEvent) => {
      void form.handleSubmit(async (data) => {
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
            refetchQueries,
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
            refetchQueries,
          });
        }

        toast.success(config.successToastMessage ?? t("actions.save"));
        config.onSuccess?.();
      })(e);
    },
    [config.editTransaction, config.financialAccountId, config.onBuildInput, config.onBuildUpdateInput, config.onSuccess, config.successToastMessage, form, recordMutation, refetchQueries, toast, t, updateMutation],
  );

  return {
    form,
    onSubmit,
    isLoading: recording || updating,
    mutationError: recordError ?? updateError ?? undefined,
  };
}

export type { UseRecordHoldingFormConfig, UseRecordHoldingFormReturn };
