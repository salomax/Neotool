"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider, Typography } from "@mui/material";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { TRANSFER_CUSTODY } from "@/lib/graphql/operations/banking/mutations";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import { DatePickerField, NumberField, SelectField, ToggleField } from "@/shared/components/ui/forms/form";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { useToast } from "@/shared/providers";
import { formatDateOnly, toStartOfDayLocal } from "@/shared/utils/date/dateOnly";
import { personalFinanceTranslations } from "../i18n";
import { HoldingIdentity } from "./components/HoldingIdentity";

type TransferCustodyMutation = {
  transferCustody: {
    outflowTransaction: { id: string };
    inflowTransaction: { id: string };
  };
};

type TransferCustodyMutationVariables = {
  input: {
    sourceHoldingId: string;
    destinationFinancialAccountId: string;
    quantity?: number;
    tradeDate: string;
  };
};

export type TransferCustodyDrawerHolding = {
  id: string;
  financialAccountId: string;
  quantity: number;
  averageCost: number;
  asset: {
    id: string;
    name: string;
    assetType: string | null;
  };
  issuerName?: string | null;
};

interface TransferCustodyDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: TransferCustodyDrawerHolding | null;
  investmentAccounts: Array<{
    id: string;
    name: string;
    institutionName?: string | null;
    subtype: string;
  }>;
  onSuccess?: () => void;
}

type TransferCustodyFormData = {
  tradeDate: Date | null;
  isFullTransfer: boolean;
  quantity: number | undefined;
  destinationFinancialAccountId: string;
};

const FORM_ID = "transfer-custody-form";
const MAX_QUANTITY_FRACTION_DIGITS = 6;

export function TransferCustodyDrawer({
  open,
  onClose,
  holding,
  investmentAccounts,
  onSuccess,
}: TransferCustodyDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const locale = i18n.language || "pt-BR";

  const [transferCustody, { loading, error }] = useMutation<TransferCustodyMutation, TransferCustodyMutationVariables>(
    TRANSFER_CUSTODY,
  );

  const schema = React.useMemo(() => {
    const maxQty = holding?.quantity ?? Number.POSITIVE_INFINITY;
    return z
      .object({
        tradeDate: z.date(),
        isFullTransfer: z.boolean(),
        quantity: z.number().positive(t("investments.transferCustody.validation.quantityPositive")).optional(),
        destinationFinancialAccountId: z
          .string()
          .min(1, t("investments.transferCustody.validation.destinationRequired")),
      })
      .refine((d) => d.isFullTransfer || (d.quantity != null && d.quantity <= maxQty), {
        message: t("investments.transferCustody.validation.quantityMax"),
        path: ["quantity"],
      });
  }, [holding?.quantity, t]);

  const methods = useForm<TransferCustodyFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      tradeDate: null,
      isFullTransfer: true,
      quantity: undefined,
      destinationFinancialAccountId: "",
    },
    mode: "onBlur",
    reValidateMode: "onChange",
  });

  React.useEffect(() => {
    if (!open || !holding) return;
    methods.reset({
      tradeDate: toStartOfDayLocal(new Date()),
      isFullTransfer: true,
      quantity: undefined,
      destinationFinancialAccountId: "",
    });
  }, [holding, methods, open]);

  const destinationAccountOptions = React.useMemo(
    () =>
      investmentAccounts
        .filter((account) => account.id !== holding?.financialAccountId)
        .map((account) => ({
          value: account.id,
          label: account.institutionName ? `${account.name} · ${account.institutionName}` : account.name,
        })),
    [holding?.financialAccountId, investmentAccounts],
  );
  const noDestinationAccountAvailable = destinationAccountOptions.length === 0;

  const isFullTransfer = useWatch({ control: methods.control, name: "isFullTransfer" });
  const formattedHoldingQuantity = React.useMemo(
    () =>
      new Intl.NumberFormat(locale, {
        minimumFractionDigits: 0,
        maximumFractionDigits: MAX_QUANTITY_FRACTION_DIGITS,
      }).format(holding?.quantity ?? 0),
    [holding?.quantity, locale],
  );

  const submit = methods.handleSubmit(async (values) => {
    if (!holding || !values.tradeDate) return;

    try {
      await transferCustody({
        variables: {
          input: {
            sourceHoldingId: holding.id,
            destinationFinancialAccountId: values.destinationFinancialAccountId,
            quantity: values.isFullTransfer ? undefined : values.quantity,
            tradeDate: formatDateOnly(values.tradeDate),
          },
        },
        refetchQueries: [
          {
            query: INVESTMENT_HOLDINGS,
            variables: { financialAccountId: holding.financialAccountId },
          },
          {
            query: INVESTMENT_HOLDINGS,
            variables: { financialAccountId: values.destinationFinancialAccountId },
          },
          {
            query: INVESTMENT_PORTFOLIO_SUMMARY,
            variables: { financialAccountId: holding.financialAccountId },
          },
        ],
      });
      toast.success(t("investments.transferCustody.success"));
      onSuccess?.();
      onClose();
    } catch {
      // ErrorAlert renders Apollo mutation errors.
    }
  });

  if (!holding) return null;

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.transferCustody.drawerTitle")} />
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
                  <DatePickerField
                    name="tradeDate"
                    label={t("investments.transferCustody.form.tradeDate")}
                    size="medium"
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <SelectField
                    name="destinationFinancialAccountId"
                    label={t("investments.transferCustody.form.destinationAccount")}
                    options={destinationAccountOptions}
                    helperText={
                      noDestinationAccountAvailable
                        ? t("investments.transferCustody.form.noDestinationAccountAvailable")
                        : undefined
                    }
                    size="medium"
                  />
                </Box>
              </FormRowStack>

              <ToggleField
                name="isFullTransfer"
                label={t("investments.transferCustody.form.fullTransferToggle")}
                size="medium"
              />

              {isFullTransfer ? (
                <Typography variant="caption" color="text.secondary">
                  {t("investments.transferCustody.form.quantityHint", {
                    quantity: formattedHoldingQuantity,
                  })}
                </Typography>
              ) : (
                <NumberField
                  name="quantity"
                  label={t("investments.transferCustody.form.quantity")}
                  allowNegative={false}
                  fractionDigits={MAX_QUANTITY_FRACTION_DIGITS}
                  locale={locale}
                  size="medium"
                />
              )}

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.transferCustody.submit")}
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

export default TransferCustodyDrawer;
