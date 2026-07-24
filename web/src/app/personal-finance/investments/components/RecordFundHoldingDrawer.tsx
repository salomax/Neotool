"use client";

import React, { useEffect } from "react";
import { useApolloClient, useMutation, useQuery } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider, Tab, TextField } from "@mui/material";
import { Controller, FormProvider, useForm } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION, UPDATE_INVESTMENT_ASSET, UPDATE_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import { INVESTMENT_FUND_NAV } from "@/lib/graphql/operations/financial-data/queries";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  AssetType,
  HoldingPricingMode,
  InvestmentTransaction,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { FormRowStack } from "@/shared/components/ui/forms/components/FormRowStack";
import { FormStack } from "@/shared/components/ui/forms/components/FormStack";
import { FormTabs } from "@/shared/components/ui/forms/components/FormTabs";
import { DatePickerField, MoneyInput, NumberField, PercentField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { formatDateOnly, isValidDate, parseDateOnly, toStartOfDayLocal } from "@/shared/utils/date/dateOnly";
import { coerceInvestmentSettlementDate, validateInvestmentSettlementDates } from "@/shared/utils/investment/settlementDatePolicy";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { Label } from "@/shared/components/ui/primitives/Label";
import { FINANCE_MOVING_WINDOW_YEARS, getFinanceMinAllowedDate } from "../../finance-transactions/constants/dateWindow";
import { personalFinanceTranslations } from "../../i18n";
import { FundAutocomplete, FundAutocompleteValue } from "./FundAutocomplete";
import { InvestmentTradeAndSettlementDateRow } from "./InvestmentTradeAndSettlementDateRow";
import { useSyncDefaultSettlementDate } from "../hooks/useSyncDefaultSettlementDate";
import { mapCvmFundAssetType } from "@/lib/utils/mapCvmFundAssetType";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";
import type { FundGateResult } from "./transaction-editor/new-holding/newHoldingFlow.types";
import { UpdateFormSectionLabel } from "./update/UpdateFormSectionLabel";

interface RecordFundHoldingDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: IssuerSelectionAccount[];
  /** When true, renders only the form body (parent supplies [`InvestmentRecordDrawer`]). */
  embedded?: boolean;
  /** Required when `embedded` — custodian account id from parent drawer. */
  resolvedAccountId?: string | null;
  /** Two-step flow: step 2 locks listing UI and seeds fund identity from step 1. */
  assetGate?: { mode: "full" } | { mode: "locked"; result: FundGateResult };
  onRecorded?: () => void;
  editHolding?: {
    id: string;
    pricingMode: HoldingPricingMode | null;
    valuationReferenceDate: unknown | null;
    grossValue: number | null;
    netValue: number | null;
    asset: { id: string; name: string; assetType: AssetType };
  } | null;
  editTransaction?: InvestmentTransaction | null;
  prefillHolding?: {
    id: string;
    asset: { id: string; name: string; assetType: AssetType };
  } | null;
  initialDirection?: InvestmentTransactionDirection;
}

type ListingMode = "CVM" | "MANUAL";
type ValueFlow = "BY_AMOUNT" | "BY_QUANTITY";
type UnitPriceMode = "AUTO" | "MANUAL";

type RecordFundFormData = {
  fund: FundAutocompleteValue | null;
  manualFundName?: string;
  manualFundCnpj?: string;
  manualAssetType?: AssetType;
  quantity?: number | string;
  tradeDate?: Date;
  settlementDate?: Date;
  unitPrice?: number;
  grossValue?: number;
  manualCurrentGrossValue?: number;
  manualValuedAt?: Date;
  administrationFeeAnnualPercent?: number;
};

type UpdateInvestmentAssetMutationData = { updateInvestmentAsset: { id: string } };
type UpdateInvestmentTransactionMutationData = { updateInvestmentTransaction: { id: string } };

function buildDefaultValues(): RecordFundFormData {
  return {
    fund: null,
    manualFundName: "",
    manualFundCnpj: "",
    manualAssetType: AssetType.MutualFund,
    quantity: undefined,
    tradeDate: undefined,
    settlementDate: undefined,
    unitPrice: undefined,
    grossValue: undefined,
    manualCurrentGrossValue: undefined,
    manualValuedAt: toStartOfDayLocal(new Date()),
    administrationFeeAnnualPercent: undefined,
  };
}

export function RecordFundHoldingDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  embedded = false,
  resolvedAccountId = null,
  assetGate,
  onRecorded,
  editHolding,
  editTransaction,
  prefillHolding,
  initialDirection,
}: RecordFundHoldingDrawerProps) {
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
  const [valueFlow, setValueFlow] = React.useState<ValueFlow>("BY_AMOUNT");
  const [listingMode, setListingMode] = React.useState<ListingMode>("CVM");
  const [unitPriceMode, setUnitPriceMode] = React.useState<UnitPriceMode>("AUTO");
  const isEdit = Boolean(editHolding && editTransaction);
  const isLockedGate = assetGate?.mode === "locked";
  const showListingSection = !isEdit && !isLockedGate;
  const lastEditSeedRef = React.useRef<{ holdingId: string | null; transactionId: string | null } | null>(null);
  const lastListingModeRef = React.useRef<ListingMode>("CVM");

  const parseLocalizedNumber = React.useCallback(
    (value: unknown) => {
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
    [localeDecimal, localeGroup],
  );

  const createSchema = React.useMemo(
    () =>
      z.object({
        fund: z
          .object({
            id: z.string(),
            name: z.string(),
            cnpj: z.string(),
            assetType: z.string(),
            isActive: z.boolean(),
          })
          .nullable(),
        manualFundName: z.string().optional(),
        manualFundCnpj: z.string().optional(),
        manualAssetType: z.nativeEnum(AssetType).optional(),
        quantity: z.preprocess(parseLocalizedNumber, z.number().positive().optional()),
        unitPrice: z.preprocess(parseLocalizedNumber, z.number().positive().optional()),
        grossValue: z.preprocess(parseLocalizedNumber, z.number().positive().optional()),
        manualCurrentGrossValue: z.preprocess(parseLocalizedNumber, z.number().positive().optional()),
        administrationFeeAnnualPercent: z.preprocess(parseLocalizedNumber, z.number().min(0).max(100).optional()),
        tradeDate: z.preprocess(
          (value) => (value === "" || value === null || value === undefined ? undefined : value),
          z.date().optional(),
        ),
        settlementDate: z.preprocess(
          (value) => (value === "" || value === null || value === undefined ? undefined : value),
          z.date().optional(),
        ),
        manualValuedAt: z.preprocess(
          (value) => (value === "" || value === null || value === undefined ? undefined : value),
          z.date().optional(),
        ),
      })
      .superRefine((data, ctx) => {
        if (data.tradeDate && dayjs(data.tradeDate).isBefore(minAllowedDate, "day")) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t("financeTransactions.validation.dateTooOld", { years: FINANCE_MOVING_WINDOW_YEARS }),
            path: ["tradeDate"],
          });
        }
        if (data.tradeDate && !data.settlementDate) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t("investments.updateHolding.validation.settlementDateRequired"),
            path: ["settlementDate"],
          });
        }
        validateInvestmentSettlementDates(data, ctx, t);
      }),
    [minAllowedDate, parseLocalizedNumber, t],
  );

  const methods = useForm<RecordFundFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: buildDefaultValues(),
  });

  const { reset, clearErrors, setError } = methods;
  const selectedFund = methods.watch("fund");
  const selectedTradeDate = methods.watch("tradeDate");
  const tradeDateIso = React.useMemo(
    () => (isValidDate(selectedTradeDate) ? formatDateOnly(selectedTradeDate) : null),
    [selectedTradeDate],
  );

  useSyncDefaultSettlementDate<RecordFundFormData>({
    enabled: open,
    assetKind: "fund",
    tradeDate: selectedTradeDate,
    setValue: methods.setValue,
  });

  const fundNavQuery = useQuery<
    {
      investmentFund: {
        id: string;
        latestNav: { date: string; closePrice: number } | null;
        priceHistory: Array<{ date: string; closePrice: number }>;
      } | null;
    },
    { id: string; to: string }
  >(INVESTMENT_FUND_NAV, {
    variables: { id: selectedFund?.id ?? "", to: tradeDateIso ?? "" },
    skip: listingMode !== "CVM" || !selectedFund?.id || !tradeDateIso,
    fetchPolicy: "cache-and-network",
    errorPolicy: "all",
  });

  const suggestedUnitPrice =
    listingMode === "CVM" ? (fundNavQuery.data?.investmentFund?.priceHistory?.[0]?.closePrice ?? null) : null;

  useEffect(() => {
    if (!open) return;
    if (!suggestedUnitPrice) return;
    if (unitPriceMode !== "AUTO") return;
    methods.setValue("unitPrice", suggestedUnitPrice, { shouldDirty: false, shouldTouch: true, shouldValidate: true });
  }, [methods, open, suggestedUnitPrice, unitPriceMode]);

  useEffect(() => {
    if (!open) return;
    if (listingMode !== "CVM") return;
    setUnitPriceMode("AUTO");
  }, [listingMode, open, selectedFund?.id]);

  useEffect(() => {
    if (!open) {
      lastEditSeedRef.current = null;
      reset(buildDefaultValues());
      setValueFlow("BY_AMOUNT");
      setListingMode("CVM");
      setUnitPriceMode("AUTO");
    }
  }, [open, reset]);

  useEffect(() => {
    if (!embedded) return;
    setResolvedFinancialAccountId(resolvedAccountId ?? null);
  }, [embedded, resolvedAccountId]);

  useEffect(() => {
    if (!open || !embedded || !isLockedGate || !assetGate || assetGate.mode !== "locked") return;
    const r = assetGate.result;
    if (r.kind === "fund_cvm") {
      setListingMode("CVM");
      setUnitPriceMode("AUTO");
      reset({
        ...buildDefaultValues(),
        fund: r.fund,
        tradeDate: dayjs().startOf("day").toDate(),
        settlementDate: coerceInvestmentSettlementDate("fund", dayjs().startOf("day").toDate(), undefined),
      });
    } else {
      setListingMode("MANUAL");
      setUnitPriceMode("MANUAL");
      reset({
        ...buildDefaultValues(),
        manualFundName: r.manualFundName,
        manualFundCnpj: r.manualFundCnpj,
        manualAssetType: r.manualAssetType,
        tradeDate: dayjs().startOf("day").toDate(),
        settlementDate: coerceInvestmentSettlementDate("fund", dayjs().startOf("day").toDate(), undefined),
      });
    }
  }, [assetGate, embedded, isLockedGate, open, reset]);

  useEffect(() => {
    if (!open) return;
    if (isEdit || embedded) return;
    if (!prefillHolding) return;

    setListingMode("CVM");
    setUnitPriceMode("AUTO");
    methods.setValue(
      "fund",
      {
        id: prefillHolding.asset.id,
        name: prefillHolding.asset.name,
        cnpj: "",
        assetType: String(prefillHolding.asset.assetType),
        isActive: true,
      },
      { shouldDirty: false, shouldValidate: true },
    );
  }, [embedded, isEdit, methods, open, prefillHolding]);

  useEffect(() => {
    if (isEdit || embedded || isLockedGate) return;
    if (lastListingModeRef.current === listingMode) return;
    lastListingModeRef.current = listingMode;
    clearErrors();
    methods.setValue("fund", null, { shouldDirty: false, shouldValidate: false });
    methods.setValue("unitPrice", undefined, { shouldDirty: false, shouldValidate: false });
    setUnitPriceMode(listingMode === "CVM" ? "AUTO" : "MANUAL");
  }, [clearErrors, embedded, isEdit, isLockedGate, listingMode, methods]);

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
    success(t("investments.recordFund.success"));
    onRecorded?.();
    onClose();

    void apolloClient
      .refetchQueries({
        include: [
          INVESTMENT_HOLDINGS,
          INVESTMENT_PORTFOLIO_SUMMARY,
          INVESTMENT_CASH_BALANCE,
          INVESTMENT_CASH_TRANSACTIONS,
        ],
      })
      .catch(() => undefined);
  }, [apolloClient, onClose, onRecorded, success, t]);

  const isSubmitting = creatingAsset || recordingTx || updatingAsset || updatingTx;
  const mutationError = createAssetError || recordTxError || updateAssetError || updateTxError;

  const onSubmit = async (data: RecordFundFormData) => {
    if (!resolvedFinancialAccountId) return;
    if (!data.tradeDate) {
      setError("tradeDate", { type: "required", message: t("investments.recordFund.validation.tradeDateRequired") });
      return;
    }
    if (!data.settlementDate) {
      setError("settlementDate", { type: "required", message: t("investments.updateHolding.validation.settlementDateRequired") });
      return;
    }

    const isManual = isEdit ? editHolding!.pricingMode === HoldingPricingMode.Manual : listingMode === "MANUAL";
    if (!isEdit && !isManual && !data.fund) {
      setError("fund", { type: "required", message: t("investments.recordFund.validation.fundRequired") });
      return;
    }
    if (isManual && !data.manualFundName?.trim()) {
      setError("manualFundName", { type: "required", message: t("investments.recordFund.validation.manualFundNameRequired") });
      return;
    }

    if (valueFlow === "BY_AMOUNT") {
      if (!data.grossValue) {
        setError("grossValue", { type: "required", message: t("investments.recordFund.validation.grossValueRequired") });
        return;
      }
      if (!data.unitPrice) {
        setError("unitPrice", { type: "required", message: t("investments.recordFund.validation.unitPriceRequired") });
        return;
      }
    } else {
      if (!data.quantity) {
        setError("quantity", { type: "required", message: t("investments.recordFund.validation.quantityRequired") });
        return;
      }
      if (!data.unitPrice) {
        setError("unitPrice", { type: "required", message: t("investments.recordFund.validation.unitPriceRequired") });
        return;
      }
    }

    if (isManual) {
      if (!data.manualCurrentGrossValue) {
        setError("manualCurrentGrossValue", {
          type: "required",
          message: t("investments.recordFund.validation.manualCurrentGrossValueRequired"),
        });
        return;
      }
      if (!data.manualValuedAt) {
        setError("manualValuedAt", {
          type: "required",
          message: t("investments.recordFund.validation.manualValuedAtRequired"),
        });
        return;
      }
    }

    const tradeDate = formatDateOnly(data.tradeDate);
    const unitPrice = data.unitPrice ?? suggestedUnitPrice;
    if (!unitPrice) return;
    const quantity = valueFlow === "BY_AMOUNT" ? (data.grossValue ? data.grossValue / unitPrice : NaN) : Number(data.quantity);
    const grossValue = valueFlow === "BY_AMOUNT" ? (data.grossValue ?? 0) : quantity * unitPrice;
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setError("quantity", { type: "required", message: t("investments.recordFund.validation.quantityPositive") });
      return;
    }
    if (!Number.isFinite(grossValue) || grossValue <= 0) {
      setError("grossValue", { type: "required", message: t("investments.recordFund.validation.grossValuePositive") });
      return;
    }

    const pricingMode = isEdit
      ? (editHolding!.pricingMode ?? HoldingPricingMode.Manual)
      : isManual
        ? HoldingPricingMode.Manual
        : HoldingPricingMode.MarketData;

    const resolvedAssetId =
      isEdit
        ? editHolding!.asset.id
        : prefillHolding
          ? prefillHolding.asset.id
        : (await (async () => {
            const assetType = isManual
              ? data.manualAssetType ?? AssetType.MutualFund
              : mapCvmFundAssetType(data.fund!.assetType);
            const fundCnpj = isManual ? data.manualFundCnpj?.trim() || undefined : data.fund?.cnpj;

            const createdAsset = await createAsset({
              variables: {
                input: {
                  name: isManual ? data.manualFundName!.trim() : data.fund!.name,
                  assetType,
                  fundCnpj,
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
      settlementDate: formatDateOnly(coerceInvestmentSettlementDate("fund", data.tradeDate, data.settlementDate)),
      pricingMode,
      manualValuation:
        pricingMode === HoldingPricingMode.Manual && data.manualCurrentGrossValue && data.manualValuedAt
          ? {
              grossValue: data.manualCurrentGrossValue,
              valuedAt: formatDateOnly(data.manualValuedAt),
            }
          : undefined,
      administrationFeeAnnualPercent: data.administrationFeeAnnualPercent,
    };

    try {
      if (isEdit) {
        const nextAssetType = isManual ? data.manualAssetType ?? AssetType.MutualFund : editHolding!.asset.assetType;
        const nextAssetName = isManual ? data.manualFundName!.trim() : editHolding!.asset.name;
        const assetChanged = nextAssetName !== editHolding!.asset.name || nextAssetType !== editHolding!.asset.assetType;
        if (assetChanged) {
          await updateAsset({
            variables: {
              id: resolvedAssetId,
              input: {
                name: nextAssetName,
                assetType: nextAssetType,
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

    const nextListingMode: ListingMode = editHolding!.pricingMode === HoldingPricingMode.Manual ? "MANUAL" : "CVM";
    setListingMode(nextListingMode);
    setUnitPriceMode(nextListingMode === "CVM" ? "AUTO" : "MANUAL");

    const hasQuantity = editTransaction!.quantity != null && Number.isFinite(editTransaction!.quantity);
    const hasUnitPrice = editTransaction!.unitPrice != null && Number.isFinite(editTransaction!.unitPrice);
    const nextFlow: ValueFlow = hasQuantity && hasUnitPrice ? "BY_QUANTITY" : "BY_AMOUNT";
    setValueFlow(nextFlow);

    const initialCurrentValue =
      typeof editHolding!.grossValue === "number"
        ? editHolding!.grossValue
        : typeof editHolding!.netValue === "number"
          ? editHolding!.netValue
          : undefined;

    reset(
      {
        fund: null,
        manualFundName: nextListingMode === "MANUAL" ? editHolding!.asset.name ?? "" : "",
        manualFundCnpj: "",
        manualAssetType: editHolding!.asset.assetType,
        quantity: nextFlow === "BY_QUANTITY" ? editTransaction!.quantity ?? undefined : undefined,
        tradeDate:
          parseDateOnly(
            editTransaction!.tradeDate != null ? String(editTransaction!.tradeDate) : null,
          ) ?? undefined,
        settlementDate:
          parseDateOnly(
            editTransaction!.settlementDate != null ? String(editTransaction!.settlementDate) : null,
          ) ?? undefined,
        unitPrice: editTransaction!.unitPrice ?? undefined,
        grossValue: nextFlow === "BY_AMOUNT" ? editTransaction!.grossValue : undefined,
        manualCurrentGrossValue: nextListingMode === "MANUAL" ? initialCurrentValue : undefined,
        manualValuedAt:
          parseDateOnly(
            editHolding!.valuationReferenceDate != null
              ? String(editHolding!.valuationReferenceDate)
              : null,
          ) ?? toStartOfDayLocal(new Date()),
        administrationFeeAnnualPercent: undefined,
      },
      { keepDirty: false },
    );
  }, [editHolding, editTransaction, isEdit, open, reset]);

  const fundForm = (activeAccountId: string | null) => (
    <FormProvider {...methods}>
      <form id="record-fund-form" onSubmit={methods.handleSubmit(onSubmit)}>
        <FormStack spacing={2}>
              <ErrorAlert error={mutationError} />

              {showListingSection ? (
                <>
                  <FormStack spacing={0.5}>
                    <Label sx={{ mb: 0 }}>{t("investments.recordFund.sections.listing")}</Label>
                    <FormTabs
                      value={listingMode}
                      onChange={(_, next: ListingMode) => {
                        setListingMode(next);
                      }}
                    >
                      <Tab value="CVM" label={t("investments.recordFund.modes.cvmListed")} disabled={isEdit} />
                      <Tab value="MANUAL" label={t("investments.recordFund.modes.notListed")} disabled={isEdit} />
                    </FormTabs>
                  </FormStack>

                  {listingMode === "CVM" ? (
                    isEdit ? (
                      <TextField
                        value={editHolding?.asset.name ?? ""}
                        label={t("investments.recordFund.sections.fund")}
                        size="medium"
                        disabled
                        data-testid="record-fund-edit-fund-name"
                      />
                    ) : (
                      <FundAutocomplete
                        name="fund"
                        size="medium"
                        data-testid="record-fund-autocomplete"
                        disabled={Boolean(prefillHolding)}
                      />
                    )
                  ) : (
                    <FormStack spacing={1.5}>
                      <Controller
                        name="manualFundName"
                        control={methods.control}
                        render={({ field, fieldState }) => (
                          <TextField
                            {...field}
                            label={t("investments.recordFund.fields.manualFundName")}
                            size="medium"
                            error={!!fieldState.error}
                            helperText={fieldState.error?.message}
                            data-testid="record-fund-manual-name"
                          />
                        )}
                      />
                      <Controller
                        name="manualFundCnpj"
                        control={methods.control}
                        render={({ field, fieldState }) => (
                          <TextField
                            {...field}
                            label={t("investments.recordFund.fields.manualFundCnpj")}
                            size="medium"
                            error={!!fieldState.error}
                            helperText={fieldState.error?.message}
                            data-testid="record-fund-manual-cnpj"
                          />
                        )}
                      />
                      <FormStack spacing={0.5}>
                        <Label sx={{ mb: 0 }}>{t("investments.recordFund.fields.manualAssetType")}</Label>
                        <Controller
                          name="manualAssetType"
                          control={methods.control}
                          render={({ field }) => (
                            <SegmentedControl
                              fullWidth
                              value={field.value ?? AssetType.MutualFund}
                              onChange={(value) => field.onChange(value)}
                              options={[
                                { value: AssetType.MutualFund, label: t("investments.recordFund.assetTypes.MUTUAL_FUND") },
                                { value: AssetType.Pgbl, label: t("investments.recordFund.assetTypes.PGBL") },
                                { value: AssetType.Vgbl, label: t("investments.recordFund.assetTypes.VGBL") },
                              ]}
                            />
                          )}
                        />
                      </FormStack>
                    </FormStack>
                  )}
                </>
              ) : null}

              <UpdateFormSectionLabel title={t("investments.recordFund.sections.dates")} />
              <InvestmentTradeAndSettlementDateRow<RecordFundFormData>
                tradeDateLabel={t("investments.recordFund.fields.tradeDate")}
                settlementDateLabel={t("investments.recordFund.fields.settlementDate")}
                size="medium"
                minDate={minAllowedDate.toDate()}
                tradeMaxDate={dayjs().startOf("day").toDate()}
                tradeDateTestId="record-fund-trade-date"
                settlementDateTestId="record-fund-settlement-date"
              />

              <FormStack spacing={1}>
                <UpdateFormSectionLabel title={t("investments.recordFund.sections.values")} />
                <FormTabs
                  value={valueFlow}
                  onChange={(_, next: ValueFlow) => {
                    setValueFlow(next);
                  }}
                >
                  <Tab value="BY_AMOUNT" label={t("investments.recordFund.flows.byAmount")} />
                  <Tab value="BY_QUANTITY" label={t("investments.recordFund.flows.byQuantity")} />
                </FormTabs>
              </FormStack>

              {valueFlow === "BY_AMOUNT" ? (
                <FormStack spacing={1.5}>
                  <FormRowStack alignItems="flex-start">
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <MoneyInput
                        name="grossValue"
                        label={t("investments.recordFund.fields.grossValue")}
                        currency="BRL"
                        size="medium"
                        fractionDigits={8}
                        fixedDecimalScale={false}
                        data-testid="record-fund-gross-value"
                      />
                    </Box>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                        <MoneyInput
                          name="unitPrice"
                          label={t("investments.recordFund.fields.unitPrice")}
                          currency="BRL"
                          size="medium"
                          fractionDigits={8}
                          fixedDecimalScale={false}
                          onNumericValueChange={(value) => {
                            setUnitPriceMode(value == null ? "AUTO" : "MANUAL");
                          }}
                          data-testid="record-fund-unit-price"
                        />
                    </Box>
                  </FormRowStack>
                </FormStack>
              ) : (
                <FormStack spacing={1.5}>
                    <Box
                      sx={{
                        display: "grid",
                        gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                        gap: 2,
                      }}
                    >
                      <NumberField
                        name="quantity"
                        label={t("investments.recordFund.fields.quantity")}
                        size="medium"
                        fullWidth
                        locale={locale}
                        allowNegative={false}
                        data-testid="record-fund-quantity"
                      />

                      <MoneyInput
                        name="unitPrice"
                        label={t("investments.recordFund.fields.unitPrice")}
                        currency="BRL"
                        size="medium"
                        fractionDigits={8}
                        fixedDecimalScale={false}
                        onNumericValueChange={(value) => {
                          setUnitPriceMode(value == null ? "AUTO" : "MANUAL");
                        }}
                        data-testid="record-fund-unit-price"
                      />
                    </Box>
                </FormStack>
              )}

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <PercentField
                    name="administrationFeeAnnualPercent"
                    label={t("investments.recordFund.fields.administrationFeeAnnualPercent")}
                    size="medium"
                    locale={locale}
                    fractionDigits={2}
                    data-testid="record-fund-administration-fee"
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0, display: { xs: "none", sm: "block" } }} aria-hidden />
              </FormRowStack>

              {listingMode === "MANUAL" ? (
                <>
                  <FormStack spacing={1}>
                    <UpdateFormSectionLabel title={t("investments.recordFund.sections.currentValue")} />
                    <FormRowStack alignItems="flex-start">
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <MoneyInput
                          name="manualCurrentGrossValue"
                          label={t("investments.recordFund.fields.manualCurrentGrossValue")}
                          currency="BRL"
                          size="medium"
                          data-testid="record-fund-manual-current-gross-value"
                        />
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <DatePickerField
                          name="manualValuedAt"
                          label={t("investments.recordFund.fields.manualValuedAt")}
                          size="medium"
                          maxDate={dayjs().startOf("day").toDate()}
                          data-testid="record-fund-manual-valued-at"
                        />
                      </Box>
                    </FormRowStack>
                  </FormStack>
                </>
              ) : null}

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.recordFund.actions.submit")}
                submittingText={t("investments.recordFund.actions.submitting")}
                onCancel={onClose}
                submitForm="record-fund-form"
                cancelDisabled={isSubmitting}
                submitDisabled={isSubmitting || !activeAccountId}
                submitting={isSubmitting}
              />
            </FormStack>
          </form>
        </FormProvider>
  );

  if (embedded) {
    return fundForm(resolvedAccountId ?? null);
  }

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={t("investments.recordFund.title")}
      initialFinancialAccountId={financialAccountId || null}
      financialAccounts={financialAccounts}
      issuerSelection={{
        description: t("investments.recordFund.selectAccount.description"),
        emptyState: t("investments.recordFund.selectAccount.emptyState"),
        dataTestIdPrefix: "record-fund-select-account-",
      }}
      onResolvedChange={setResolvedFinancialAccountId}
    >
      {({ resolvedFinancialAccountId }) => fundForm(resolvedFinancialAccountId)}
    </InvestmentRecordDrawer>
  );
}
