"use client";

import React, { useEffect } from "react";
import { useMutation, useQuery } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Avatar,
  Box,
  CircularProgress,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import { Controller, FormProvider, useForm } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import { ALL_INVESTMENT_HOLDINGS, INVESTMENT_HOLDINGS } from "@/lib/graphql/operations/banking/queries";
import { INVESTMENT_FUND_NAV } from "@/lib/graphql/operations/financial-data/queries";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  AssetType,
  FinancialAccount,
  HoldingPricingMode,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { FormErrorBanner } from "@/shared/components/ui/forms/components/FormErrorBanner";
import { DatePickerField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { personalFinanceTranslations } from "../../i18n";
import { FundAutocomplete, FundAutocompleteValue } from "./FundAutocomplete";
import { mapCvmFundAssetType } from "@/lib/utils/mapCvmFundAssetType";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";

interface RecordFundHoldingDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: FinancialAccount[];
  onRecorded?: () => void;
}

type ListingMode = "CVM" | "MANUAL";
type ValueFlow = "BY_AMOUNT" | "BY_QUANTITY";

type RecordFundFormData = {
  fund: FundAutocompleteValue | null;
  manualFundName?: string;
  manualFundCnpj?: string;
  manualAssetType?: AssetType;
  quantity?: number | string;
  tradeDate?: Date;
  unitPrice?: number;
  grossValue?: number;
  manualCurrentGrossValue?: number;
  manualValuedAt?: Date;
};

function buildDefaultValues(): RecordFundFormData {
  return {
    fund: null,
    manualFundName: "",
    manualFundCnpj: "",
    manualAssetType: AssetType.MutualFund,
    quantity: undefined,
    tradeDate: undefined,
    unitPrice: undefined,
    grossValue: undefined,
    manualCurrentGrossValue: undefined,
    manualValuedAt: new Date(),
  };
}

export function RecordFundHoldingDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
}: RecordFundHoldingDrawerProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(
    () => getLocaleSeparators(locale),
    [locale],
  );
  const [resolvedFinancialAccountId, setResolvedFinancialAccountId] = React.useState<string | null>(null);
  const [valueFlow, setValueFlow] = React.useState<ValueFlow>("BY_AMOUNT");
  const [listingMode, setListingMode] = React.useState<ListingMode>("CVM");

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
        tradeDate: z.preprocess(
          (value) => (value === "" || value === null || value === undefined ? undefined : value),
          z.date().optional(),
        ),
        manualValuedAt: z.preprocess(
          (value) => (value === "" || value === null || value === undefined ? undefined : value),
          z.date().optional(),
        ),
      }),
    [parseLocalizedNumber],
  );

  const methods = useForm<RecordFundFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: buildDefaultValues(),
  });

  const { reset, clearErrors, setError } = methods;
  const { dirtyFields } = methods.formState;
  const selectedFund = methods.watch("fund");
  const selectedTradeDate = methods.watch("tradeDate");
  const unitPriceValue = methods.watch("unitPrice");
  const grossValueValue = methods.watch("grossValue");
  const quantityValue = methods.watch("quantity");
  const tradeDateIso = React.useMemo(
    () => (selectedTradeDate ? dayjs(selectedTradeDate).format("YYYY-MM-DD") : null),
    [selectedTradeDate],
  );

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
    if (dirtyFields.unitPrice) return;
    if (typeof unitPriceValue === "number" && unitPriceValue > 0) return;
    methods.setValue("unitPrice", suggestedUnitPrice, { shouldDirty: false, shouldTouch: true, shouldValidate: true });
  }, [dirtyFields.unitPrice, methods, open, suggestedUnitPrice, unitPriceValue]);

  useEffect(() => {
    if (!open) {
      reset(buildDefaultValues());
      setValueFlow("BY_AMOUNT");
      setListingMode("CVM");
    }
  }, [open, reset]);

  useEffect(() => {
    if (!open) return;
    setResolvedFinancialAccountId(financialAccountId || null);
  }, [financialAccountId, open]);

  useEffect(() => {
    clearErrors();
    methods.setValue("fund", null, { shouldDirty: false, shouldValidate: false });
    methods.setValue("unitPrice", undefined, { shouldDirty: false, shouldValidate: false });
  }, [clearErrors, listingMode, methods]);

  const [createAsset, { loading: creatingAsset, error: createAssetError }] = useMutation<
    CreateInvestmentAssetMutation,
    CreateInvestmentAssetMutationVariables
  >(CREATE_INVESTMENT_ASSET);

  const [recordTransaction, { loading: recordingTx, error: recordTxError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION, {
    refetchQueries: [
      { query: INVESTMENT_HOLDINGS, variables: { financialAccountId: resolvedFinancialAccountId || "" } },
      { query: ALL_INVESTMENT_HOLDINGS },
    ],
    onCompleted: () => {
      success(t("investments.recordFund.success"));
      onRecorded?.();
      onClose();
    },
  });

  const isSubmitting = creatingAsset || recordingTx;
  const mutationError = createAssetError || recordTxError;

  const currencyFormatter = React.useMemo(
    () => new Intl.NumberFormat(locale, { style: "currency", currency: "BRL" }),
    [locale],
  );

  const computedQuantityByAmount = React.useMemo(() => {
    const unitPrice = typeof unitPriceValue === "number" ? unitPriceValue : null;
    const grossValue = typeof grossValueValue === "number" ? grossValueValue : null;
    if (!unitPrice || !grossValue) return null;
    if (unitPrice <= 0 || grossValue <= 0) return null;
    return grossValue / unitPrice;
  }, [grossValueValue, unitPriceValue]);

  const computedTotalByQuantity = React.useMemo(() => {
    const unitPrice = typeof unitPriceValue === "number" ? unitPriceValue : null;
    const quantity = typeof quantityValue === "number" ? quantityValue : null;
    if (!unitPrice || quantity == null) return null;
    if (!Number.isFinite(quantity)) return null;
    if (unitPrice <= 0 || quantity <= 0) return null;
    return unitPrice * quantity;
  }, [quantityValue, unitPriceValue]);

  const onSubmit = async (data: RecordFundFormData) => {
    if (!resolvedFinancialAccountId) return;
    if (!data.tradeDate) {
      setError("tradeDate", { type: "required", message: t("investments.recordFund.validation.tradeDateRequired") });
      return;
    }

    const isManual = listingMode === "MANUAL";
    if (!isManual && !data.fund) {
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

    const tradeDate = dayjs(data.tradeDate).format("YYYY-MM-DD");
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

    const assetId = createdAsset.data?.createInvestmentAsset.id;
    if (!assetId) return;

    await recordTransaction({
      variables: {
        input: {
          financialAccountId: resolvedFinancialAccountId,
          assetId,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Purchase,
          quantity,
          unitPrice,
          grossValue,
          tradeDate,
          settlementDate: tradeDate,
          pricingMode: isManual ? HoldingPricingMode.Manual : HoldingPricingMode.MarketData,
          manualValuation: isManual && data.manualCurrentGrossValue && data.manualValuedAt
            ? {
                grossValue: data.manualCurrentGrossValue,
                valuedAt: dayjs(data.manualValuedAt).format("YYYY-MM-DD"),
              }
            : undefined,
        },
      },
    });
  };

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
      {({ resolvedFinancialAccountId }) => (
        <FormProvider {...methods}>
          <form id="record-fund-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <Stack spacing={2}>
              {mutationError && <FormErrorBanner message={mutationError.message} />}

                <Stack spacing={1}>
                  <Typography variant="overline" color="text.secondary">
                    {t("investments.recordFund.sections.fund")}
                  </Typography>
                  <SegmentedControl
                    fullWidth
                    value={listingMode}
                    onChange={(v) => setListingMode(v as ListingMode)}
                    options={[
                      { value: "CVM", label: t("investments.recordFund.modes.cvmListed") },
                      { value: "MANUAL", label: t("investments.recordFund.modes.notListed") },
                    ]}
                  />
                </Stack>

                {listingMode === "CVM" ? (
                  <Stack spacing={1}>
                    <Typography variant="overline" color="text.secondary">
                      {t("investments.recordFund.sections.applicationData")}
                    </Typography>
                    <FundAutocomplete name="fund" data-testid="record-fund-autocomplete" />
                  </Stack>
                ) : (
                  <Stack spacing={2}>
                    <Typography variant="overline" color="text.secondary">
                      {t("investments.recordFund.sections.manualFund")}
                    </Typography>
                    <Controller
                      name="manualFundName"
                      control={methods.control}
                      render={({ field, fieldState }) => (
                        <TextField
                          {...field}
                          label={t("investments.recordFund.fields.manualFundName")}
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
                          error={!!fieldState.error}
                          helperText={fieldState.error?.message ?? t("investments.recordFund.fields.manualFundCnpjHelper")}
                          data-testid="record-fund-manual-cnpj"
                        />
                      )}
                    />
                    <Stack spacing={1}>
                      <Typography variant="caption" color="text.secondary">
                        {t("investments.recordFund.fields.manualAssetType")}
                      </Typography>
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
                    </Stack>
                  </Stack>
                )}

                <DatePickerField
                  name="tradeDate"
                  label={t("investments.recordFund.fields.tradeDate")}
                  data-testid="record-fund-trade-date"
                />

                <Divider />

                <Stack spacing={1}>
                  <Typography variant="overline" color="text.secondary">
                    {t("investments.recordFund.sections.values")}
                  </Typography>
                  <SegmentedControl
                    fullWidth
                    value={valueFlow}
                    onChange={(v) => setValueFlow(v as ValueFlow)}
                    options={[
                      { value: "BY_AMOUNT", label: t("investments.recordFund.flows.byAmount") },
                      { value: "BY_QUANTITY", label: t("investments.recordFund.flows.byQuantity") },
                    ]}
                  />
                </Stack>

                {valueFlow === "BY_AMOUNT" ? (
                  <Stack spacing={2}>
                    <MoneyInput
                      name="grossValue"
                      label={t("investments.recordFund.fields.grossValue")}
                      currency="BRL"
                      data-testid="record-fund-gross-value"
                    />

                    {listingMode !== "CVM" || !suggestedUnitPrice ? (
                      <MoneyInput
                        name="unitPrice"
                        label={t("investments.recordFund.fields.unitPrice")}
                        currency="BRL"
                        data-testid="record-fund-unit-price"
                      />
                    ) : null}

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
                      <Box sx={{ display: "flex", flexDirection: "column", gap: 0.25, minWidth: 0 }}>
                        <Typography variant="body2" color="text.secondary">
                          {listingMode === "CVM"
                            ? t("investments.recordFund.values.byAmount.navLabel")
                            : t("investments.recordFund.values.byAmount.unitPriceLabel")}
                        </Typography>
                        <Typography variant="body2" fontWeight={700}>
                          {typeof unitPriceValue === "number" ? currencyFormatter.format(unitPriceValue) : "—"}
                        </Typography>
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
                        {computedQuantityByAmount != null
                          ? t("investments.recordFund.values.byAmount.estimatedShares", {
                              shares: computedQuantityByAmount.toLocaleString(locale, {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2,
                              }),
                            })
                          : "—"}
                      </Typography>
                    </Paper>
                  </Stack>
                ) : (
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
                        label={t("investments.recordFund.fields.quantity")}
                        fullWidth
                        locale={locale}
                        fractionDigits={2}
                        allowNegative={false}
                        data-testid="record-fund-quantity"
                      />

                      <MoneyInput
                        name="unitPrice"
                        label={t("investments.recordFund.fields.unitPrice")}
                        currency="BRL"
                        data-testid="record-fund-unit-price"
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
                        {t("investments.recordFund.values.byQuantity.totalEstimatedLabel")}
                      </Typography>
                      <Typography variant="body2" fontWeight={800}>
                        {computedTotalByQuantity != null ? currencyFormatter.format(computedTotalByQuantity) : "—"}
                      </Typography>
                    </Paper>
                  </Stack>
                )}

                {listingMode === "MANUAL" ? (
                  <>
                    <Divider />
                    <Stack spacing={2}>
                      <Typography variant="overline" color="text.secondary">
                        {t("investments.recordFund.sections.currentValue")}
                      </Typography>
                      <MoneyInput
                        name="manualCurrentGrossValue"
                        label={t("investments.recordFund.fields.manualCurrentGrossValue")}
                        currency="BRL"
                        data-testid="record-fund-manual-current-gross-value"
                      />
                      <DatePickerField
                        name="manualValuedAt"
                        label={t("investments.recordFund.fields.manualValuedAt")}
                        data-testid="record-fund-manual-valued-at"
                      />
                    </Stack>
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
                  submitDisabled={isSubmitting || !resolvedFinancialAccountId}
                  submitting={isSubmitting}
                />

            </Stack>
          </form>
        </FormProvider>
      )}
    </InvestmentRecordDrawer>
  );
}
