"use client";

import React, { useEffect } from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider, Paper, ToggleButton, ToggleButtonGroup, Typography, alpha, useTheme } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { RECORD_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import {
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { FormStack, FormRowStack } from "@/shared/components/ui/forms/components";
import { DatePickerField, FormTextField as TextField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { useToast } from "@/shared/providers";
import { personalFinanceTranslations } from "../../../i18n";
import { FixedIncomeHoldingDetailDocument } from "./api/queries.generated";

interface RedemptionHolding {
  id: string;
  quantity: number;
  grossValue: number | null;
  netValue: number | null;
  isTaxExempt: boolean;
  financialAccountId: string;
  assetId: string;
}

interface RecordFixedIncomeRedemptionDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: RedemptionHolding | null;
  onRecorded?: () => void;
}

type RedemptionType = "SALE" | "MATURITY";

type RedemptionFormData = {
  redemptionType: RedemptionType;
  isPartial: boolean;
  quantity: number;
  grossValue: number;
  incomeTax?: number;
  iof?: number;
  tradeDate?: Date;
};

export function RecordFixedIncomeRedemptionDrawer({
  open,
  onClose,
  holding,
  onRecorded,
}: RecordFixedIncomeRedemptionDrawerProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);

  const currencyFormatter = React.useMemo(
    () => new Intl.NumberFormat(locale, { style: "currency", currency: "BRL" }),
    [locale],
  );

  const maxQuantity = holding?.quantity ?? 0;

  const schema = React.useMemo(
    () =>
      z
        .object({
          redemptionType: z.enum(["SALE", "MATURITY"]),
          isPartial: z.boolean(),
          quantity: z
            .number({ required_error: t("investments.redemption.validation.quantityRequired") })
            .positive(t("investments.redemption.validation.quantityPositive")),
          grossValue: z
            .number({ required_error: t("investments.redemption.validation.grossValueRequired") })
            .positive(t("investments.redemption.validation.grossValuePositive")),
          incomeTax: z.number().nonnegative().optional(),
          iof: z.number().nonnegative().optional(),
          tradeDate: z.preprocess(
            (v) => (v === "" || v === null || v === undefined ? undefined : v),
            z.date({
              required_error: t("investments.redemption.validation.tradeDateRequired"),
              invalid_type_error: t("investments.redemption.validation.tradeDateRequired"),
            }),
          ),
        })
        .superRefine((data, ctx) => {
          if (data.isPartial && data.quantity > maxQuantity) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.redemption.validation.quantityExceedsHolding", {
                max: maxQuantity,
              }),
              path: ["quantity"],
            });
          }
        }),
    [maxQuantity, t],
  );

  const methods = useForm<RedemptionFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      redemptionType: "SALE",
      isPartial: false,
      quantity: maxQuantity,
      grossValue: holding?.grossValue ?? undefined,
      incomeTax: undefined,
      iof: undefined,
      tradeDate: undefined,
    },
  });

  const { reset, setValue, watch } = methods;
  const watchedIsPartial = useWatch({ control: methods.control, name: "isPartial" });
  const watchedGrossValue = useWatch({ control: methods.control, name: "grossValue" });
  const watchedIncomeTax = useWatch({ control: methods.control, name: "incomeTax" });
  const watchedIof = useWatch({ control: methods.control, name: "iof" });
  const watchedRedemptionType = watch("redemptionType");

  const computedNetValue = React.useMemo(() => {
    if (!watchedGrossValue) return null;
    return watchedGrossValue - (watchedIncomeTax ?? 0) - (watchedIof ?? 0);
  }, [watchedGrossValue, watchedIncomeTax, watchedIof]);

  useEffect(() => {
    if (!open) {
      reset();
      return;
    }
    reset({
      redemptionType: "SALE",
      isPartial: false,
      quantity: holding?.quantity ?? 0,
      grossValue: holding?.grossValue ?? undefined,
      incomeTax: undefined,
      iof: undefined,
      tradeDate: undefined,
    });
  }, [open, holding, reset]);

  // When switching between partial/full, sync quantity
  useEffect(() => {
    if (!watchedIsPartial) {
      setValue("quantity", maxQuantity);
    }
  }, [watchedIsPartial, maxQuantity, setValue]);

  const [recordTransaction, { loading: recordingTx, error: recordTxError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION, {
    refetchQueries: [
      { query: INVESTMENT_HOLDINGS, variables: { financialAccountId: holding?.financialAccountId ?? null } },
      { query: INVESTMENT_HOLDINGS },
      { query: INVESTMENT_PORTFOLIO_SUMMARY, variables: { financialAccountId: holding?.financialAccountId ?? null } },
      { query: INVESTMENT_PORTFOLIO_SUMMARY },
      { query: FixedIncomeHoldingDetailDocument, variables: { holdingId: holding?.id ?? "" } },
    ],
    onCompleted: () => {
      success(t("investments.redemption.success"));
      onRecorded?.();
      onClose();
    },
  });

  const onSubmit = async (data: RedemptionFormData) => {
    if (!holding?.financialAccountId || !holding?.assetId) return;
    if (!data.tradeDate) return;

    const tradeDate = dayjs(data.tradeDate).format("YYYY-MM-DD");
    const quantity = data.isPartial ? data.quantity : maxQuantity;
    const unitPrice = quantity > 0 ? data.grossValue / quantity : data.grossValue;
    const netValue = data.grossValue - (data.incomeTax ?? 0) - (data.iof ?? 0);

    const txType =
      data.redemptionType === "MATURITY"
        ? InvestmentTransactionType.Maturity
        : InvestmentTransactionType.Sale;

    try {
      await recordTransaction({
        variables: {
          input: {
            financialAccountId: holding.financialAccountId,
            assetId: holding.assetId,
            direction: InvestmentTransactionDirection.Outflow,
            type: txType,
            quantity,
            unitPrice,
            grossValue: data.grossValue,
            netValue,
            incomeTax: data.incomeTax ?? null,
            iof: data.iof ?? null,
            tradeDate,
            settlementDate: tradeDate,
          },
        },
      });
    } catch {
      return;
    }
  };

  const isSubmitting = recordingTx;
  const isTaxExempt = holding?.isTaxExempt ?? false;

  return (
    <Drawer open={open} onClose={onClose} anchor="right">
      <Drawer.Header title={t("investments.redemption.title")} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id="record-redemption-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <FormStack>
              <ErrorAlert error={recordTxError} />

              {/* Tipo de movimentação */}
              <Box>
                <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 1 }}>
                  {t("investments.redemption.sections.type")}
                </Typography>
                <ToggleButtonGroup
                  exclusive
                  fullWidth
                  size="small"
                  value={watchedRedemptionType}
                  onChange={(_, val) => { if (val) setValue("redemptionType", val); }}
                >
                  <ToggleButton value="SALE">
                    {t("investments.redemption.types.sale")}
                  </ToggleButton>
                  <ToggleButton value="MATURITY">
                    {t("investments.redemption.types.maturity")}
                  </ToggleButton>
                </ToggleButtonGroup>
              </Box>

              <Divider />

              {/* Parcial / Total */}
              <Box>
                <Typography variant="overline" color="text.secondary" sx={{ display: "block", mb: 1 }}>
                  {t("investments.redemption.sections.scope")}
                </Typography>
                <ToggleButtonGroup
                  exclusive
                  fullWidth
                  size="small"
                  value={watchedIsPartial ? "partial" : "full"}
                  onChange={(_, val) => { if (val) setValue("isPartial", val === "partial"); }}
                >
                  <ToggleButton value="full">
                    {t("investments.redemption.scope.full")}
                  </ToggleButton>
                  <ToggleButton value="partial">
                    {t("investments.redemption.scope.partial")}
                  </ToggleButton>
                </ToggleButtonGroup>
              </Box>

              {watchedIsPartial && (
                <NumberField
                  name="quantity"
                  label={t("investments.redemption.fields.quantity")}
                  fullWidth
                  locale={locale}
                  min={0}
                  max={maxQuantity}
                  allowNegative={false}
                  helperText={t("investments.redemption.fields.quantityHelper", {
                    max: maxQuantity,
                  })}
                />
              )}

              <Divider />

              {/* Valores */}
              <Typography variant="overline" color="text.secondary" sx={{ mb: 0 }}>
                {t("investments.redemption.sections.values")}
              </Typography>

              <MoneyInput
                name="grossValue"
                label={t("investments.redemption.fields.grossValue")}
                currency="BRL"
              />

              {!isTaxExempt && (
                <FormRowStack alignItems="flex-start">
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <MoneyInput
                      name="incomeTax"
                      label={t("investments.redemption.fields.incomeTax")}
                      currency="BRL"
                    />
                  </Box>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <MoneyInput
                      name="iof"
                      label={t("investments.redemption.fields.iof")}
                      currency="BRL"
                    />
                  </Box>
                </FormRowStack>
              )}

              {computedNetValue != null && (
                <Paper
                  variant="outlined"
                  sx={{
                    px: 1.5,
                    py: 1,
                    borderRadius: 1,
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                    borderColor: alpha(theme.palette.success.main, 0.35),
                    bgcolor: alpha(theme.palette.success.main, 0.06),
                  }}
                >
                  <InfoOutlinedIcon fontSize="small" sx={{ color: "success.main" }} />
                  <Typography variant="body2" color="text.secondary">
                    {t("investments.redemption.fields.netValueInfo", {
                      value: currencyFormatter.format(computedNetValue),
                    })}
                  </Typography>
                </Paper>
              )}

              {isTaxExempt && (
                <Paper
                  variant="outlined"
                  sx={{
                    px: 1.5,
                    py: 1,
                    borderRadius: 1,
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                    borderColor: alpha(theme.palette.info.main, 0.35),
                    bgcolor: alpha(theme.palette.info.main, 0.06),
                  }}
                >
                  <InfoOutlinedIcon fontSize="small" sx={{ color: "info.main" }} />
                  <Typography variant="body2" color="text.secondary">
                    {t("investments.redemption.taxExemptInfo")}
                  </Typography>
                </Paper>
              )}

              <Divider />

              <DatePickerField
                name="tradeDate"
                label={t("investments.redemption.fields.tradeDate")}
              />

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.redemption.actions.submit")}
                submittingText={t("investments.redemption.actions.submitting")}
                onCancel={onClose}
                submitForm="record-redemption-form"
                cancelDisabled={isSubmitting}
                submitDisabled={isSubmitting || !holding}
                submitting={isSubmitting}
              />
            </FormStack>
          </form>
        </FormProvider>
      </Drawer.Body>
    </Drawer>
  );
}
