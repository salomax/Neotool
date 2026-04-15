"use client";

import React, { useEffect } from "react";
import { useApolloClient, useMutation, useQuery } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Avatar,
  Box,
  Button,
  CircularProgress,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION, UPDATE_INVESTMENT_ASSET, UPDATE_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import { INVESTMENT_ASSET_PRICE } from "@/lib/graphql/operations/financial-data/queries";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  AssetType,
  InvestmentTransaction,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { DatePickerField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { FINANCE_MOVING_WINDOW_YEARS, getFinanceMinAllowedDate } from "../../finance-transactions/constants/dateWindow";
import { personalFinanceTranslations } from "../../i18n";
import { EquityAutocomplete, EquityAutocompleteValue } from "./EquityAutocomplete";
import { mapB3AssetType } from "@/lib/utils/mapB3AssetType";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";

interface RecordEquityHoldingDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: IssuerSelectionAccount[];
  onRecorded?: () => void;
  editHolding?: {
    id: string;
    asset: { id: string; ticker: string | null; name: string; assetType: AssetType };
  } | null;
  editTransaction?: InvestmentTransaction | null;
  prefillHolding?: {
    id: string;
    asset: { id: string; ticker: string | null; name: string; assetType: AssetType };
  } | null;
  initialDirection?: InvestmentTransactionDirection;
}

type RecordEquityFormData = {
  asset: EquityAutocompleteValue | null;
  quantity?: number | string;
  tradeDate?: Date;
  unitPrice?: number;
  grossValue?: number;
  totalFees?: number;
  brokerageFee?: number;
  settlementFee?: number;
  otherFees?: number;
};

type UpdateInvestmentAssetMutationData = { updateInvestmentAsset: { id: string } };
type UpdateInvestmentTransactionMutationData = { updateInvestmentTransaction: { id: string } };

export function RecordEquityHoldingDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
  editHolding,
  editTransaction,
  prefillHolding,
  initialDirection,
}: RecordEquityHoldingDrawerProps) {
  const theme = useTheme();
  const apolloClient = useApolloClient();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(
    () => getLocaleSeparators(locale),
    [locale],
  );
  const minAllowedDate = React.useMemo(() => getFinanceMinAllowedDate(), []);
  const [resolvedFinancialAccountId, setResolvedFinancialAccountId] = React.useState<string | null>(null);
  const [valueFlow, setValueFlow] = React.useState<"BY_AMOUNT" | "BY_QUANTITY">("BY_QUANTITY");
  const [feesMode, setFeesMode] = React.useState<"SUMMARY" | "DETAILED">("SUMMARY");
  const isEdit = Boolean(editHolding && editTransaction);
  const lastEditSeedRef = React.useRef<{ holdingId: string | null; transactionId: string | null } | null>(null);
  const mapHoldingAssetTypeToB3 = React.useCallback((assetType: AssetType) => {
    switch (assetType) {
      case AssetType.RealEstateFund:
        return "FII";
      case AssetType.Option:
        return "OPTION_CALL";
      case AssetType.Stock:
      default:
        return "EQUITY";
    }
  }, []);

  const createSchema = React.useMemo(
    () =>
      z.object({
        asset: z
          .object({
            id: z.string(),
            ticker: z.string(),
            name: z.string(),
            assetType: z.string(),
            isActive: z.boolean(),
          })
          .nullable()
          .refine((v) => v != null, { message: t("investments.recordEquity.validation.assetRequired") }),
        quantity: z.preprocess(
          (value) => {
            if (value === "" || value === null || value === undefined) return undefined;
            if (typeof value === "number") return Number.isNaN(value) ? undefined : value;
            const raw = String(value);
            let normalized: string;
            if (localeGroup) {
              const decimalIndex = raw.lastIndexOf(localeDecimal);
              if (decimalIndex >= 0 && raw.indexOf(localeGroup, decimalIndex + 1) !== -1) {
                return undefined;
              }
              normalized = raw.replace(new RegExp(`\\${localeGroup}`, "g"), "").replace(localeDecimal, ".");
            } else {
              normalized = raw.replace(localeDecimal, ".");
            }
            const parsed = Number(normalized);
            return Number.isNaN(parsed) ? undefined : parsed;
          },
          z
            .number({ required_error: t("investments.recordEquity.validation.quantityRequired") })
            .positive(t("investments.recordEquity.validation.quantityPositive")),
        ).optional(),
        unitPrice: z.preprocess(
          (value) => {
            if (value === "" || value === null || value === undefined) return undefined;
            if (typeof value === "number") return Number.isNaN(value) ? undefined : value;
            const raw = String(value);
            let normalized: string;
            if (localeGroup) {
              const decimalIndex = raw.lastIndexOf(localeDecimal);
              if (decimalIndex >= 0 && raw.indexOf(localeGroup, decimalIndex + 1) !== -1) {
                return undefined;
              }
              normalized = raw.replace(new RegExp(`\\${localeGroup}`, "g"), "").replace(localeDecimal, ".");
            } else {
              normalized = raw.replace(localeDecimal, ".");
            }
            const parsed = Number(normalized);
            return Number.isNaN(parsed) ? undefined : parsed;
          },
          z
            .number({ required_error: t("investments.recordEquity.validation.unitPriceRequired") })
            .positive(t("investments.recordEquity.validation.unitPricePositive")),
        ).optional(),
        tradeDate: z.preprocess(
          (value) => (value === "" || value === null || value === undefined ? undefined : value),
          z.date({
            required_error: t("investments.recordEquity.validation.tradeDateRequired"),
            invalid_type_error: t("investments.recordEquity.validation.tradeDateRequired"),
          }),
        ),
        grossValue: z
          .number({ invalid_type_error: t("investments.recordEquity.validation.grossValueRequired") })
          .positive(t("investments.recordEquity.validation.grossValuePositive"))
          .optional(),
        totalFees: z.number().min(0).optional(),
        brokerageFee: z.number().min(0).optional(),
        settlementFee: z.number().min(0).optional(),
        otherFees: z.number().min(0).optional(),
      })
      .superRefine((data, ctx) => {
        if (dayjs(data.tradeDate).isBefore(minAllowedDate, "day")) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t("financeTransactions.validation.dateTooOld", { years: FINANCE_MOVING_WINDOW_YEARS }),
            path: ["tradeDate"],
          });
        }
      }),
    [localeDecimal, localeGroup, minAllowedDate, t],
  );

  const methods = useForm<RecordEquityFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      asset: null,
      quantity: undefined,
      tradeDate: undefined,
      unitPrice: undefined,
      grossValue: undefined,
      totalFees: undefined,
      brokerageFee: undefined,
      settlementFee: undefined,
      otherFees: undefined,
    },
  });

  const { reset } = methods;
  const { dirtyFields } = methods.formState;
  const selectedAsset = methods.watch("asset");
  const selectedTradeDate = methods.watch("tradeDate");
  const unitPriceValue = methods.watch("unitPrice");
  const grossValueValue = methods.watch("grossValue");
  const quantityValue = methods.watch("quantity");
  const totalFeesValue = methods.watch("totalFees");
  const brokerageFeeValue = methods.watch("brokerageFee");
  const settlementFeeValue = methods.watch("settlementFee");
  const otherFeesValue = methods.watch("otherFees");

  const tradeDateIso = React.useMemo(
    () => (selectedTradeDate ? dayjs(selectedTradeDate).format("YYYY-MM-DD") : null),
    [selectedTradeDate],
  );

  const assetPriceQuery = useQuery<
    {
      investmentAssetByTicker: {
        id: string;
        ticker: string;
        priceHistory: Array<{ date: string; closePrice: number }>;
      } | null;
    },
    { ticker: string; to: string }
  >(INVESTMENT_ASSET_PRICE, {
    variables: { ticker: selectedAsset?.ticker ?? "", to: tradeDateIso ?? "" },
    skip: !selectedAsset?.ticker || !tradeDateIso,
    fetchPolicy: "cache-and-network",
    errorPolicy: "all",
  });

  const suggestedUnitPrice = assetPriceQuery.data?.investmentAssetByTicker?.priceHistory?.[0]?.closePrice ?? null;

  useEffect(() => {
    if (!open) return;
    if (!suggestedUnitPrice) return;
    if (dirtyFields.unitPrice) return;
    if (typeof unitPriceValue === "number" && unitPriceValue > 0) return;
    methods.setValue("unitPrice", suggestedUnitPrice, { shouldDirty: false, shouldTouch: true, shouldValidate: true });
  }, [dirtyFields.unitPrice, methods, open, suggestedUnitPrice, unitPriceValue]);

  useEffect(() => {
    if (!open) {
      lastEditSeedRef.current = null;
      reset();
      setValueFlow("BY_QUANTITY");
      setFeesMode("SUMMARY");
    }
  }, [open, reset]);

  useEffect(() => {
    if (!open) return;
    if (isEdit) return;
    if (!prefillHolding) return;

    methods.setValue(
      "asset",
      {
        id: prefillHolding.asset.id,
        ticker: prefillHolding.asset.ticker ?? "",
        name: prefillHolding.asset.name,
        assetType: mapHoldingAssetTypeToB3(prefillHolding.asset.assetType),
        isActive: true,
      },
      { shouldDirty: false, shouldValidate: true },
    );
  }, [isEdit, mapHoldingAssetTypeToB3, methods, open, prefillHolding]);

  // resolvedFinancialAccountId is managed via InvestmentRecordDrawer onResolvedChange

  const [createAsset, { loading: creatingAsset, error: createAssetError }] = useMutation<
    CreateInvestmentAssetMutation,
    CreateInvestmentAssetMutationVariables
  >(CREATE_INVESTMENT_ASSET);

  const [recordTransaction, { loading: recordingTx, error: recordTxError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION);
  const [updateAsset, { loading: updatingAsset, error: updateAssetError }] = useMutation<UpdateInvestmentAssetMutationData>(
    UPDATE_INVESTMENT_ASSET,
  );
  const [updateTransaction, { loading: updatingTx, error: updateTxError }] = useMutation<UpdateInvestmentTransactionMutationData>(
    UPDATE_INVESTMENT_TRANSACTION,
  );

  const completeRecording = React.useCallback(() => {
    success(t("investments.recordEquity.success"));
    onRecorded?.();
    onClose();

    void apolloClient
      .refetchQueries({
        include: [INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY],
      })
      .catch(() => undefined);
  }, [apolloClient, onClose, onRecorded, success, t]);

  const isSubmitting = creatingAsset || recordingTx || updatingAsset || updatingTx;
  const mutationError = createAssetError || recordTxError || updateAssetError || updateTxError;

  const currencyFormatter = React.useMemo(
    () => new Intl.NumberFormat(locale, { style: "currency", currency: "BRL" }),
    [locale],
  );

  const computedQuantityByAmount = React.useMemo(() => {
    const unitPrice = typeof unitPriceValue === "number" ? unitPriceValue : null;
    const grossValue = typeof grossValueValue === "number" ? grossValueValue : null;
    if (!unitPrice || !grossValue || unitPrice <= 0 || grossValue <= 0) return null;
    return grossValue / unitPrice;
  }, [grossValueValue, unitPriceValue]);

  const computedTotalByQuantity = React.useMemo(() => {
    const unitPrice = typeof unitPriceValue === "number" ? unitPriceValue : null;
    const quantity = typeof quantityValue === "number" ? quantityValue : null;
    if (!unitPrice || quantity == null || !Number.isFinite(quantity) || unitPrice <= 0 || quantity <= 0) return null;
    return unitPrice * quantity;
  }, [quantityValue, unitPriceValue]);

  const computedGrossValue = React.useMemo(() => {
    if (valueFlow === "BY_AMOUNT") {
      return typeof grossValueValue === "number" && grossValueValue > 0 ? grossValueValue : null;
    }
    return computedTotalByQuantity;
  }, [computedTotalByQuantity, grossValueValue, valueFlow]);

  const computedDetailedFees = React.useMemo(() => {
    const brokerageFee = typeof brokerageFeeValue === "number" ? brokerageFeeValue : 0;
    const settlementFee = typeof settlementFeeValue === "number" ? settlementFeeValue : 0;
    const otherFees = typeof otherFeesValue === "number" ? otherFeesValue : 0;
    return brokerageFee + settlementFee + otherFees;
  }, [brokerageFeeValue, otherFeesValue, settlementFeeValue]);

  const computedTotalFees = React.useMemo(() => {
    if (feesMode === "DETAILED") return computedDetailedFees;
    return typeof totalFeesValue === "number" ? totalFeesValue : 0;
  }, [computedDetailedFees, feesMode, totalFeesValue]);

  const computedTransactionTotal = React.useMemo(() => {
    if (computedGrossValue == null) return null;
    return computedGrossValue + computedTotalFees;
  }, [computedGrossValue, computedTotalFees]);

  const toDateValue = React.useCallback((value: unknown) => {
    if (!value) return undefined;
    if (value instanceof Date) return value;
    const d = new Date(String(value));
    return Number.isNaN(d.getTime()) ? undefined : d;
  }, []);

  const onSubmit = async (data: RecordEquityFormData) => {
    if (!resolvedFinancialAccountId) return;
    const asset = data.asset;
    if (!asset) return;
    if (!data.tradeDate) return;

    if (valueFlow === "BY_AMOUNT") {
      if (!data.grossValue) {
        methods.setError("grossValue", { type: "required", message: t("investments.recordEquity.validation.grossValueRequired") });
        return;
      }
      if (!data.unitPrice) {
        methods.setError("unitPrice", { type: "required", message: t("investments.recordEquity.validation.unitPriceRequired") });
        return;
      }
    } else {
      if (!data.quantity) {
        methods.setError("quantity", { type: "required", message: t("investments.recordEquity.validation.quantityRequired") });
        return;
      }
      if (!data.unitPrice) {
        methods.setError("unitPrice", { type: "required", message: t("investments.recordEquity.validation.unitPriceRequired") });
        return;
      }
    }

    const tradeDate = dayjs(data.tradeDate).format("YYYY-MM-DD");
    const unitPrice = data.unitPrice ?? suggestedUnitPrice;
    if (!unitPrice) return;

    const quantity = valueFlow === "BY_AMOUNT" ? (data.grossValue ? data.grossValue / unitPrice : NaN) : Number(data.quantity);
    const grossValue = valueFlow === "BY_AMOUNT" ? (data.grossValue ?? 0) : quantity * unitPrice;

    if (!Number.isFinite(quantity) || quantity <= 0) {
      methods.setError("quantity", { type: "required", message: t("investments.recordEquity.validation.quantityPositive") });
      return;
    }
    if (!Number.isFinite(grossValue) || grossValue <= 0) {
      methods.setError("grossValue", { type: "required", message: t("investments.recordEquity.validation.grossValuePositive") });
      return;
    }

    const resolvedAssetId =
      isEdit
        ? editHolding!.asset.id
        : prefillHolding
          ? prefillHolding.asset.id
        : (await (async () => {
            const createdAsset = await createAsset({
              variables: {
                input: {
                  name: asset.name,
                  assetType: mapB3AssetType(asset.assetType),
                  ticker: asset.ticker,
                  currencyCode: "BRL",
                },
              },
            });
            return createdAsset.data?.createInvestmentAsset.id ?? null;
          })());
    if (!resolvedAssetId) return;

    const txInput = {
      direction: isEdit ? editTransaction!.direction : (initialDirection ?? InvestmentTransactionDirection.Inflow),
      type: isEdit
        ? editTransaction!.type
        : (initialDirection ?? InvestmentTransactionDirection.Inflow) === InvestmentTransactionDirection.Outflow
          ? InvestmentTransactionType.Sale
          : InvestmentTransactionType.Purchase,
      quantity,
      unitPrice,
      grossValue,
      tradeDate,
      settlementDate: dayjs(data.tradeDate).add(2, "day").format("YYYY-MM-DD"),
    };

    try {
      if (isEdit) {
        const nextAssetType = mapB3AssetType(asset.assetType);
        const assetChanged =
          asset.name !== editHolding!.asset.name ||
          nextAssetType !== editHolding!.asset.assetType ||
          asset.ticker !== (editHolding!.asset.ticker ?? "");
        if (assetChanged) {
          await updateAsset({
            variables: {
              id: resolvedAssetId,
              input: {
                name: asset.name,
                assetType: nextAssetType,
                ticker: asset.ticker,
                currencyCode: "BRL",
              },
            },
          });
        }
        await updateTransaction({
          variables: {
            id: editTransaction!.id,
            input: txInput,
          },
        });
      } else {
        await recordTransaction({
          variables: {
            input: {
              financialAccountId: resolvedFinancialAccountId,
              assetId: resolvedAssetId,
              ...txInput,
            },
          },
        });
      }

      completeRecording();
    } catch {
      return;
    }
  };

  useEffect(() => {
    if (!open) return;
    if (!isEdit) return;

    const holdingId = editHolding?.id ?? null;
    const transactionId = editTransaction?.id ?? null;
    if (
      lastEditSeedRef.current?.holdingId === holdingId &&
      lastEditSeedRef.current?.transactionId === transactionId
    ) {
      return;
    }
    lastEditSeedRef.current = { holdingId, transactionId };

    const hasQuantity = editTransaction!.quantity != null && Number.isFinite(editTransaction!.quantity);
    const hasUnitPrice = editTransaction!.unitPrice != null && Number.isFinite(editTransaction!.unitPrice);
    const nextFlow = hasQuantity && hasUnitPrice ? "BY_QUANTITY" : "BY_AMOUNT";

    setValueFlow(nextFlow);
    setFeesMode("SUMMARY");

    const assetTicker = editHolding!.asset.ticker ?? "";
    methods.reset(
      {
        asset: {
          id: editHolding!.asset.id,
          ticker: assetTicker,
          name: editHolding!.asset.name,
          assetType: mapHoldingAssetTypeToB3(editHolding!.asset.assetType),
          isActive: true,
        },
        quantity: nextFlow === "BY_QUANTITY" ? editTransaction!.quantity ?? undefined : undefined,
        tradeDate: toDateValue(editTransaction!.tradeDate),
        unitPrice: nextFlow === "BY_QUANTITY" ? editTransaction!.unitPrice ?? undefined : editTransaction!.unitPrice ?? undefined,
        grossValue: nextFlow === "BY_AMOUNT" ? editTransaction!.grossValue : undefined,
        totalFees: undefined,
        brokerageFee: undefined,
        settlementFee: undefined,
        otherFees: undefined,
      },
      { keepDirty: false },
    );
  }, [editHolding, editTransaction, isEdit, mapHoldingAssetTypeToB3, methods, open, toDateValue]);

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={t("investments.recordEquity.title")}
      initialFinancialAccountId={financialAccountId || null}
      financialAccounts={financialAccounts}
      issuerSelection={{
        description: t("investments.recordEquity.selectAccount.description"),
        emptyState: t("investments.recordEquity.selectAccount.emptyState"),
        dataTestIdPrefix: "record-equity-select-account-",
      }}
      onResolvedChange={setResolvedFinancialAccountId}
      footer={
        ({ resolvedFinancialAccountId }) => (
          <Stack spacing={2}>
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
              <Typography variant="body2" color="text.secondary">
                {t("investments.recordEquity.footer.grossValue")}
              </Typography>
              <Typography variant="body2" fontWeight={700}>
                {computedGrossValue != null ? currencyFormatter.format(computedGrossValue) : "—"}
              </Typography>
            </Box>

            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
              <Typography variant="body2" color="text.secondary">
                {t("investments.recordEquity.footer.fees")}
              </Typography>
              <Typography variant="body2" fontWeight={700}>
                {currencyFormatter.format(computedTotalFees)}
              </Typography>
            </Box>

            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
              <Typography variant="subtitle2" color="text.secondary">
                {t("investments.recordEquity.footer.total")}
              </Typography>
              <Typography variant="h6" fontWeight={900}>
                {computedTransactionTotal != null ? currencyFormatter.format(computedTransactionTotal) : "—"}
              </Typography>
            </Box>

            <Drawer.ActionButtons
              cancelText={t("actions.cancel")}
              submitText={t("investments.recordEquity.actions.submit")}
              submittingText={t("investments.recordEquity.actions.submitting")}
              onCancel={onClose}
              submitForm="record-equity-form"
              cancelDisabled={isSubmitting}
              submitDisabled={isSubmitting || !resolvedFinancialAccountId}
              submitting={isSubmitting}
            />
          </Stack>
        )
      }
    >
      {({ resolvedFinancialAccountId }) => (
        <FormProvider {...methods}>
          <form id="record-equity-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <Stack spacing={2}>
              <ErrorAlert error={mutationError} />

                <Stack spacing={1}>
                  <Typography variant="overline" color="text.secondary">
                    {t("investments.recordEquity.sections.assetData")}
                  </Typography>
                  <Box
                    sx={{
                      display: "grid",
                      gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                      gap: 2,
                    }}
                  >
                    <EquityAutocomplete
                      name="asset"
                      data-testid="record-equity-autocomplete"
                      disabled={isEdit || Boolean(prefillHolding)}
                    />
                    <DatePickerField
                      name="tradeDate"
                      label={t("investments.recordEquity.fields.tradeDate")}
                      minDate={minAllowedDate.toDate()}
                      data-testid="record-equity-trade-date"
                    />
                  </Box>
                </Stack>

                <Divider />

                <Stack spacing={1}>
                  <Typography variant="overline" color="text.secondary">
                    {t("investments.recordEquity.sections.values")}
                  </Typography>
                  <SegmentedControl
                    fullWidth
                    value={valueFlow}
                    onChange={(v) => setValueFlow(v as "BY_AMOUNT" | "BY_QUANTITY")}
                    options={[
                      { value: "BY_QUANTITY", label: t("investments.recordEquity.flows.byQuantity") },
                      { value: "BY_AMOUNT", label: t("investments.recordEquity.flows.byAmount") },
                    ]}
                  />
                </Stack>

                {valueFlow === "BY_QUANTITY" ? (
                  <Stack spacing={2}>
                    <Box
                      sx={{
                        display: "grid",
                        gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                        gap: 2,
                      }}
                    >
                      <NumberField
                        name="quantity"
                        label={t("investments.recordEquity.fields.quantity")}
                        fullWidth
                        locale={locale}
                        fractionDigits={0}
                        allowNegative={false}
                        data-testid="record-equity-quantity"
                      />
                      <MoneyInput
                        name="unitPrice"
                        label={t("investments.recordEquity.fields.unitPrice")}
                        currency="BRL"
                        data-testid="record-equity-unit-price"
                      />
                    </Box>

                    <Paper
                      variant="outlined"
                      sx={{
                        p: 2,
                        borderRadius: 2,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: 2,
                      }}
                    >
                      <Typography variant="body2" color="text.secondary">
                        {t("investments.recordEquity.values.byQuantity.totalLabel")}
                      </Typography>
                      <Typography variant="body2" fontWeight={800}>
                        {computedTotalByQuantity != null ? currencyFormatter.format(computedTotalByQuantity) : "—"}
                      </Typography>
                    </Paper>
                  </Stack>
                ) : (
                  <Stack spacing={2}>
                    <MoneyInput
                      name="grossValue"
                      label={t("investments.recordEquity.fields.grossValue")}
                      currency="BRL"
                      data-testid="record-equity-gross-value"
                    />

                    <MoneyInput
                      name="unitPrice"
                      label={t("investments.recordEquity.fields.unitPrice")}
                      currency="BRL"
                      data-testid="record-equity-unit-price"
                    />

                    <Paper
                      variant="outlined"
                      sx={{
                        p: 2,
                        borderRadius: 2,
                        bgcolor: alpha(theme.palette.primary.main, 0.04),
                        borderColor: alpha(theme.palette.primary.main, 0.12),
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: 2,
                      }}
                    >
                      <Typography variant="body2" color="text.secondary">
                        {t("investments.recordEquity.values.byAmount.estimatedShares")}
                      </Typography>
                      <Typography variant="body2" fontWeight={700}>
                        {computedQuantityByAmount != null
                          ? computedQuantityByAmount.toLocaleString(locale, {
                              minimumFractionDigits: 0,
                              maximumFractionDigits: 0,
                            }) + " " + t("investments.recordEquity.values.byAmount.sharesUnit")
                          : "—"}
                      </Typography>
                    </Paper>
                  </Stack>
                )}

                <Divider />

                <Stack spacing={1}>
                  <Typography variant="overline" color="text.secondary">
                    {t("investments.recordEquity.sections.fees")}
                  </Typography>
                  <SegmentedControl
                    value={feesMode}
                    onChange={(v) => setFeesMode(v as "SUMMARY" | "DETAILED")}
                    options={[
                      { value: "SUMMARY", label: t("investments.recordEquity.fees.modes.summary") },
                      { value: "DETAILED", label: t("investments.recordEquity.fees.modes.detailed") },
                    ]}
                  />
                </Stack>

                {feesMode === "SUMMARY" ? (
                  <MoneyInput
                    name="totalFees"
                    label={t("investments.recordEquity.fees.totalFees")}
                    currency="BRL"
                    data-testid="record-equity-fees-total"
                  />
                ) : (
                  <Stack spacing={2}>
                    <MoneyInput
                      name="brokerageFee"
                      label={t("investments.recordEquity.fees.brokerage")}
                      currency="BRL"
                      data-testid="record-equity-fees-brokerage"
                    />
                    <MoneyInput
                      name="settlementFee"
                      label={t("investments.recordEquity.fees.settlement")}
                      currency="BRL"
                      data-testid="record-equity-fees-settlement"
                    />
                    <MoneyInput
                      name="otherFees"
                      label={t("investments.recordEquity.fees.other")}
                      currency="BRL"
                      data-testid="record-equity-fees-other"
                    />

                    <Paper
                      variant="outlined"
                      sx={{
                        p: 2,
                        borderRadius: 2,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: 2,
                      }}
                    >
                      <Typography variant="body2" color="text.secondary">
                        {t("investments.recordEquity.fees.totalFees")}
                      </Typography>
                      <Typography variant="body2" fontWeight={800}>
                        {currencyFormatter.format(computedDetailedFees)}
                      </Typography>
                    </Paper>
                  </Stack>
                )}
              </Stack>
          </form>
        </FormProvider>
      )}
    </InvestmentRecordDrawer>
  );
}
