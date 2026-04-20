"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider } from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import { z } from "zod";
import { RECORD_STOCK_LENDING } from "@/lib/graphql/operations/banking/mutations";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import { DatePickerField, MoneyInput, SelectField } from "@/shared/components/ui/forms/form";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useTranslation } from "@/shared/i18n";
import { useToast } from "@/shared/providers";
import { formatDateOnly, toStartOfDayLocal } from "@/shared/utils/date/dateOnly";
import { personalFinanceTranslations } from "../i18n";
import { HoldingIdentity } from "./components/HoldingIdentity";

type RecordStockLendingMutation = {
  recordStockLending: {
    lendingTransaction: { id: string };
    bankingTransaction: { id: string } | null;
  };
};

type RecordStockLendingMutationVariables = {
  input: {
    holdingId: string;
    tradeDate: string;
    grossValue: number;
    netValue: number;
    incomeTax: number;
    destinationAccountId?: string;
  };
};

export type StockLendingDrawerHolding = {
  id: string;
  financialAccountId: string;
  asset: {
    id: string;
    name: string;
    assetType: string | null;
  };
  issuerName?: string | null;
};

interface RecordStockLendingDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: StockLendingDrawerHolding | null;
  financialAccounts?: Array<{ id: string; name: string; institutionName?: string | null }>;
  onSuccess?: () => void;
}

type StockLendingFormData = {
  tradeDate: Date | null;
  grossValue: number | undefined;
  netValue: number | undefined;
  incomeTax: number | undefined;
  destinationAccountId: string | undefined;
};

const FORM_ID = "record-stock-lending-form";

export function RecordStockLendingDrawer({
  open,
  onClose,
  holding,
  financialAccounts = [],
  onSuccess,
}: RecordStockLendingDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [recordStockLending, { loading, error }] = useMutation<
    RecordStockLendingMutation,
    RecordStockLendingMutationVariables
  >(RECORD_STOCK_LENDING);

  const schema = React.useMemo(
    () =>
      z
        .object({
          tradeDate: z.date(),
          grossValue: z.number().positive(t("investments.stockLending.validation.grossPositive")),
          netValue: z.number().min(0, t("investments.stockLending.validation.netMin")),
          incomeTax: z.number().min(0),
          destinationAccountId: z.string().optional(),
        })
        .refine((d) => d.netValue <= d.grossValue, {
          message: t("investments.stockLending.validation.netMax"),
          path: ["netValue"],
        }),
    [t],
  );

  const methods = useForm<StockLendingFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      tradeDate: null,
      grossValue: undefined,
      netValue: undefined,
      incomeTax: undefined,
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
      incomeTax: undefined,
      destinationAccountId: "",
    });
  }, [holding, methods, open]);

  const destinationAccountOptions = React.useMemo(
    () => [
      {
        value: "",
        label: t("investments.stockLending.form.noDestinationAccount"),
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
      await recordStockLending({
        variables: {
          input: {
            holdingId: holding.id,
            tradeDate: formatDateOnly(values.tradeDate),
            grossValue: values.grossValue ?? 0,
            netValue: values.netValue ?? 0,
            incomeTax: values.incomeTax ?? 0,
            destinationAccountId,
          },
        },
        refetchQueries: [
          {
            query: INVESTMENT_HOLDINGS,
            variables: { financialAccountId: holding.financialAccountId },
          },
          {
            query: INVESTMENT_PORTFOLIO_SUMMARY,
            variables: { financialAccountId: holding.financialAccountId },
          },
        ],
      });
      toast.success(t("investments.stockLending.success"));
      onSuccess?.();
      onClose();
    } catch (mutationError) {
      toast.error(mutationError instanceof Error ? mutationError.message : "Request failed");
    }
  });

  if (!holding) return null;

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.stockLending.drawerTitle")} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id={FORM_ID} onSubmit={submit}>
            <FormStack>
              <HoldingIdentity
                assetName={holding.asset.name}
                issuerName={holding.issuerName ?? undefined}
                size={36}
                sx={{ pb: 1 }}
              />

              <ErrorAlert error={error} />

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <DatePickerField name="tradeDate" label={t("investments.stockLending.form.tradeDate")} size="medium" />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="grossValue"
                    label={t("investments.stockLending.form.grossValue")}
                    currency="BRL"
                    size="medium"
                  />
                </Box>
              </FormRowStack>

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="netValue"
                    label={t("investments.stockLending.form.netValue")}
                    currency="BRL"
                    size="medium"
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="incomeTax"
                    label={t("investments.stockLending.form.incomeTax")}
                    currency="BRL"
                    size="medium"
                  />
                </Box>
              </FormRowStack>

              <Divider />

              <SelectField
                name="destinationAccountId"
                label={t("investments.stockLending.form.destinationAccount")}
                options={destinationAccountOptions}
                size="medium"
              />

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.stockLending.submit")}
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

export default RecordStockLendingDrawer;
