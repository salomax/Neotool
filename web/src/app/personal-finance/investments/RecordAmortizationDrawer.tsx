"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider } from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import { z } from "zod";
import { RECORD_AMORTIZATION } from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import { DatePickerField, MoneyInput, SelectField } from "@/shared/components/ui/forms/form";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useTranslation } from "@/shared/i18n";
import { useToast } from "@/shared/providers";
import { formatDateOnly, toStartOfDayLocal } from "@/shared/utils/date/dateOnly";
import { personalFinanceTranslations } from "../i18n";
import { FixedIncomeAssetIdentity, HoldingIdentity } from "./components/HoldingIdentity";

type RecordAmortizationMutation = {
  recordAmortization: {
    amortizationTransaction: { id: string };
    bankingTransaction: { id: string } | null;
  };
};

type RecordAmortizationMutationVariables = {
  input: {
    holdingId: string;
    tradeDate: string;
    grossValue: number;
    netValue: number;
    incomeTax: number;
    iof?: number;
    destinationAccountId?: string;
  };
};

export type AmortizationDrawerHolding = {
  id: string;
  financialAccountId: string;
  grossValue: number | null;
  asset: {
    id: string;
    name: string;
    assetType: string | null;
    isTaxExempt?: boolean | null;
  };
  issuerName?: string | null;
};

interface RecordAmortizationDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: AmortizationDrawerHolding | null;
  financialAccounts?: Array<{ id: string; name: string; institutionName?: string | null }>;
  onSuccess?: () => void;
}

type AmortizationFormData = {
  tradeDate: Date | null;
  grossValue: number | undefined;
  netValue: number | undefined;
  incomeTax: number | undefined;
  iof: number | undefined;
  destinationAccountId: string | undefined;
};

const FORM_ID = "record-amortization-form";
const FIIS_ASSET_TYPES = new Set(["REAL_ESTATE_FUND", "AGRIBUSINESS_FUND"]);

export function RecordAmortizationDrawer({
  open,
  onClose,
  holding,
  financialAccounts = [],
  onSuccess,
}: RecordAmortizationDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [recordAmortization, { loading, error }] = useMutation<
    RecordAmortizationMutation,
    RecordAmortizationMutationVariables
  >(RECORD_AMORTIZATION);

  const schema = React.useMemo(
    () =>
      z
        .object({
          tradeDate: z.date(),
          grossValue: z.number().positive(t("investments.amortization.validation.grossPositive")),
          netValue: z.number().min(0, t("investments.amortization.validation.netMin")),
          incomeTax: z.number().min(0),
          iof: z.number().min(0),
          destinationAccountId: z.string().optional(),
        })
        .refine((d) => d.netValue <= d.grossValue, {
          message: t("investments.amortization.validation.netMax"),
          path: ["netValue"],
        }),
    [t],
  );

  const methods = useForm<AmortizationFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      tradeDate: null,
      grossValue: undefined,
      netValue: undefined,
      incomeTax: undefined,
      iof: 0,
      destinationAccountId: "",
    },
    mode: "onBlur",
    reValidateMode: "onChange",
  });

  React.useEffect(() => {
    if (!open || !holding) return;
    methods.reset({
      tradeDate: toStartOfDayLocal(new Date()),
      grossValue: undefined,
      netValue: undefined,
      incomeTax: 0,
      iof: 0,
      destinationAccountId: "",
    });
  }, [holding, methods, open]);

  const destinationAccountOptions = React.useMemo(
    () => [
      {
        value: "",
        label: t("investments.amortization.form.noDestinationAccount"),
      },
      ...financialAccounts.map((account) => ({
        value: account.id,
        label: account.institutionName ? `${account.name} · ${account.institutionName}` : account.name,
      })),
    ],
    [financialAccounts, t],
  );

  const submit = methods.handleSubmit(async (values) => {
    if (!holding || !values.tradeDate) return;
    try {
      const destinationAccountId = values.destinationAccountId?.trim() ? values.destinationAccountId : undefined;
      await recordAmortization({
        variables: {
          input: {
            holdingId: holding.id,
            tradeDate: formatDateOnly(values.tradeDate),
            grossValue: values.grossValue ?? 0,
            netValue: values.netValue ?? 0,
            incomeTax: values.incomeTax ?? 0,
            iof: values.iof ?? 0,
            destinationAccountId,
          },
        },
        refetchQueries: [
          {
            query: INVESTMENT_HOLDINGS,
            variables: { financialAccountId: holding.financialAccountId },
          },
          {
            query: INVESTMENT_CASH_BALANCE,
            variables: { financialAccountId: holding.financialAccountId },
          },
          {
            query: INVESTMENT_CASH_TRANSACTIONS,
            variables: { financialAccountId: holding.financialAccountId, first: 20, after: undefined },
          },
          {
            query: INVESTMENT_PORTFOLIO_SUMMARY,
            variables: { financialAccountId: holding.financialAccountId },
          },
        ],
      });
      toast.success(t("investments.amortization.success"));
      onSuccess?.();
      onClose();
    } catch (mutationError) {
      toast.error(mutationError instanceof Error ? mutationError.message : "Request failed");
    }
  });

  if (!holding) return null;

  const isFiiLike = FIIS_ASSET_TYPES.has(holding.asset.assetType ?? "");

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.amortization.drawerTitle")} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id={FORM_ID} onSubmit={submit}>
            <FormStack>
              {isFiiLike ? (
                <HoldingIdentity
                  assetName={holding.asset.name}
                  issuerName={holding.issuerName ?? undefined}
                  size={36}
                  sx={{ pb: 1 }}
                />
              ) : (
                <FixedIncomeAssetIdentity
                  assetType={holding.asset.assetType}
                  assetName={holding.asset.name}
                  issuerName={holding.issuerName ?? undefined}
                  size={36}
                  sx={{ pb: 1 }}
                />
              )}

              <ErrorAlert error={error} />

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <DatePickerField name="tradeDate" label={t("investments.amortization.form.tradeDate")} size="medium" />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="grossValue"
                    label={t("investments.amortization.form.grossValue")}
                    currency="BRL"
                    size="medium"
                  />
                </Box>
              </FormRowStack>

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="netValue"
                    label={t("investments.amortization.form.netValue")}
                    currency="BRL"
                    size="medium"
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="incomeTax"
                    label={t("investments.amortization.form.incomeTax")}
                    currency="BRL"
                    size="medium"
                  />
                </Box>
              </FormRowStack>

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput name="iof" label={t("investments.amortization.form.iof")} currency="BRL" size="medium" />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }} />
              </FormRowStack>

              <Divider />

              <SelectField
                name="destinationAccountId"
                label={t("investments.amortization.form.destinationAccount")}
                options={destinationAccountOptions}
                size="medium"
              />

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.amortization.submit")}
                submittingText={t("actions.saving")}
                onCancel={onClose}
                submitForm={FORM_ID}
                cancelDisabled={loading}
                submitDisabled={loading}
                submitting={loading}
              />
            </FormStack>
          </form>
        </FormProvider>
      </Drawer.Body>
    </Drawer>
  );
}

export default RecordAmortizationDrawer;
