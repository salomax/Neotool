"use client";

import React, { useEffect } from "react";
import { useApolloClient, useLazyQuery, useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Avatar,
  Box,
  Button,
  CircularProgress,
  Divider,
  InputAdornment,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Tab,
  Typography,
} from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION, UPDATE_INVESTMENT_ASSET, UPDATE_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import {
  InvestmentAssetPriceDocument,
  type InvestmentAssetPriceQuery,
  type InvestmentAssetPriceQueryVariables,
} from "@/lib/graphql/operations/financial-data/queries.generated";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  AssetType,
  B3AssetType,
  InvestmentTransaction,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { CurrencyAmountParts } from "@/shared/components/ui/data-display";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { FormInfoBox } from "@/shared/components/ui/forms/components/FormInfoBox";
import { FormStack } from "@/shared/components/ui/forms/components/FormStack";
import { FormTabs } from "@/shared/components/ui/forms/components/FormTabs";
import { DatePickerField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { formatDateOnly, parseDateOnly } from "@/shared/utils/date/dateOnly";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { FINANCE_MOVING_WINDOW_YEARS, getFinanceMinAllowedDate } from "../../finance-transactions/constants/dateWindow";
import { personalFinanceTranslations } from "../../i18n";
import { EquityAutocomplete, EquityAutocompleteValue } from "./EquityAutocomplete";
import { mapB3AssetType } from "@/lib/utils/mapB3AssetType";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";
import type { EquityRecordVariant } from "./grid/investmentGridConfig";
import SectionDivider from "@/shared/components/ui/layout/SectionDivider";
import { FormRowStack } from "@/shared/components/ui/forms/components/FormRowStack";

function displayVariantFromGraphqlAssetType(assetType: AssetType): EquityRecordVariant | null {
  switch (assetType) {
    case AssetType.RealEstateFund:
    case AssetType.AgribusinessFund:
      return "fii";
    case AssetType.Stock:
    case AssetType.StockRights:
    case AssetType.Bdr:
    case AssetType.Etf:
    case AssetType.EtfInternational:
      return "stock";
    case AssetType.Option:
      return "mixed";
    default:
      return null;
  }
}

function displayVariantFromB3String(b3: string): EquityRecordVariant | null {
  if (b3 === "FII") return "fii";
  if (b3 === "EQUITY") return "stock";
  if (b3 === "OPTION_CALL" || b3 === "OPTION_PUT") return "mixed";
  return null;
}

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
  /** When opening from the investments grid, narrows copy and ticker search to stocks vs FIIs. */
  equityRecordVariant?: EquityRecordVariant;
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
  equityRecordVariant = "mixed",
}: RecordEquityHoldingDrawerProps) {
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
  const lastRequestedAssetPriceKeyRef = React.useRef<string | null>(null);
  const lastAppliedAssetPriceKeyRef = React.useRef<string | null>(null);
  const lastAutoUnitPriceRef = React.useRef<number | null>(null);
  const [assetPriceFetching, setAssetPriceFetching] = React.useState(false);
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

  const displayVariant = React.useMemo<EquityRecordVariant>(() => {
    if (selectedAsset?.assetType) {
      const fromSelection = displayVariantFromB3String(selectedAsset.assetType);
      if (fromSelection) return fromSelection;
    }
    const holding = editHolding ?? prefillHolding;
    if (holding?.asset?.assetType != null) {
      const fromHolding = displayVariantFromGraphqlAssetType(holding.asset.assetType);
      if (fromHolding != null) return fromHolding;
    }
    return equityRecordVariant;
  }, [editHolding, equityRecordVariant, prefillHolding, selectedAsset]);

  const autocompleteB3Filter = React.useMemo(() => {
    if (isEdit || prefillHolding) return undefined;
    if (equityRecordVariant === "stock") return B3AssetType.Equity;
    if (equityRecordVariant === "fii") return B3AssetType.Fii;
    return undefined;
  }, [equityRecordVariant, isEdit, prefillHolding]);

  const drawerTitle = React.useMemo(
    () => t(`investments.recordEquity.titles.${displayVariant}`),
    [displayVariant, t],
  );

  const quantityFieldLabel = React.useMemo(() => {
    const key =
      displayVariant === "fii"
        ? "investments.recordEquity.fields.quantityFii"
        : displayVariant === "stock"
          ? "investments.recordEquity.fields.quantityStock"
          : "investments.recordEquity.fields.quantityMixed";
    return t(key);
  }, [displayVariant, t]);

  const byAmountEstimatedLabel = React.useMemo(() => {
    const key =
      displayVariant === "fii"
        ? "investments.recordEquity.values.byAmount.estimatedFii"
        : displayVariant === "stock"
          ? "investments.recordEquity.values.byAmount.estimatedStock"
          : "investments.recordEquity.values.byAmount.estimatedMixed";
    return t(key);
  }, [displayVariant, t]);

  const byAmountUnitLabel = React.useMemo(() => {
    const key =
      displayVariant === "fii"
        ? "investments.recordEquity.values.byAmount.unitFii"
        : displayVariant === "stock"
          ? "investments.recordEquity.values.byAmount.unitStock"
          : "investments.recordEquity.values.byAmount.unitMixed";
    return t(key);
  }, [displayVariant, t]);

  const tradeDateIso = React.useMemo(
    () => (selectedTradeDate ? formatDateOnly(selectedTradeDate) : null),
    [selectedTradeDate],
  );

  const [fetchAssetPrice, assetPriceQuery] = useLazyQuery<InvestmentAssetPriceQuery, InvestmentAssetPriceQueryVariables>(
    InvestmentAssetPriceDocument,
    {
    fetchPolicy: "cache-and-network",
    errorPolicy: "all",
    },
  );

  useEffect(() => {
    if (!open) return;
    const activeTicker = selectedAsset?.ticker ?? null;
    if (!activeTicker) return;
    if (!tradeDateIso) return;
    const key = `${activeTicker}|${tradeDateIso}`;
    if (lastRequestedAssetPriceKeyRef.current === key) return;
    lastRequestedAssetPriceKeyRef.current = key;
    const currentUnitPrice = methods.getValues("unitPrice");
    if (currentUnitPrice == null || currentUnitPrice === lastAutoUnitPriceRef.current) {
      methods.setValue("unitPrice", undefined, { shouldDirty: false, shouldTouch: false, shouldValidate: true });
    }
    setAssetPriceFetching(true);
    fetchAssetPrice({ variables: { ticker: activeTicker, from: tradeDateIso } })
      .then((result) => {
        if (lastRequestedAssetPriceKeyRef.current !== key) return;
        setAssetPriceFetching(false);
        const latestUnitPrice = methods.getValues("unitPrice");
        if (latestUnitPrice != null && latestUnitPrice !== lastAutoUnitPriceRef.current) return;
        const price = result.data?.investmentAssetByTicker?.priceHistory?.[0]?.closePrice ?? null;
        if (price == null) return;
        if (lastAppliedAssetPriceKeyRef.current === key && latestUnitPrice === price) return;
        lastAppliedAssetPriceKeyRef.current = key;
        lastAutoUnitPriceRef.current = price;
        methods.setValue("unitPrice", price, { shouldDirty: false, shouldTouch: false, shouldValidate: true });
      })
      .catch(() => {
        if (lastRequestedAssetPriceKeyRef.current !== key) return;
        setAssetPriceFetching(false);
      });
  }, [fetchAssetPrice, methods, open, selectedAsset?.ticker, tradeDateIso]);

  const isAssetPriceLoading = open && Boolean(selectedAsset?.ticker) && Boolean(tradeDateIso) && assetPriceFetching;

  const unitPriceInputProps = React.useMemo(
    () => ({
      endAdornment: isAssetPriceLoading ? (
        <InputAdornment position="end">
          <CircularProgress size={16} />
        </InputAdornment>
      ) : null,
    }),
    [isAssetPriceLoading],
  );

  useEffect(() => {
    if (!open) {
      lastEditSeedRef.current = null;
      lastRequestedAssetPriceKeyRef.current = null;
      lastAppliedAssetPriceKeyRef.current = null;
      lastAutoUnitPriceRef.current = null;
      setAssetPriceFetching(false);
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

    const tradeDate = formatDateOnly(data.tradeDate);
    const unitPrice = data.unitPrice;
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
      settlementDate: formatDateOnly(dayjs(data.tradeDate).add(2, "day").toDate()),
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
        tradeDate:
          parseDateOnly(
            editTransaction!.tradeDate != null ? String(editTransaction!.tradeDate) : null,
          ) ?? undefined,
        unitPrice: nextFlow === "BY_QUANTITY" ? editTransaction!.unitPrice ?? undefined : editTransaction!.unitPrice ?? undefined,
        grossValue: nextFlow === "BY_AMOUNT" ? editTransaction!.grossValue : undefined,
        totalFees: undefined,
        brokerageFee: undefined,
        settlementFee: undefined,
        otherFees: undefined,
      },
      { keepDirty: false },
    );
  }, [editHolding, editTransaction, isEdit, mapHoldingAssetTypeToB3, methods, open]);

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={drawerTitle}
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

            <Divider />

            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, pb: 2 }}>
              <Typography variant="h3" color="text.secondary">
                {t("investments.recordEquity.footer.total")}
              </Typography>
              <CurrencyAmountParts
                value={computedTransactionTotal}
                currency="BRL"
                locale={locale}
                variant="h3"
                fontWeight={900}
              />
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
            <FormStack>
              <ErrorAlert error={mutationError} />

                <FormStack>
                  <Box
                    sx={{
                      display: "grid",
                      gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                      gap: 2,
                    }}
                  >
                    <EquityAutocomplete
                      name="asset"
                      assetTypeFilter={autocompleteB3Filter}
                      data-testid="record-equity-autocomplete"
                      disabled={isEdit || Boolean(prefillHolding)}
                    />
                    <DatePickerField
                      name="tradeDate"
                      label={t("investments.recordEquity.fields.tradeDate")}
                      minDate={minAllowedDate.toDate()}
                      disableFuture={true}
                      data-testid="record-equity-trade-date"
                    />
                  </Box>
                </FormStack>

                <FormStack>
                  <FormTabs
                    value={valueFlow}
                    onChange={(_, next: "BY_AMOUNT" | "BY_QUANTITY") => setValueFlow(next)}
                    data-testid="record-equity-value-flow-tabs"
                  >
                    <Tab
                      value="BY_QUANTITY"
                      label={t("investments.recordEquity.flows.byQuantity")}
                      data-testid="record-equity-flow-by-quantity"
                    />
                    <Tab
                      value="BY_AMOUNT"
                      label={t("investments.recordEquity.flows.byAmount")}
                      data-testid="record-equity-flow-by-amount"
                    />
                  </FormTabs>
                </FormStack>

                {valueFlow === "BY_QUANTITY" ? (
                  <FormStack>
                    <FormRowStack alignItems="flex-start">
                      <NumberField
                        name="quantity"
                        label={quantityFieldLabel}
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
                        InputProps={unitPriceInputProps}
                        data-testid="record-equity-unit-price"
                      />
                    </FormRowStack>

                    <FormInfoBox>
                      <Typography variant="body2" color="text.secondary">
                        {t("investments.recordEquity.values.byQuantity.totalLabel")}
                      </Typography>
                      <Typography variant="body2" fontWeight={800}>
                        {computedTotalByQuantity != null ? currencyFormatter.format(computedTotalByQuantity) : "—"}
                      </Typography>
                    </FormInfoBox>
                  </FormStack>
                ) : (
                  <FormStack>
                    <FormRowStack alignItems="flex-start">
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
                        InputProps={unitPriceInputProps}
                        data-testid="record-equity-unit-price"
                      />
                    </FormRowStack>

                    <FormInfoBox>
                      <Typography variant="body2" color="text.secondary">
                        {byAmountEstimatedLabel}
                      </Typography>
                      <Typography variant="body2" fontWeight={700}>
                        {computedQuantityByAmount != null
                          ? computedQuantityByAmount.toLocaleString(locale, {
                              minimumFractionDigits: 0,
                              maximumFractionDigits: 0,
                            }) + " " + byAmountUnitLabel
                          : "—"}
                      </Typography>
                    </FormInfoBox>
                  </FormStack>
                )}

                <FormStack>
                  <FormTabs
                    value={feesMode}
                    onChange={(_, next: "SUMMARY" | "DETAILED") => setFeesMode(next)}
                    data-testid="record-equity-fees-mode-tabs"
                  >
                    <Tab
                      value="SUMMARY"
                      label={t("investments.recordEquity.fees.modes.summary")}
                      data-testid="record-equity-fees-mode-summary"
                    />
                    <Tab
                      value="DETAILED"
                      label={t("investments.recordEquity.fees.modes.detailed")}
                      data-testid="record-equity-fees-mode-detailed"
                    />
                  </FormTabs>
                </FormStack>

                {feesMode === "SUMMARY" ? (
                  <MoneyInput
                    name="totalFees"
                    label={t("investments.recordEquity.fees.totalFees")}
                    currency="BRL"
                    data-testid="record-equity-fees-total"
                  />
                ) : (
                  <FormStack>
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

                    <FormInfoBox>
                      <Typography variant="body2" color="text.secondary">
                        {t("investments.recordEquity.fees.totalFees")}
                      </Typography>
                      <Typography variant="body2" fontWeight={800}>
                        {currencyFormatter.format(computedDetailedFees)}
                      </Typography>
                    </FormInfoBox>
                  </FormStack>
                )}
              </FormStack>
          </form>
        </FormProvider>
      )}
    </InvestmentRecordDrawer>
  );
}
