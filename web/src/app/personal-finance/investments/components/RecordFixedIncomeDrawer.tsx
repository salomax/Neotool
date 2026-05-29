"use client";

import React, { useEffect } from "react";
import { useApolloClient, useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Avatar, Box, Divider, List, ListItemButton, ListItemIcon, ListItemText, Paper, Typography, alpha, useTheme } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { FormProvider, useForm, useWatch } from "react-hook-form";
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
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  AssetType,
  CouponFrequency,
  Indexer,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  InvestmentTransaction,
} from "@/lib/graphql/types/__generated__/graphql";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { FormStack, FormRowStack } from "@/shared/components/ui/forms/components";
import { CheckboxField, DatePickerField, FormTextField as TextField, MoneyInput, NumberField, PercentField, SelectField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { formatDateOnly, parseDateOnly } from "@/shared/utils/date/dateOnly";
import { coerceInvestmentSettlementDate, validateInvestmentSettlementDates } from "@/shared/utils/investment/settlementDatePolicy";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { FINANCE_MOVING_WINDOW_YEARS, getFinanceMinAllowedDate } from "../../finance-transactions/constants/dateWindow";
import { personalFinanceTranslations } from "../../i18n";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";
import { InvestmentTradeAndSettlementDateRow } from "./InvestmentTradeAndSettlementDateRow";
import { useSyncDefaultSettlementDate } from "../hooks/useSyncDefaultSettlementDate";
import { getCurrentLocale } from "./table/investmentTableHelpers";

interface RecordFixedIncomeDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: IssuerSelectionAccount[];
  onRecorded?: () => void;
  editHolding?: {
    id: string;
    asset: {
      id: string;
      name: string;
      assetType: AssetType;
      issuerName: string | null;
    };
    indexer: Indexer | null;
    couponDayOfMonth: number | null;
    indexerPercentage: number | null;
    fixedAnnualRate: number | null;
    maturityDate: unknown | null;
    isTaxExempt: boolean;
    couponFrequency: CouponFrequency | null;
  } | null;
  editTransaction?: InvestmentTransaction | null;
}

const FIXED_INCOME_ASSET_TYPES: readonly AssetType[] = [
  AssetType.Cdb,
  AssetType.Rdb,
  AssetType.Lci,
  AssetType.Lca,
  AssetType.Lc,
  AssetType.Lf,
  AssetType.Lig,
  AssetType.Debentures,
  AssetType.Cri,
  AssetType.Cra,
  AssetType.Treasury,
] as const;

const FIXED_INCOME_INDEXERS: readonly Indexer[] = [
  Indexer.Cdi,
  Indexer.Di,
  Indexer.Ipca,
  Indexer.IgpM,
  Indexer.Selic,
  Indexer.PreFixado,
  Indexer.Outros,
] as const;

const TAX_EXEMPT_ASSET_TYPES: readonly AssetType[] = [AssetType.Lci, AssetType.Lca, AssetType.Cri, AssetType.Cra] as const;

type RecordFixedIncomeFormData = {
  couponDayOfMonth?: number;
  assetName: string;
  assetType?: AssetType;
  issuerName: string;
  indexer?: Indexer;
  indexerPercentage?: number;
  fixedAnnualRate?: number;
  quantity?: number | string;
  grossValue: number;
  tradeDate?: Date;
  settlementDate?: Date;
  maturityDate?: Date;
  isTaxExempt: boolean;
  couponFrequency: CouponFrequency;
};

type UpdateInvestmentAssetMutationData = { updateInvestmentAsset: { id: string } };
type UpdateInvestmentAssetMutationVars = {
  id: string;
  input: { name: string; assetType: AssetType; issuerName?: string; currencyCode?: string };
};
type UpdateInvestmentTransactionMutationData = { updateInvestmentTransaction: { id: string } };
type UpdateInvestmentTransactionMutationVars = {
  id: string;
  input: {
    direction: InvestmentTransactionDirection;
    type: InvestmentTransactionType;
    quantity: number;
    unitPrice: number;
    grossValue: number;
    tradeDate: string;
    settlementDate: string;
    indexer: Indexer;
    indexerPercentage?: number;
    fixedAnnualRate?: number;
    issueDate: string;
    maturityDate: string;
    isTaxExempt: boolean;
    couponFrequency?: CouponFrequency | null;
    couponDayOfMonth?: number | null;
  };
};

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function RecordFixedIncomeDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
  editHolding,
  editTransaction,
}: RecordFixedIncomeDrawerProps) {
  const theme = useTheme();
  const apolloClient = useApolloClient();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = getCurrentLocale();
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(
    () => getLocaleSeparators(locale),
    [locale],
  );
  const today = React.useMemo(() => dayjs().startOf("day"), []);
  const minAllowedDate = React.useMemo(() => getFinanceMinAllowedDate(), []);
  const maxMaturityDate = React.useMemo(() => dayjs().startOf("day").add(50, "year"), []);
  const [resolvedFinancialAccountId, setResolvedFinancialAccountId] = React.useState<string | null>(null);

  const createSchema = React.useMemo(
    () =>
      z
        .object({
          assetName: z.string().trim().min(1, t("investments.recordHolding.validation.assetNameRequired")),
          assetType: z.nativeEnum(AssetType, {
            required_error: t("investments.recordHolding.validation.assetTypeRequired"),
          }),
          issuerName: z.string().trim().min(1, t("investments.recordHolding.validation.issuerRequired")),
          indexer: z.nativeEnum(Indexer, {
            required_error: t("investments.recordHolding.validation.indexerRequired"),
          }),
          indexerPercentage: z.preprocess(
            (value) => {
              if (value === "" || value === null || value === undefined) return undefined;
              // `PercentField` already parses locale-formatted user input into a number.
              // If we normalize numeric inputs as if they were locale strings, we can
              // incorrectly treat the decimal dot as a thousands separator in pt-BR.
              if (typeof value === "number") return value;
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
            z.number().positive(t("investments.recordHolding.validation.indexerPercentageRequired")).optional(),
          ),
          fixedAnnualRate: z.preprocess(
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
                normalized = raw.replace(new RegExp(`\\${localeGroup}`, "g"), "").replace(localeDecimal, ".");
              } else {
                normalized = raw.replace(localeDecimal, ".");
              }
              const parsed = Number(normalized);
              return Number.isNaN(parsed) ? undefined : parsed;
            },
            z.number().positive().optional(),
          ),
          quantity: z.preprocess(
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
                normalized = raw.replace(new RegExp(`\\${localeGroup}`, "g"), "").replace(localeDecimal, ".");
              } else {
                normalized = raw.replace(localeDecimal, ".");
              }
              const parsed = Number(normalized);
              return Number.isNaN(parsed) ? undefined : parsed;
            },
            z.number().min(0, t("investments.recordHolding.validation.quantityNonNegative")).default(1),
          ),
          grossValue: z.preprocess(
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
                normalized = raw.replace(new RegExp(`\\${localeGroup}`, "g"), "").replace(localeDecimal, ".");
              } else {
                normalized = raw.replace(localeDecimal, ".");
              }
              const parsed = Number(normalized);
              return Number.isNaN(parsed) ? undefined : parsed;
            },
            z
              .number({ required_error: t("investments.recordHolding.validation.grossValueRequired") })
              .positive(t("investments.recordHolding.validation.grossValuePositive")),
          ),
          tradeDate: z.preprocess(
            (value) => (value === "" || value === null || value === undefined ? undefined : value),
            z.date({
              required_error: t("investments.recordHolding.validation.tradeDateRequired"),
              invalid_type_error: t("investments.recordHolding.validation.tradeDateRequired"),
            }),
          ),
          settlementDate: z.preprocess(
            (value) => (value === "" || value === null || value === undefined ? undefined : value),
            z.date({
              required_error: t("investments.updateHolding.validation.settlementDateRequired"),
              invalid_type_error: t("investments.updateHolding.validation.settlementDateRequired"),
            }),
          ),
          maturityDate: z.preprocess(
            (value) => (value === "" || value === null || value === undefined ? undefined : value),
            z.date({
              required_error: t("investments.recordHolding.validation.maturityDateRequired"),
              invalid_type_error: t("investments.recordHolding.validation.maturityDateRequired"),
            }),
          ),
          isTaxExempt: z.boolean().default(false),
          couponDayOfMonth: z
            .number()
            .int()
            .min(1, t("investments.recordHolding.validation.couponDayOfMonthRange"))
            .max(31, t("investments.recordHolding.validation.couponDayOfMonthRange"))
            .optional(),
          couponFrequency: z.nativeEnum(CouponFrequency).default(CouponFrequency.AtMaturity),
        })
        .superRefine((data, ctx) => {
          if (data.couponFrequency !== CouponFrequency.AtMaturity && !data.couponDayOfMonth) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.couponDayOfMonthRequired"),
              path: ["couponDayOfMonth"],
            });
          }
          if (!FIXED_INCOME_ASSET_TYPES.includes(data.assetType)) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.assetTypeRequired"),
              path: ["assetType"],
            });
          }

          if (!FIXED_INCOME_INDEXERS.includes(data.indexer)) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.indexerRequired"),
              path: ["indexer"],
            });
          }

          if (data.indexer !== Indexer.PreFixado && !data.indexerPercentage) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.indexerPercentageRequired"),
              path: ["indexerPercentage"],
            });
          }

          if (data.indexer === Indexer.PreFixado && !data.fixedAnnualRate) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.fixedAnnualRateRequired"),
              path: ["fixedAnnualRate"],
            });
          }

          if (data.maturityDate.getTime() <= data.tradeDate.getTime()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.maturityAfterTrade"),
              path: ["maturityDate"],
            });
          }

          if (dayjs(data.tradeDate).isBefore(minAllowedDate, "day")) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("financeTransactions.validation.dateTooOld", { years: FINANCE_MOVING_WINDOW_YEARS }),
              path: ["tradeDate"],
            });
          }

          if (dayjs(data.tradeDate).isAfter(today, "day")) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.tradeDateNotInFuture"),
              path: ["tradeDate"],
            });
          }

          validateInvestmentSettlementDates(data, ctx, t);

          if (dayjs(data.maturityDate).isAfter(maxMaturityDate, "day")) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.maturityDateTooFar", { years: 50 }),
              path: ["maturityDate"],
            });
          }
        }),
    [localeDecimal, localeGroup, maxMaturityDate, minAllowedDate, t, today],
  );

  const methods = useForm<RecordFixedIncomeFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      assetName: "",
      assetType: undefined,
      issuerName: "",
      indexer: undefined,
      indexerPercentage: undefined,
      fixedAnnualRate: undefined,
      quantity: 1,
      grossValue: undefined,
      tradeDate: undefined,
      settlementDate: undefined,
      maturityDate: undefined,
      isTaxExempt: false,
      couponFrequency: CouponFrequency.AtMaturity,
      couponDayOfMonth: undefined,
    },
  });

  const { reset, setValue, getValues } = methods;
  const watchedAssetName = useWatch({ control: methods.control, name: "assetName" });
  const watchedAssetType = useWatch({ control: methods.control, name: "assetType" });
  const watchedIndexer = useWatch({ control: methods.control, name: "indexer" });
  const watchedTradeDate = useWatch({ control: methods.control, name: "tradeDate" });

  useSyncDefaultSettlementDate<RecordFixedIncomeFormData>({
    enabled: open,
    assetKind: "fixedIncome",
    tradeDate: watchedTradeDate,
    setValue: methods.setValue,
  });

  const watchedQuantity = useWatch({ control: methods.control, name: "quantity" });
  const watchedGrossValue = useWatch({ control: methods.control, name: "grossValue" });
  const watchedCouponFrequency = useWatch({ control: methods.control, name: "couponFrequency" });
  const isTreasury = watchedAssetType === AssetType.Treasury;
  const isAtMaturity = watchedCouponFrequency === CouponFrequency.AtMaturity;
  const isPreFixed = watchedIndexer === Indexer.PreFixado;
  const isEdit = Boolean(editHolding && editTransaction);
  const lastEditSeedRef = React.useRef<{ holdingId: string | null; transactionId: string | null } | null>(null);
  const currencyFormatter = React.useMemo(() => new Intl.NumberFormat(locale, { style: "currency", currency: "BRL" }), [locale]);

  const assetTypeTokens = React.useMemo(
    () => FIXED_INCOME_ASSET_TYPES.map(String).sort((a, b) => b.length - a.length),
    [],
  );

  const computedUnitPrice = React.useMemo(() => {
    if (typeof watchedQuantity !== "number" || !Number.isFinite(watchedQuantity) || watchedQuantity <= 0) return null;
    if (typeof watchedGrossValue !== "number" || !Number.isFinite(watchedGrossValue) || watchedGrossValue <= 0) return null;
    if (watchedQuantity <= 1) return null;
    return watchedGrossValue / watchedQuantity;
  }, [watchedGrossValue, watchedQuantity]);

  useEffect(() => {
    if (!open) {
      lastEditSeedRef.current = null;
      reset();
    }
  }, [open, reset]);

  // resolvedFinancialAccountId will be controlled by InvestmentRecordDrawer via onResolvedChange

  useEffect(() => {
    if (!open) return;
    if (!watchedAssetName) return;

    const matchedToken = assetTypeTokens.find((token) => new RegExp(`\\b${escapeRegExp(token)}\\b`).test(watchedAssetName));
    if (!matchedToken) return;

    const nextAssetType = matchedToken as AssetType;
    if (getValues("assetType") === nextAssetType) return;

    setValue("assetType", nextAssetType, { shouldDirty: true, shouldValidate: true });
  }, [assetTypeTokens, getValues, open, setValue, watchedAssetName]);

  useEffect(() => {
    if (watchedAssetType === AssetType.Treasury) {
      setValue("issuerName", "Tesouro Nacional", { shouldValidate: true });
    }

    setValue("isTaxExempt", TAX_EXEMPT_ASSET_TYPES.includes(watchedAssetType as (typeof TAX_EXEMPT_ASSET_TYPES)[number]));
  }, [watchedAssetType, setValue]);

  const [createAsset, { loading: creatingAsset, error: createAssetError }] = useMutation<
    CreateInvestmentAssetMutation,
    CreateInvestmentAssetMutationVariables
  >(CREATE_INVESTMENT_ASSET);

  const [recordTransaction, { loading: recordingTx, error: recordTxError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION);
  const [updateAsset, { loading: updatingAsset, error: updateAssetError }] = useMutation<
    UpdateInvestmentAssetMutationData,
    UpdateInvestmentAssetMutationVars
  >(UPDATE_INVESTMENT_ASSET);
  const [updateTransaction, { loading: updatingTx, error: updateTxError }] = useMutation<
    UpdateInvestmentTransactionMutationData,
    UpdateInvestmentTransactionMutationVars
  >(UPDATE_INVESTMENT_TRANSACTION);

  const completeRecording = React.useCallback(() => {
    success(t("investments.recordHolding.success"));
    onRecorded?.();
    onClose();

    void apolloClient
      .refetchQueries({
        include: [INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY, INVESTMENT_CASH_BALANCE, INVESTMENT_CASH_TRANSACTIONS],
      })
      .catch(() => undefined);
  }, [apolloClient, onClose, onRecorded, success, t]);

  const mutationError = createAssetError || recordTxError || updateAssetError || updateTxError;
  const isSubmitting = creatingAsset || recordingTx || updatingAsset || updatingTx;

  const assetTypeOptions = React.useMemo(
    () =>
      FIXED_INCOME_ASSET_TYPES.map((value) => ({
        value,
        label: t(`investments.recordHolding.assetTypes.${value}`),
      })),
    [t],
  );

  const indexerOptions = React.useMemo(
    () =>
      FIXED_INCOME_INDEXERS.map((value) => ({
        value,
        label: t(`investments.recordHolding.indexers.${value}`),
      })),
    [t],
  );

  const handleCouponFrequencyChange = (value: CouponFrequency | null) => {
    setValue("couponFrequency", value ?? CouponFrequency.AtMaturity, { shouldDirty: true, shouldValidate: true });
    if (value && value !== CouponFrequency.AtMaturity) {
      const current = methods.getValues("couponDayOfMonth");
      if (!current) {
        setValue("couponDayOfMonth", 15, { shouldDirty: true, shouldValidate: true });
      }
    } else {
      setValue("couponDayOfMonth", undefined, { shouldDirty: true, shouldValidate: true });
    }
  };

  const couponFrequencyOptions = React.useMemo(
    () => [
      { value: CouponFrequency.AtMaturity, label: t("investments.recordHolding.couponFrequencyOptions.AT_MATURITY") },
      { value: CouponFrequency.Monthly, label: t("investments.recordHolding.couponFrequencyOptions.MONTHLY") },
      { value: CouponFrequency.Quarterly, label: t("investments.recordHolding.couponFrequencyOptions.QUARTERLY") },
      { value: CouponFrequency.SemiAnnual, label: t("investments.recordHolding.couponFrequencyOptions.SEMI_ANNUAL") },
      { value: CouponFrequency.Annual, label: t("investments.recordHolding.couponFrequencyOptions.ANNUAL") },
    ],
    [t],
  );

  const onSubmit = async (data: RecordFixedIncomeFormData) => {
    if (!resolvedFinancialAccountId) return;
    if (!data.tradeDate || !data.maturityDate || !data.settlementDate) return;

    const tradeDate = formatDateOnly(data.tradeDate);
    const maturityDate = formatDateOnly(data.maturityDate);
    const quantity = typeof data.quantity === "number" && Number.isFinite(data.quantity) && data.quantity >= 0 ? data.quantity : 1;
    const unitPrice = quantity > 0 ? data.grossValue / quantity : data.grossValue;
    const txInput = {
      direction: isEdit ? editTransaction!.direction : InvestmentTransactionDirection.Inflow,
      type: isEdit ? editTransaction!.type : InvestmentTransactionType.Purchase,
      quantity,
      unitPrice,
      grossValue: data.grossValue,
      tradeDate,
      settlementDate: formatDateOnly(coerceInvestmentSettlementDate("fixedIncome", data.tradeDate, data.settlementDate)),
      indexer: data.indexer!,
      indexerPercentage: data.indexer !== Indexer.PreFixado ? data.indexerPercentage! / 100 : undefined,
      fixedAnnualRate: data.indexer === Indexer.PreFixado ? data.fixedAnnualRate! / 100 : undefined,
      issueDate: tradeDate,
      maturityDate,
      isTaxExempt: data.isTaxExempt,
      couponFrequency: data.couponFrequency,
      couponDayOfMonth: data.couponFrequency === CouponFrequency.AtMaturity ? undefined : data.couponDayOfMonth ?? 15,
    };

    const assetId = isEdit ? editHolding!.asset.id : null;
    const resolvedAssetId =
      assetId ??
      (await (async () => {
        const createdAsset = await createAsset({
          variables: {
            input: {
              name: data.assetName,
              assetType: data.assetType!,
              issuerName: data.issuerName,
              currencyCode: "BRL",
            },
          },
        });
        return createdAsset.data?.createInvestmentAsset.id ?? null;
      })());
    if (!resolvedAssetId) return;

    try {
      if (isEdit) {
        const assetChanged =
          data.assetName !== editHolding!.asset.name ||
          data.assetType !== editHolding!.asset.assetType ||
          data.issuerName !== (editHolding!.asset.issuerName ?? "");
        if (assetChanged) {
          await updateAsset({
            variables: {
              id: resolvedAssetId,
              input: {
                name: data.assetName,
                assetType: data.assetType!,
                issuerName: data.issuerName,
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

    reset(
      {
        assetName: editHolding!.asset.name ?? "",
        assetType: editHolding!.asset.assetType,
        issuerName: editHolding!.asset.issuerName ?? "",
        indexer: editHolding!.indexer ?? undefined,
        indexerPercentage:
          editHolding!.indexerPercentage != null && Number.isFinite(editHolding!.indexerPercentage)
            ? editHolding!.indexerPercentage * 100
            : undefined,
        fixedAnnualRate:
          editHolding!.fixedAnnualRate != null && Number.isFinite(editHolding!.fixedAnnualRate) ? editHolding!.fixedAnnualRate * 100 : undefined,
        quantity: editTransaction!.quantity ?? 1,
        grossValue: editTransaction!.grossValue,
        tradeDate:
          parseDateOnly(
            editTransaction!.tradeDate != null ? String(editTransaction!.tradeDate) : null,
          ) ?? undefined,
        settlementDate:
          parseDateOnly(
            editTransaction!.settlementDate != null ? String(editTransaction!.settlementDate) : null,
          ) ?? undefined,
        maturityDate:
          parseDateOnly(
            editHolding!.maturityDate != null ? String(editHolding!.maturityDate) : null,
          ) ?? undefined,
        couponDayOfMonth: editHolding!.couponDayOfMonth ?? undefined,
        isTaxExempt: editHolding!.isTaxExempt ?? false,
        couponFrequency: editHolding!.couponFrequency ?? CouponFrequency.AtMaturity,
      },
      { keepDirty: false },
    );
  }, [editHolding, editTransaction, isEdit, open, reset]);

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={t("investments.recordHolding.title")}
      initialFinancialAccountId={financialAccountId || null}
      financialAccounts={financialAccounts}
      issuerSelection={{
        description: t("investments.recordHolding.selectAccount.description"),
        emptyState: t("investments.recordHolding.selectAccount.emptyState"),
        dataTestIdPrefix: "record-select-account-",
      }}
      onResolvedChange={setResolvedFinancialAccountId}
    >
      {() => (
        <FormProvider {...methods}>
          <form id="record-fixed-income-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <FormStack>
              <ErrorAlert error={mutationError} />

                <TextField
                  name="assetName"
                  label={t("investments.recordHolding.fields.assetName")}
                size="medium"
                  data-testid="record-asset-name"
                />

              <SelectField
                name="assetType"
                label={t("investments.recordHolding.fields.assetType")}
                options={assetTypeOptions}
                data-testid="record-asset-type"
              />

              <TextField
                name="issuerName"
                label={t("investments.recordHolding.fields.issuerName")}
                size="medium"
                InputProps={isTreasury ? { readOnly: true } : undefined}
                data-testid="record-issuer-name"
              />

              <Typography variant="overline" color="text.secondary" sx={{ mb: 0 }}>
                {t("investments.recordHolding.sections.applicationDetails")}
              </Typography>

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <NumberField
                    name="quantity"
                    label={t("investments.recordHolding.fields.quantity")}
                    fullWidth
                    locale={locale}
                    min={0}
                    allowNegative={false}
                    data-testid="record-quantity"
                  />
                </Box>

                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="grossValue"
                    label={t("investments.recordHolding.fields.grossValue")}
                    currency="BRL"
                    data-testid="record-gross-value"
                    size="medium"
                  />
                </Box>
              </FormRowStack>

              {computedUnitPrice != null && (
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
                  data-testid="record-unit-price-info"
                >
                  <InfoOutlinedIcon fontSize="small" sx={{ color: "info.main" }} />
                  <Typography variant="body2" color="text.secondary">
                    {t("investments.recordHolding.values.unitPriceInfo", { unitPrice: currencyFormatter.format(computedUnitPrice) })}
                  </Typography>
                </Paper>
              )}

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <SelectField
                    name="indexer"
                    label={t("investments.recordHolding.fields.indexer")}
                    options={indexerOptions}
                    data-testid="record-indexer"
                  />
                </Box>

                <Box sx={{ flex: 1, minWidth: 0 }}>
                  {!isPreFixed ? (
                    <PercentField
                      name="indexerPercentage"
                      label={t("investments.recordHolding.fields.indexerPercentage")}
                      min={0}
                      max={1000}
                      step={0.01}
                      fractionDigits={2}
                      locale={locale}
                      data-testid="record-indexer-percentage"
                    />
                  ) : (
                    <PercentField
                      name="fixedAnnualRate"
                      label={t("investments.recordHolding.fields.fixedAnnualRate")}
                      min={0}
                      max={1000}
                      step={0.01}
                      fractionDigits={2}
                      locale={locale}
                      data-testid="record-pre-fixed-rate"
                    />
                  )}
                </Box>
              </FormRowStack>

              <InvestmentTradeAndSettlementDateRow<RecordFixedIncomeFormData>
                tradeDateLabel={t("investments.recordHolding.fields.tradeDate")}
                settlementDateLabel={t("investments.recordHolding.fields.settlementDate")}
                minDate={minAllowedDate.toDate()}
                tradeMaxDate={today.toDate()}
                tradeDateTestId="record-trade-date"
                settlementDateTestId="record-settlement-date"
              />

              <FormRowStack alignItems="flex-end">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <DatePickerField
                    name="maturityDate"
                    label={t("investments.recordHolding.fields.maturityDate")}
                    size="medium"
                    minDate={watchedTradeDate}
                    maxDate={maxMaturityDate.toDate()}
                    data-testid="record-maturity-date"
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <CheckboxField
                    name="isTaxExempt"
                    label={t("investments.recordHolding.fields.isTaxExempt")}
                    data-testid="record-tax-exempt-checkbox"
                  />
                </Box>
              </FormRowStack>

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <SelectField
                    name="couponFrequency"
                    label={t("investments.recordHolding.fields.couponFrequency")}
                    options={couponFrequencyOptions}
                    data-testid="record-coupon-frequency"
                    size="medium"
                    onChange={handleCouponFrequencyChange}
                  />
                </Box>

                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <NumberField
                    name="couponDayOfMonth"
                    label={t("investments.recordHolding.fields.couponDayOfMonth")}
                    fullWidth
                    size="medium"
                    min={1}
                    max={31}
                    disabled={isAtMaturity || isTreasury}
                    data-testid="record-coupon-day-of-month"
                  />
                </Box>
              </FormRowStack>

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.recordHolding.actions.submit")}
                submittingText={t("investments.recordHolding.actions.submitting")}
                onCancel={onClose}
                submitForm="record-fixed-income-form"
                cancelDisabled={isSubmitting}
                submitDisabled={isSubmitting || !resolvedFinancialAccountId}
                submitting={isSubmitting}
                submitTestId="record-fixed-income-submit-btn"
              />
            </FormStack>
          </form>
        </FormProvider>
      )}
    </InvestmentRecordDrawer>
  );
}
