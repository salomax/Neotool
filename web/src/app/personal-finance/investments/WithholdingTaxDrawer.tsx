"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider, Typography } from "@mui/material";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { CONFIRM_WITHHOLDING_TAX } from "@/lib/graphql/operations/banking/mutations";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import { DatePickerField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { useToast } from "@/shared/providers";
import { formatDateOnly, toStartOfDayLocal } from "@/shared/utils/date/dateOnly";
import { personalFinanceTranslations } from "../i18n";
import { HoldingIdentity } from "./components/HoldingIdentity";

type ConfirmWithholdingTaxMutation = {
  confirmWithholdingTax: {
    withholdingTaxTransaction: { id: string };
  };
};

type ConfirmWithholdingTaxMutationVariables = {
  input: {
    holdingId: string;
    tradeDate: string;
    irValue: number;
    quotasDeducted: number;
  };
};

export type WithholdingTaxDrawerHolding = {
  id: string;
  financialAccountId: string;
  quantity: number;
  averageCost: number;
  asset: {
    id: string;
    name: string;
    assetType: string | null;
  };
  currentNavPerQuota?: number | null;
};

interface WithholdingTaxDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: WithholdingTaxDrawerHolding | null;
  onSuccess?: () => void;
}

type WithholdingTaxFormData = {
  tradeDate: Date | null;
  irValue: number | undefined;
  quotasDeducted: number | undefined;
};

const FORM_ID = "confirm-withholding-tax-form";
const MAX_QUOTAS_FRACTION_DIGITS = 6;

export function WithholdingTaxDrawer({
  open,
  onClose,
  holding,
  onSuccess,
}: WithholdingTaxDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [confirmWithholdingTax, { loading, error }] = useMutation<
    ConfirmWithholdingTaxMutation,
    ConfirmWithholdingTaxMutationVariables
  >(CONFIRM_WITHHOLDING_TAX);

  const maxQuotas = holding?.quantity ?? Number.POSITIVE_INFINITY;
  const locale = i18n.language || "pt-BR";
  const schema = React.useMemo(
    () =>
      z.object({
        tradeDate: z.date(),
        irValue: z.number().positive(t("investments.withholdingTax.validation.irPositive")),
        quotasDeducted: z
          .number()
          .positive(t("investments.withholdingTax.validation.quotasPositive"))
          .max(maxQuotas, t("investments.withholdingTax.validation.quotasMax")),
      }),
    [maxQuotas, t],
  );

  const methods = useForm<WithholdingTaxFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      tradeDate: null,
      irValue: undefined,
      quotasDeducted: undefined,
    },
    mode: "onBlur",
    reValidateMode: "onChange",
  });

  React.useEffect(() => {
    if (!open || !holding) return;
    methods.reset({
      tradeDate: toStartOfDayLocal(new Date()),
      irValue: undefined,
      quotasDeducted: undefined,
    });
  }, [holding, methods, open]);

  const watchedIrValue = useWatch({ control: methods.control, name: "irValue" });
  const watchedQuotasDeducted = useWatch({ control: methods.control, name: "quotasDeducted" });
  const estimatedQuotasFromNav =
    !holding?.currentNavPerQuota || holding.currentNavPerQuota <= 0 || !watchedIrValue || watchedIrValue <= 0
      ? null
      : watchedIrValue / holding.currentNavPerQuota;

  React.useEffect(() => {
    if (!estimatedQuotasFromNav || !Number.isFinite(estimatedQuotasFromNav)) return;
    const roundedEstimated = Number(estimatedQuotasFromNav.toFixed(MAX_QUOTAS_FRACTION_DIGITS));
    if (roundedEstimated <= 0) return;
    if (watchedQuotasDeducted != null && Math.abs(watchedQuotasDeducted - roundedEstimated) < 1e-9) return;
    methods.setValue("quotasDeducted", roundedEstimated, { shouldDirty: true, shouldValidate: true });
  }, [estimatedQuotasFromNav, methods, watchedQuotasDeducted]);

  const submit = methods.handleSubmit(async (values) => {
    if (!holding || !values.tradeDate || values.irValue == null || values.quotasDeducted == null) return;
    try {
      await confirmWithholdingTax({
        variables: {
          input: {
            holdingId: holding.id,
            tradeDate: formatDateOnly(values.tradeDate),
            irValue: values.irValue,
            quotasDeducted: values.quotasDeducted,
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
      toast.success(t("investments.withholdingTax.success"));
      onSuccess?.();
      onClose();
    } catch (mutationError) {
      toast.error(mutationError instanceof Error ? mutationError.message : "Request failed");
    }
  });

  if (!holding) return null;

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.withholdingTax.drawerTitle")} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id={FORM_ID} onSubmit={submit}>
            <FormStack>
              <HoldingIdentity assetName={holding.asset.name} size={36} sx={{ pb: 1 }} />

              <ErrorAlert error={error} />

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <DatePickerField
                    name="tradeDate"
                    label={t("investments.withholdingTax.form.tradeDate")}
                    size="medium"
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="irValue"
                    label={t("investments.withholdingTax.form.irValue")}
                    currency="BRL"
                    size="medium"
                  />
                  {estimatedQuotasFromNav != null ? (
                    <Typography variant="caption" color="text.secondary">
                      {t("investments.withholdingTax.form.estimatedQuotasHint", {
                        value: new Intl.NumberFormat(locale, {
                          minimumFractionDigits: 3,
                          maximumFractionDigits: 3,
                        }).format(estimatedQuotasFromNav),
                      })}
                    </Typography>
                  ) : null}
                </Box>
              </FormRowStack>

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <NumberField
                    name="quotasDeducted"
                    label={t("investments.withholdingTax.form.quotasDeducted")}
                    allowNegative={false}
                    fractionDigits={MAX_QUOTAS_FRACTION_DIGITS}
                    locale={locale}
                    size="medium"
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }} />
              </FormRowStack>

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.withholdingTax.submit")}
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

export default WithholdingTaxDrawer;
