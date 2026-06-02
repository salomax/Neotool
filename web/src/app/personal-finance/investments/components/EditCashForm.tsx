"use client";

import React from "react";
import { useApolloClient, useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider } from "@mui/material";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import dayjs from "dayjs";
import { FormProvider, useForm } from "react-hook-form";
import { z } from "zod";
import {
  UPDATE_INVESTMENT_CASH_TRANSACTION,
} from "@/lib/graphql/operations/banking/investmentCashMutations";
import type {
  UpdateInvestmentCashTransactionMutation,
  UpdateInvestmentCashTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/investmentCashMutations.generated";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { DatePickerField, FormTextField, MoneyInput } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { useToast } from "@/shared/providers";
import { formatDateOnly, parseDateOnly, toStartOfDayLocal } from "@/shared/utils/date/dateOnly";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { personalFinanceTranslations } from "../../i18n";

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export type EditCashFormValues = {
  amount: number | undefined;
  date: Date;
  description: string;
};

export interface EditCashFormProps {
  transactionId: string;
  currencyCode: string;
  initialAmount: number;
  initialDate: string;
  initialDescription: string;
  listContextAccountId?: string | null;
  resetSignal?: number;
  formId: string;
  onSuccess: () => void;
  onCancel?: () => void;
  cancelText: string;
  submitText: string;
  submittingText: string;
  cancelTestId?: string;
  submitTestId?: string;
}

export function EditCashForm({
  transactionId,
  currencyCode,
  initialAmount,
  initialDate,
  initialDescription,
  listContextAccountId,
  resetSignal = 0,
  formId,
  onSuccess,
  onCancel,
  cancelText,
  submitText,
  submittingText,
  cancelTestId,
  submitTestId,
}: EditCashFormProps) {
  const apolloClient = useApolloClient();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR";
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(() => getLocaleSeparators(locale), [locale]);

  const schema = React.useMemo(
    () =>
      z
        .object({
          amount: z.preprocess(
            (value) => {
              if (value === "" || value === null || value === undefined) return undefined;
              if (typeof value === "number") return value;
              const raw = String(value);
              let normalized: string;
              if (localeGroup) {
                const decimalIndex = raw.lastIndexOf(localeDecimal);
                if (decimalIndex >= 0 && raw.indexOf(localeGroup, decimalIndex + 1) !== -1) {
                  return undefined;
                }
                normalized = raw.replace(new RegExp(escapeRegExp(localeGroup), "g"), "").replace(localeDecimal, ".");
              } else {
                normalized = raw.replace(localeDecimal, ".");
              }
              const parsed = Number(normalized);
              return Number.isNaN(parsed) ? undefined : parsed;
            },
            z.number().optional(),
          ),
          date: z.date(),
          description: z.string(),
        })
        .superRefine((data, ctx) => {
          const amt = data.amount;
          if (amt == null || !Number.isFinite(amt) || amt <= 0) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.cash.validation.amountPositive"),
              path: ["amount"],
            });
          }
          const dateDay = dayjs(data.date).startOf("day");
          const today = dayjs().startOf("day");
          if (dateDay.isAfter(today)) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.cash.validation.dateNotInFuture"),
              path: ["date"],
            });
          }
        }),
    [localeDecimal, localeGroup, t],
  );

  const methods = useForm<EditCashFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      amount: initialAmount,
      date: parseDateOnly(initialDate) ?? toStartOfDayLocal(new Date()),
      description: initialDescription ?? "",
    },
  });

  const { handleSubmit, reset } = methods;

  React.useEffect(() => {
    reset({
      amount: initialAmount,
      date: parseDateOnly(initialDate) ?? toStartOfDayLocal(new Date()),
      description: initialDescription ?? "",
    });
  }, [transactionId, initialAmount, initialDate, initialDescription, resetSignal, reset]);

  const [updateCash, { loading: submitting, error: mutationError }] = useMutation<
    UpdateInvestmentCashTransactionMutation,
    UpdateInvestmentCashTransactionMutationVariables
  >(UPDATE_INVESTMENT_CASH_TRANSACTION, {
    refetchQueries: [
      {
        query: INVESTMENT_CASH_BALANCE,
        variables: { financialAccountId: listContextAccountId ?? undefined },
      },
      {
        query: INVESTMENT_PORTFOLIO_SUMMARY,
        variables: { financialAccountId: listContextAccountId ?? undefined },
      },
      { query: INVESTMENT_PORTFOLIO_SUMMARY },
      {
        query: INVESTMENT_CASH_TRANSACTIONS,
        variables: { financialAccountId: listContextAccountId ?? undefined, first: 5, after: undefined },
      },
      {
        query: INVESTMENT_CASH_TRANSACTIONS,
        variables: { financialAccountId: listContextAccountId ?? undefined, first: 20, after: undefined },
      },
    ],
  });

  const onSubmit = async (data: EditCashFormValues) => {
    await updateCash({
      variables: {
        id: transactionId,
        input: {
          amount: data.amount!,
          date: formatDateOnly(data.date),
          description: data.description.trim() || undefined,
        },
      },
    });
    await apolloClient.refetchQueries({ include: [INVESTMENT_PORTFOLIO_SUMMARY] });
    success(t("investments.cash.movements.successUpdated"));
    onSuccess();
  };

  return (
    <FormProvider {...methods}>
      <Box sx={{ p: 0 }}>
        <form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormStack>
            <ErrorAlert error={mutationError} />

            <FormRowStack alignItems="flex-start">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="amount"
                  label={t("investments.cash.amount")}
                  currency={currencyCode}
                  size="medium"
                  data-testid="edit-cash-amount"
                />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <DatePickerField
                  name="date"
                  label={t("investments.cash.date")}
                  maxDate={dayjs().startOf("day").toDate()}
                  size="medium"
                  data-testid="edit-cash-date"
                />
              </Box>
            </FormRowStack>

            <FormTextField
              name="description"
              label={t("investments.cash.description")}
              size="medium"
              data-testid="edit-cash-description"
            />

            <Divider  />

            <Drawer.ActionButtons
              cancelText={cancelText}
              submitText={submitText}
              submittingText={submittingText}
              onCancel={onCancel ?? (() => {})}
              submitForm={formId}
              cancelDisabled={submitting}
              submitDisabled={submitting}
              submitting={submitting}
              cancelTestId={cancelTestId}
              submitTestId={submitTestId}
            />
          </FormStack>
        </form>
      </Box>
    </FormProvider>
  );
}
