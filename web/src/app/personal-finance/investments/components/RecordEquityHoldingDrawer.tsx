"use client";

import React, { useEffect } from "react";
import { useMutation, useQuery } from "@apollo/client/react";
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
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import { ALL_INVESTMENT_HOLDINGS, INVESTMENT_HOLDINGS } from "@/lib/graphql/operations/banking/queries";
import { INVESTMENT_ASSET_PRICE } from "@/lib/graphql/operations/financial-data/queries";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import { FinancialAccount, InvestmentTransactionDirection, InvestmentTransactionType } from "@/lib/graphql/types/__generated__/graphql";
import { FormErrorBanner } from "@/shared/components/ui/forms/components/FormErrorBanner";
import { DatePickerField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { personalFinanceTranslations } from "../../i18n";
import { EquityAutocomplete, EquityAutocompleteValue } from "./EquityAutocomplete";
import { mapB3AssetType } from "@/lib/utils/mapB3AssetType";

interface RecordEquityHoldingDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: FinancialAccount[];
  onRecorded?: () => void;
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

export function RecordEquityHoldingDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
}: RecordEquityHoldingDrawerProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(
    () => getLocaleSeparators(locale),
    [locale],
  );
  const [resolvedFinancialAccountId, setResolvedFinancialAccountId] = React.useState<string | null>(null);
  const needsAccountSelection = !resolvedFinancialAccountId;
  const [valueFlow, setValueFlow] = React.useState<"BY_AMOUNT" | "BY_QUANTITY">("BY_QUANTITY");
  const [feesMode, setFeesMode] = React.useState<"SUMMARY" | "DETAILED">("SUMMARY");

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
      }),
    [localeDecimal, localeGroup, t],
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
      reset();
      setValueFlow("BY_QUANTITY");
      setFeesMode("SUMMARY");
    }
  }, [open, reset]);

  useEffect(() => {
    if (!open) return;
    setResolvedFinancialAccountId(financialAccountId || null);
  }, [financialAccountId, open]);

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
      success(t("investments.recordEquity.success"));
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
    if (!data.asset) return;
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

    const createdAsset = await createAsset({
      variables: {
        input: {
          name: data.asset.name,
          assetType: mapB3AssetType(data.asset.assetType),
          issuerName: data.asset.ticker,
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
          settlementDate: dayjs(data.tradeDate).add(2, "day").format("YYYY-MM-DD"),
        },
      },
    });
  };

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.recordEquity.title")} />
      <Drawer.Body>
        {needsAccountSelection ? (
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {t("investments.recordEquity.selectAccount.description")}
            </Typography>

            {financialAccounts.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t("investments.recordEquity.selectAccount.emptyState")}
              </Typography>
            ) : (
              <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden" }}>
                <List disablePadding>
                  {financialAccounts.map((account) => (
                    <ListItemButton
                      key={account.id}
                      onClick={() => setResolvedFinancialAccountId(account.id)}
                      sx={{
                        py: 1.5,
                        "&:not(:last-of-type)": { borderBottom: `1px solid ${alpha(theme.palette.divider, 0.6)}` },
                      }}
                      data-testid={`record-equity-select-account-${account.id}`}
                    >
                      <ListItemIcon sx={{ minWidth: 44 }}>
                        <Avatar
                          src={account.issuer?.logoAsset?.publicUrl || undefined}
                          sx={{
                            bgcolor: account.issuer?.logoAsset?.publicUrl ? "transparent" : (account.themeColor || "primary.main"),
                            width: 32,
                            height: 32,
                            borderRadius: 2,
                          }}
                        >
                          {account.name?.slice(0, 1).toUpperCase()}
                        </Avatar>
                      </ListItemIcon>
                      <ListItemText
                        primary={
                          <Typography variant="body2" fontWeight={700} noWrap>
                            {account.name}
                          </Typography>
                        }
                        secondary={
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {account.issuer?.name || account.issuerName}
                          </Typography>
                        }
                      />
                    </ListItemButton>
                  ))}
                </List>
              </Paper>
            )}
          </Stack>
        ) : (
          <FormProvider {...methods}>
            <form id="record-equity-form" onSubmit={methods.handleSubmit(onSubmit)}>
              <Stack spacing={2}>
                {mutationError && <FormErrorBanner message={mutationError.message} />}

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
                    <EquityAutocomplete name="asset" data-testid="record-equity-autocomplete" />
                    <DatePickerField
                      name="tradeDate"
                      label={t("investments.recordEquity.fields.tradeDate")}
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
      </Drawer.Body>

      {!needsAccountSelection ? (
        <Drawer.Footer>
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
        </Drawer.Footer>
      ) : null}
    </Drawer>
  );
}
