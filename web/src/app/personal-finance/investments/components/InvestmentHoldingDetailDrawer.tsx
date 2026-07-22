"use client";

import * as React from "react";
import AddIcon from "@mui/icons-material/Add";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Skeleton,
  Tab,
  Tabs,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { useApolloClient, useMutation } from "@apollo/client/react";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { AssetType } from "@/lib/graphql/types/__generated__/graphql";
import { UPDATE_FUND_HOLDING_PARAMS } from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import { Drawer, DrawerDeleteButton } from "@/shared/components/ui/layout/Drawer";
import { ConfirmationDialog } from "@/shared/components/ui/feedback/dialogs/ConfirmationDialog";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { CurrencyAmountParts, GainPercent } from "@/shared/components/ui/data-display";
import { personalFinanceTranslations } from "../../i18n";
import { CloseIcon } from "@/shared/ui/mui-imports";
import { FixedIncomeHoldingDetailContent } from "./fixed-income/FixedIncomeHoldingDetailContent";
import { useHoldingDetailDrawer } from "./useHoldingDetailDrawer";
import { buildFixedIncomeHoldingDetailViewModel } from "./fixed-income/fixedIncomeHoldingDetailAdapter";
import { MaturityConfirmDrawer } from "../MaturityConfirmDrawer";
import { CouponPaymentConfirmDrawer } from "../CouponPaymentConfirmDrawer";
import { FundHoldingDetailContent } from "./fund/FundHoldingDetailContent";
import { buildFundHoldingDetailViewModel } from "./fund/fundHoldingDetailAdapter";
import type { FundHoldingDetailQuery } from "./fund/api/queries.generated";
import { getAssetGroupKey } from "./table/investmentTableHelpers";
import type { HoldingRow } from "./table/investmentTableHelpers";
import { HoldingDetailHeader } from "./HoldingDetailHeader";
import type { HoldingDetailHeaderModel } from "./holdingDetailHeaderPresenter";
import { formatFundAssetType, type CvmFundAssetType } from "@/lib/utils/mapCvmFundAssetType";
import {
  mapAssetTypeToTransactionEditorClass,
  mapHoldingToResolvedAsset,
  type EditIntent,
  type RowBuyIntent,
} from "./transaction-editor/intent";
import { ManagementHeaderActionButton } from "@/shared/components/management/ManagementHeaderActions";
import { MovimentacoesTab } from "./InvestmentHoldingDetailDrawer/MovimentacoesTab";
import { FormStack } from "@/shared/components/ui/forms/components/FormStack";
import { PercentField } from "@/shared/components/ui/forms/form";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { useMutationWithRefetch } from "@/shared/hooks/mutations";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";

export type InvestmentHoldingDetailKind = "fixedIncome" | "fund" | "equity";
type DetailTab = "summary" | "transactions" | "parameters";

const DAILY_PRICED_ASSET_TYPES = new Set<AssetType>([
  AssetType.Stock,
  AssetType.StockRights,
  AssetType.Bdr,
  AssetType.RealEstateFund,
  AssetType.AgribusinessFund,
  AssetType.Etf,
  AssetType.EtfInternational,
  AssetType.MutualFund,
  AssetType.Crypto,
]);

interface InvestmentHoldingDetailDrawerProps {
  open: boolean;
  holdingId: string | null;
  kind: InvestmentHoldingDetailKind | null;
  initialTab?: DetailTab;
  refreshNonce?: number;
  initialTitle?: string | null;
  initialAssetType?: string | null;
  initialLogoUrl?: string | null;
  initialIssuerLogoUrl?: string | null;
  onOpenTransactionEditor: (intent: RowBuyIntent | EditIntent) => void;
  onClose: () => void;
}

interface HoldingDetailSummaryCardViewModel {
  id: string;
  label: string;
  value: number | null;
  /** default: monetary amount; percent: annualized IRR / XIRR; number: quantity / units */
  valueKind?: "currency" | "percent" | "number" | "percentagePoints";
  currencyCode?: string;
  /** Shown under the main value (e.g. period start for TIR). */
  caption?: string | null;
  labelTooltip?: string;
}

interface HoldingDetailStrategy {
  headerModel: HoldingDetailHeaderModel;
  headerActions: React.ReactNode;
  footerActions: React.ReactNode;
  body: React.ReactNode;
  extra: React.ReactNode;
  assetName: string;
  logoUrl: string | undefined;
  grossValue: number | null;
  currencyCode: string;
  gainPercent: number | null;
  summaryCards: HoldingDetailSummaryCardViewModel[];
}

interface HoldingDetailDrawerShellProps {
  open: boolean;
  onClose: () => void;
  isMobile: boolean;
  locale: string;
  title: string;
  closeAriaLabel: string;
  headerActions: React.ReactNode;
  footerActions: React.ReactNode;
  headerModel: HoldingDetailHeaderModel;
  assetName: string;
  logoUrl: string | undefined;
  priceReferenceLabel: string | null;
  priceReferenceValue: number | null;
  priceReferenceCurrencyCode: string;
  totalValueLabel: string;
  totalValue: number | null;
  currencyCode: string;
  gainPercent: number | null;
  activeTab: DetailTab;
  onTabChange: (tab: DetailTab) => void;
  summaryTabLabel: string;
  transactionsTabLabel: string;
  parametersTabLabel?: string | null;
  /** Shown on the right of the tab row when the transactions tab is active (e.g. add transaction). */
  transactionsTabAction?: React.ReactNode;
  summaryCards: HoldingDetailSummaryCardViewModel[];
  body: React.ReactNode;
  transactionsContent: React.ReactNode;
  parametersContent?: React.ReactNode;
}

function formatDetailQuantityValue(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    maximumFractionDigits: 8,
    minimumFractionDigits: 0,
    useGrouping: true,
  }).format(value);
}

function formatLocalDate(
  value: unknown,
  locale: string,
  options: Intl.DateTimeFormatOptions,
): string {
  if (!value) return "—";
  const raw = String(value);
  const localDateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (localDateMatch) {
    const [, year, month, day] = localDateMatch;
    const date = new Date(Number(year), Number(month) - 1, Number(day));
    return date.toLocaleDateString(locale, options);
  }
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString(locale, options);
}

function HoldingDetailDrawerShell({
  open,
  onClose,
  isMobile,
  locale,
  title,
  closeAriaLabel,
  headerActions,
  footerActions,
  headerModel,
  assetName,
  logoUrl,
  priceReferenceLabel,
  priceReferenceValue,
  priceReferenceCurrencyCode,
  totalValueLabel,
  totalValue,
  currencyCode,
  gainPercent,
  activeTab,
  onTabChange,
  summaryTabLabel,
  transactionsTabLabel,
  parametersTabLabel,
  transactionsTabAction,
  summaryCards,
  body,
  transactionsContent,
  parametersContent,
}: HoldingDetailDrawerShellProps) {
  const metricCards = React.useMemo(() => {
    if (!priceReferenceLabel) return summaryCards;
    return [
      {
        id: "priceReference",
        label: priceReferenceLabel,
        value: priceReferenceValue,
        valueKind: "currency" as const,
        currencyCode: priceReferenceCurrencyCode,
      },
      ...summaryCards,
    ];
  }, [priceReferenceCurrencyCode, priceReferenceLabel, priceReferenceValue, summaryCards]);

  return (
    <Drawer
      data-testid="investment-holding-detail-drawer"
      open={open}
      onClose={onClose}
      anchor="right"
      size={isMobile ? undefined : "lg"}
      width={isMobile ? "100%" : undefined}
    >
      <Drawer.Header>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", gap: 2 }}>
          <Box sx={{ display: "flex", flexDirection: "column", minWidth: 0 }}>
            <Typography variant="h5" fontWeight={400} noWrap sx={{ letterSpacing: "0.05em", lineHeight: 1.15, color: "action.active" }}>
              {title.toLocaleUpperCase(locale)}
            </Typography>
          </Box>

          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 1, minWidth: 0 }}>
            {headerActions}
            <IconButton onClick={onClose} size="small" aria-label={closeAriaLabel}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>
      </Drawer.Header>

      <Drawer.Body
        sx={{
          p: 3,
          px: { xs: 2, md: 3 },
          pb: 4,
        }}
      >
        <HoldingDetailHeader
          model={{
            ...headerModel,
            assetName,
            logoUrl,
            metrics: {
              ...headerModel.metrics,
              totalValueLabel,
              totalValue,
              currencyCode,
              gainPercent,
              locale,
            },
          }}
        />

        <Box
          sx={{
            mt: 2,
            mb: 2,
            borderBottom: 1,
            borderColor: "divider",
          }}
        >
          <Box
            sx={{
              display: "flex",
              alignItems: "flex-end",
              justifyContent: "space-between",
              gap: 1.5,
            }}
          >
            <Tabs
              value={activeTab}
              onChange={(_event, tab) => onTabChange(tab as DetailTab)}
              aria-label="investment-holding-detail-tabs"
              sx={{ flex: 1, minWidth: 0 }}
            >
              <Tab value="summary" label={summaryTabLabel} />
              <Tab value="transactions" label={transactionsTabLabel} />
              {parametersTabLabel ? <Tab value="parameters" label={parametersTabLabel} /> : null}
            </Tabs>
            {activeTab === "transactions" && transactionsTabAction ? (
              <Box sx={{ flexShrink: 0, alignSelf: "center", transform: "translateY(-2px)" }}>
                {transactionsTabAction}
              </Box>
            ) : null}
          </Box>
        </Box>

        {activeTab === "summary" ? (
          <>
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "repeat(2, minmax(0, 1fr))", md: "repeat(3, minmax(0, 1fr))" },
                gap: 1.5,
                mb: 2,
              }}
            >
              {metricCards.map((card) => (
                <Box key={card.id} sx={{ p: 1.5, border: "1px solid", borderColor: "divider", borderRadius: 1, minWidth: 0 }}>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 0.25 }}>
                    <Label sx={{ mb: 0 }}>{card.label}</Label>
                    {card.labelTooltip ? (
                      <Tooltip title={card.labelTooltip}>
                        <InfoOutlinedIcon sx={{ fontSize: 14, color: "text.secondary" }} aria-hidden />
                      </Tooltip>
                    ) : null}
                  </Box>
                  {card.valueKind === "percent" ? (
                    <>
                      {card.value != null && Number.isFinite(card.value) ? (
                        <GainPercent
                          value={card.value}
                          locale={locale}
                          variant="h6"
                          iconSize={14}
                          textSx={{ fontWeight: 500, fontSize: { xs: "1.05rem", md: "1.15rem" }, lineHeight: 1.1 }}
                        />
                      ) : (
                        <Typography variant="h6" fontWeight={500} sx={{ lineHeight: 1.1, fontSize: { xs: "1.05rem", md: "1.15rem" } }}>
                          —
                        </Typography>
                      )}
                      {card.caption ? (
                        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: "block" }}>
                          {card.caption}
                        </Typography>
                      ) : null}
                    </>
                  ) : card.valueKind === "percentagePoints" ? (
                    <Typography variant="h6" fontWeight={500} sx={{ lineHeight: 1.1, fontSize: { xs: "1.05rem", md: "1.15rem" } }} noWrap>
                      {card.value != null && Number.isFinite(card.value)
                        ? new Intl.NumberFormat(locale, {
                            minimumFractionDigits: 0,
                            maximumFractionDigits: 2,
                          }).format(card.value) + "%"
                        : "—"}
                    </Typography>
                  ) : card.valueKind === "number" ? (
                    <Typography variant="h6" fontWeight={500} sx={{ lineHeight: 1.1, fontSize: { xs: "1.05rem", md: "1.15rem" } }} noWrap>
                      {card.value != null && Number.isFinite(card.value) ? formatDetailQuantityValue(card.value, locale) : "—"}
                    </Typography>
                  ) : (
                    <CurrencyAmountParts
                      value={card.value}
                      currency={card.currencyCode ?? currencyCode}
                      locale={locale}
                      variant="h6"
                      fontWeight={500}
                      noWrap
                      sx={{ lineHeight: 1.1, fontSize: { xs: "1.05rem", md: "1.15rem" } }}
                    />
                  )}
                </Box>
              ))}
            </Box>

            <Divider sx={{ my: 5 }} />

            {body}
          </>
        ) : (
          activeTab === "transactions" ? transactionsContent : parametersContent
        )}
      </Drawer.Body>

      {footerActions ? (
        <Drawer.Footer>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "flex-start", gap: 2 }}>
            {footerActions}
          </Box>
        </Drawer.Footer>
      ) : null}
    </Drawer>
  );
}

type FundParametersFormData = {
  administrationFeeAnnualPercent?: number;
  comeCotasMode: "default" | "enabled" | "disabled";
};

export function buildUpdateFundHoldingParamsInput(
  holdingId: string,
  values: FundParametersFormData,
) {
  return {
    input: {
      holdingId,
      administrationFeeAnnualPercent:
        values.administrationFeeAnnualPercent == null || values.administrationFeeAnnualPercent === 0
          ? null
          : values.administrationFeeAnnualPercent,
      comeCotasEnabled:
        values.comeCotasMode === "default"
          ? null
          : values.comeCotasMode === "enabled",
    },
  };
}

interface FundParametersTabProps {
  holding: NonNullable<FundHoldingDetailQuery["investmentHolding"]>;
  locale: string;
  onSaved: () => Promise<unknown>;
}

export function FundParametersTab({ holding, locale, onSaved }: FundParametersTabProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const apolloClient = useApolloClient();
  const methods = useForm<FundParametersFormData>({
    defaultValues: {
      administrationFeeAnnualPercent: holding.administrationFeeAnnualPercent ?? undefined,
      comeCotasMode:
        holding.comeCotasEnabled == null
          ? "default"
          : holding.comeCotasEnabled
            ? "enabled"
            : "disabled",
    },
  });
  const [updateFundHoldingParamsMutation, { loading }] = useMutation(UPDATE_FUND_HOLDING_PARAMS);
  const comeCotasMode = useWatch({ control: methods.control, name: "comeCotasMode" });
  const { executeMutation } = useMutationWithRefetch({
    onRefetch: () => {
      void onSaved();
      void apolloClient.refetchQueries({
        include: [
          INVESTMENT_PORTFOLIO_SUMMARY,
          INVESTMENT_CASH_BALANCE,
          INVESTMENT_CASH_TRANSACTIONS,
        ],
      });
    },
    errorMessage: t("investments.detailDrawer.parameters.updateError"),
  });

  React.useEffect(() => {
    methods.reset({
      administrationFeeAnnualPercent: holding.administrationFeeAnnualPercent ?? undefined,
      comeCotasMode:
        holding.comeCotasEnabled == null
          ? "default"
          : holding.comeCotasEnabled
            ? "enabled"
            : "disabled",
    });
  }, [holding.administrationFeeAnnualPercent, holding.comeCotasEnabled, methods]);

  const handleSubmit = methods.handleSubmit(async (values) => {
    try {
      await executeMutation(
        updateFundHoldingParamsMutation,
        buildUpdateFundHoldingParamsInput(holding.id, values),
        holding.id,
      );
      toast.success(t("investments.detailDrawer.parameters.updateSuccess"));
    } catch (error) {
      toast.error(extractErrorMessage(error, t("investments.detailDrawer.parameters.updateError")));
    }
  });

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit}>
        <FormStack spacing={2}>
          <PercentField
            name="administrationFeeAnnualPercent"
            label={t("investments.detailDrawer.parameters.administrationFeeAnnualPercent")}
            locale={locale}
            fractionDigits={2}
            data-testid="fund-parameters-administration-fee"
          />
          <FormStack spacing={0.5}>
            <Label sx={{ mb: 0 }}>{t("investments.detailDrawer.comeCotas.label")}</Label>
            <SegmentedControl
              fullWidth
              value={comeCotasMode}
              onChange={(value) => methods.setValue("comeCotasMode", value as FundParametersFormData["comeCotasMode"])}
              options={[
                { value: "default", label: t("investments.detailDrawer.comeCotas.options.default") },
                { value: "enabled", label: t("investments.detailDrawer.comeCotas.options.enabled") },
                { value: "disabled", label: t("investments.detailDrawer.comeCotas.options.disabled") },
              ]}
            />
            <Typography variant="body2" color="text.secondary">
              {t("investments.detailDrawer.comeCotas.description")}
            </Typography>
          </FormStack>
          <Box sx={{ display: "flex", justifyContent: "flex-start" }}>
            <Button type="submit" variant="contained" disabled={loading}>
              {t("actions.save")}
            </Button>
          </Box>
        </FormStack>
      </form>
    </FormProvider>
  );
}

export function InvestmentHoldingDetailDrawer({
  open,
  holdingId,
  kind,
  initialTab = "summary",
  refreshNonce = 0,
  initialTitle,
  initialAssetType,
  initialLogoUrl,
  initialIssuerLogoUrl,
  onOpenTransactionEditor,
  onClose,
}: InvestmentHoldingDetailDrawerProps) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const { t } = useTranslation(personalFinanceTranslations);
  const {
    fixedIncomeData,
    fundData,
    fixedIncomeLoading,
    fundLoading,
    fixedIncomeError,
    fundError,
    refetchFixedIncome,
    refetchFund,
    maturityOpen: maturityConfirmOpen,
    maturityHolding,
    closeMaturity,
    couponOpen: couponConfirmOpen,
    couponHolding: couponConfirmHolding,
    couponEditingTransaction,
    couponDestinationAccount,
    closeCoupon,
    openCoupon: handleRegisterOverdueCoupon,
    editCouponPayment: handleEditCouponPayment,
    deleteCouponPayment: handleDeleteCouponPayment,
    deletingCouponPayment,
    deletingTransactionId,
    deleteConfirmOpen,
    deleteInProgress,
    deletingTransaction,
    openDeleteTransaction: handleRequestDeleteTransaction,
    closeDeleteConfirm: handleCloseDeleteDialog,
    confirmDeleteTransaction: handleConfirmDeleteTransaction,
    holdingRemoveConfirmOpen,
    openHoldingRemoveConfirm,
    closeHoldingRemoveConfirm,
    confirmRemoveHolding,
    deletingHolding,
    performancePeriod: fundPerformancePeriod,
    setPerformancePeriod: setFundPerformancePeriod,
    locale,
    actionHoldingRow,
  } = useHoldingDetailDrawer({
    open,
    holdingId,
    kind,
    onClose,
  });
  const [activeTab, setActiveTab] = React.useState<DetailTab>(initialTab);

  React.useEffect(() => {
    if (!open) return;
    setActiveTab(initialTab);
  }, [initialTab, open, holdingId]);

  React.useEffect(() => {
    if (!open || !holdingId || refreshNonce === 0) return;
    if (kind === "fixedIncome") {
      void refetchFixedIncome();
      return;
    }
    if (kind === "fund" || kind === "equity") {
      void refetchFund();
    }
  }, [kind, holdingId, open, refreshNonce, refetchFixedIncome, refetchFund]);

  const fixedIncomeQuery = React.useMemo(
    () => ({ loading: fixedIncomeLoading, error: fixedIncomeError, refetch: refetchFixedIncome }),
    [fixedIncomeError, fixedIncomeLoading, refetchFixedIncome],
  );
  const fundQuery = React.useMemo(
    () => ({ loading: fundLoading, error: fundError, refetch: refetchFund }),
    [fundError, fundLoading, refetchFund],
  );

  const resolvedAssetType =
    (kind === "fixedIncome"
      ? fixedIncomeData?.investmentHolding?.asset.assetType
      : kind === "fund" || kind === "equity"
      ? fundData?.investmentHolding?.asset.assetType
      : null) ||
    initialAssetType ||
    null;

  const groupKey = getAssetGroupKey(resolvedAssetType);
  const groupDisplayKey = groupKey;

  const title =
    resolvedAssetType != null
      ? t(`investments.assetGroups.${groupDisplayKey}`)
      : t("investments.detailDrawer.title");

  const priceReference = React.useMemo(() => {
    if (kind !== "fund" && kind !== "equity") return null;
    const holding = fundData?.investmentHolding;
    const assetType = holding?.asset.assetType;
    if (!holding || assetType == null || !DAILY_PRICED_ASSET_TYPES.has(assetType)) return null;
    const quantity = holding.quantity;
    const grossValue = holding.grossValue;
    if (
      typeof quantity !== "number" ||
      !Number.isFinite(quantity) ||
      quantity <= 0 ||
      typeof grossValue !== "number" ||
      !Number.isFinite(grossValue)
    ) {
      return null;
    }

    const unitPrice = grossValue / quantity;
    if (!Number.isFinite(unitPrice) || unitPrice <= 0) return null;

    const notAvailable = t("investments.detailDrawer.common.notAvailable");
    const dateLabel = holding.valuationReferenceDate
      ? formatLocalDate(holding.valuationReferenceDate, locale, { day: "2-digit", month: "2-digit", year: "2-digit" })
      : notAvailable;

    const descriptorKey =
      assetType === AssetType.Crypto
        ? "cryptoPrice"
        : assetType === AssetType.RealEstateFund || assetType === AssetType.AgribusinessFund
          ? "fiiQuotaPrice"
          : assetType === AssetType.MutualFund
            ? "quotaValue"
            : "unitPrice";
    const descriptor = t(`investments.detailDrawer.priceReference.descriptors.${descriptorKey}`);

    return {
      label: t("investments.detailDrawer.priceReference.labelWithDate", { label: descriptor, date: dateLabel }),
      value: unitPrice,
      currencyCode: holding.asset.currencyCode || "BRL",
    };
  }, [fundData?.investmentHolding, kind, locale, t]);

  const assetTypeLabel =
    resolvedAssetType != null
      ? t(`investments.assetTypes.${resolvedAssetType}`)
      : "—";
  const fundCategoryLabel = React.useMemo(() => {
    if (kind !== "fund" || resolvedAssetType == null) return null;
    return formatFundAssetType(String(resolvedAssetType) as CvmFundAssetType, t);
  }, [kind, resolvedAssetType, t]);

  const fixedIncomeViewModel = React.useMemo(() => {
    if (kind !== "fixedIncome") return null;
    const holding = fixedIncomeData?.investmentHolding;
    if (!holding) return null;
    return buildFixedIncomeHoldingDetailViewModel(
      holding,
      fixedIncomeData?.investmentTransactions ?? [],
      fixedIncomeData?.holdingPerformance,
      fixedIncomeData?.investmentHoldingPerformance,
      t,
      locale,
    );
  }, [fixedIncomeData?.holdingPerformance, fixedIncomeData?.investmentHolding, fixedIncomeData?.investmentHoldingPerformance, fixedIncomeData?.investmentTransactions, kind, locale, t]);

  const fundViewModel = React.useMemo(() => {
    if (kind !== "fund" && kind !== "equity") return null;
    const holding = fundData?.investmentHolding;
    if (!holding) return null;
    return buildFundHoldingDetailViewModel(
      holding,
      fundData?.investmentTransactions ?? [],
      fundData?.holdingPerformance,
      fundData?.investmentHoldingPerformance,
      t,
      locale,
    );
  }, [fundData?.holdingPerformance, fundData?.investmentHolding, fundData?.investmentHoldingPerformance, fundData?.investmentTransactions, kind, locale, t]);

  const assetName = fixedIncomeViewModel?.title || fundViewModel?.title || initialTitle || "—";
  const logoUrl =
    fixedIncomeData?.investmentHolding?.asset.logoAsset?.publicUrl ??
    fixedIncomeData?.investmentHolding?.financialAccount?.issuer?.logoAsset?.publicUrl ??
    fundData?.investmentHolding?.asset.logoAsset?.publicUrl ??
    fundData?.investmentHolding?.financialAccount?.issuer?.logoAsset?.publicUrl ??
    initialLogoUrl ??
    initialIssuerLogoUrl ??
    undefined;

  const deletingTransactionSummary = React.useMemo(() => {
    if (!deletingTransaction) return "";
    const directionLabel =
      deletingTransaction.direction === "INFLOW"
        ? t("investments.detailDrawer.timeline.inflowLabel")
        : t("investments.detailDrawer.timeline.outflowLabel");
    const tradeDateLabel = formatLocalDate(deletingTransaction.tradeDate, locale, { dateStyle: "medium" });
    const currencyCode =
      fundData?.investmentHolding?.financialAccount?.currencyCode ??
      fixedIncomeData?.investmentHolding?.financialAccount?.currencyCode ??
      "BRL";
    const valueLabel = new Intl.NumberFormat(locale, { style: "currency", currency: currencyCode }).format(
      deletingTransaction.grossValue,
    );
    return `${directionLabel} · ${tradeDateLabel} · ${valueLabel}`;
  }, [
    deletingTransaction,
    fixedIncomeData?.investmentHolding?.financialAccount?.currencyCode,
    fundData?.investmentHolding?.financialAccount?.currencyCode,
    locale,
    t,
  ]);

  const canRegisterRedemption =
    kind === "fixedIncome" && Boolean(maturityHolding) && (fixedIncomeData?.investmentHolding?.quantity ?? 0) > 0;

  const detailQuantityGridCard = React.useMemo((): HoldingDetailSummaryCardViewModel => {
    const quantityLabelKey =
      kind === "fixedIncome"
        ? "quantitySecurities"
        : kind === "fund"
          ? "quantityQuotas"
          : "quantityUnits";
    const rawQuantity =
      kind === "fixedIncome"
        ? fixedIncomeData?.investmentHolding?.quantity
        : fundData?.investmentHolding?.quantity;
    const quantityValue =
      typeof rawQuantity === "number" && Number.isFinite(rawQuantity) ? rawQuantity : null;
    return {
      id: "quantity",
      label: t(`investments.detailDrawer.grid.${quantityLabelKey}`),
      value: quantityValue,
      valueKind: "number",
    };
  }, [
    kind,
    fixedIncomeData?.investmentHolding?.quantity,
    fundData?.investmentHolding?.quantity,
    t,
  ]);

  const strategy: HoldingDetailStrategy =
    kind === "fixedIncome"
      ? {
          headerModel: {
            variant: "fixedIncome",
            assetName,
            logoUrl,
            assetType: resolvedAssetType,
            account: {
              name: fixedIncomeData?.investmentHolding?.financialAccount?.name ?? null,
            },
            traits: {
              issuerLabel: fixedIncomeViewModel?.issuerLabel ?? null,
              assetTypeLabel: assetTypeLabel,
              indexerLabel:
                fixedIncomeViewModel?.indexerLabel && fixedIncomeViewModel.indexerLabel !== "—"
                  ? fixedIncomeViewModel.indexerLabel
                  : null,
              taxExemptLabel: fixedIncomeViewModel?.taxExemptLabel ?? null,
            },
            metrics: {
              totalValueLabel: t("investments.detailDrawer.grid.grossTotal"),
              totalValue: fixedIncomeViewModel?.grossValue ?? null,
              currencyCode: fixedIncomeViewModel?.currencyCode ?? "BRL",
              gainPercent: fixedIncomeViewModel?.netGainPercent ?? null,
              locale,
            },
          },
          headerActions: null,
          footerActions: null,
          body: (
            <FixedIncomeHoldingDetailContent
              holding={fixedIncomeData?.investmentHolding}
              transactions={fixedIncomeData?.investmentTransactions}
              performance={fixedIncomeData?.holdingPerformance}
              investmentHoldingPerformance={fixedIncomeData?.investmentHoldingPerformance}
              loading={fixedIncomeQuery.loading}
              error={fixedIncomeQuery.error}
              locale={locale}
              onRetry={() => {
                void fixedIncomeQuery.refetch();
              }}
              onRegisterOverdueCoupon={handleRegisterOverdueCoupon}
              onEditCouponPayment={handleEditCouponPayment}
              onDeleteCouponPayment={handleDeleteCouponPayment}
              deletingCouponPayment={deletingCouponPayment}
              onDeleteTransaction={handleRequestDeleteTransaction}
            />
          ),
          extra: (
            <>
              <MaturityConfirmDrawer
                open={maturityConfirmOpen}
                onClose={closeMaturity}
                holding={maturityHolding}
                onSuccess={() => void fixedIncomeQuery.refetch()}
              />
              <CouponPaymentConfirmDrawer
                open={couponConfirmOpen}
                onClose={closeCoupon}
                pendingActionId={null}
                holding={couponConfirmHolding}
                destinationAccount={couponDestinationAccount}
                editingTransaction={couponEditingTransaction}
                onSuccess={() => {
                  closeCoupon();
                  void fixedIncomeQuery.refetch();
                }}
              />
            </>
          ),
          assetName,
          logoUrl,
          grossValue: fixedIncomeViewModel?.grossValue ?? null,
          currencyCode: (fixedIncomeViewModel?.currencyCode ?? "BRL"),
          gainPercent: fixedIncomeViewModel?.netGainPercent ?? null,
          summaryCards: [
            detailQuantityGridCard,
            { id: "principal", label: t("investments.detailDrawer.grid.principal"), value: fixedIncomeViewModel?.principal ?? null },
            { id: "netTotal", label: t("investments.detailDrawer.grid.netTotal"), value: fixedIncomeViewModel?.netValue ?? null },
            {
              id: "xirr",
              label:
                fixedIncomeViewModel?.xirr != null && fixedIncomeViewModel.xirrSinceYear
                  ? `${t("investments.detailDrawer.performance.xirr")} (${fixedIncomeViewModel.xirrSinceYear})`
                  : t("investments.detailDrawer.performance.xirr"),
              value: fixedIncomeViewModel?.xirr ?? null,
              valueKind: "percent",
              caption: null,
            },
            { id: "totalGain", label: t("investments.detailDrawer.grid.totalGain"), value: fixedIncomeViewModel?.totalReturnAbsolute ?? null },
            { id: "couponsReceivedNet", label: t("investments.detailDrawer.grid.couponsReceivedNet"), value: fixedIncomeViewModel?.couponsReceivedNet ?? null },
            { id: "incomeTax", label: t("investments.detailDrawer.grid.incomeTax"), value: fixedIncomeViewModel?.incomeTax ?? null },
            { id: "iof", label: t("investments.detailDrawer.grid.iof"), value: fixedIncomeViewModel?.iof ?? null },
          ],
        }
      : {
          headerModel: {
            variant: kind === "equity" ? "equity" : "fund",
            assetName,
            logoUrl,
            assetType: resolvedAssetType,
            account: {
              name: fundData?.investmentHolding?.financialAccount?.name ?? null,
            },
            traits: {
              issuerLabel: fundViewModel?.issuerLabel ?? null,
              assetTypeLabel: assetTypeLabel,
              subtitleLabel: fundCategoryLabel ?? fundViewModel?.subtitle ?? null,
              fundCnpj: kind === "fund" ? fundData?.investmentHolding?.asset.fundCnpj ?? null : null,
            },
            metrics: {
              totalValueLabel: t("investments.detailDrawer.grid.grossTotal"),
              totalValue: fundViewModel?.grossValue ?? null,
              currencyCode: fundViewModel?.currencyCode ?? "BRL",
              gainPercent: fundViewModel?.netGainPercent ?? null,
              locale,
            },
          },
          headerActions: null,
          footerActions: null,
          body: (
            <FundHoldingDetailContent
              holding={fundData?.investmentHolding}
              transactions={fundData?.investmentTransactions}
              performance={fundData?.holdingPerformance}
              investmentHoldingPerformance={fundData?.investmentHoldingPerformance}
              loading={fundQuery.loading}
              error={fundQuery.error}
              locale={locale}
              selectedPeriod={fundPerformancePeriod}
              onSelectPeriod={setFundPerformancePeriod}
              onRetry={() => {
                void fundQuery.refetch();
              }}
              onDeleteTransaction={handleRequestDeleteTransaction}
            />
          ),
          extra: null,
          assetName,
          logoUrl,
          grossValue: fundViewModel?.grossValue ?? null,
          currencyCode: (fundViewModel?.currencyCode ?? "BRL"),
          gainPercent: fundViewModel?.netGainPercent ?? null,
          // Equity: IR depends on monthly sale volume, 20k exemption, and trade type — cannot
          // pre-calculate net value or income tax. IOF does not apply to equity assets.
          // Funds: income is realised via quota appreciation (come-cotas), not separate dividends.
          summaryCards: kind === "equity"
            ? [
                detailQuantityGridCard,
                { id: "principal", label: t("investments.detailDrawer.grid.principal"), value: fundViewModel?.principal ?? null },
                {
                  id: "xirr",
                  label:
                    fundViewModel?.xirr != null && fundViewModel.xirrSinceYear
                      ? `${t("investments.detailDrawer.performance.xirr")} (${fundViewModel.xirrSinceYear})`
                      : t("investments.detailDrawer.performance.xirr"),
                  value: fundViewModel?.xirr ?? null,
                  valueKind: "percent" as const,
                  caption: null,
                },
                { id: "totalReturn", label: t("investments.detailDrawer.grid.totalReturn"), value: fundViewModel?.totalReturnAbsolute ?? null },
                { id: "dividendsReceived", label: t("investments.detailDrawer.grid.dividendsReceived"), value: fundViewModel?.dividendsReceived ?? null },
              ]
            : [
                detailQuantityGridCard,
                { id: "principal", label: t("investments.detailDrawer.grid.principal"), value: fundViewModel?.principal ?? null },
                { id: "netTotal", label: t("investments.detailDrawer.grid.netTotal"), value: fundViewModel?.netValue ?? null },
                {
                  id: "administrationFeeAnnualPercent",
                  label: t("investments.detailDrawer.grid.administrationFeeAnnualPercent"),
                  value: fundData?.investmentHolding?.administrationFeeAnnualPercent ?? null,
                  valueKind: "percentagePoints" as const,
                },
                {
                  id: "xirr",
                  label:
                    fundViewModel?.xirr != null && fundViewModel.xirrSinceYear
                      ? `${t("investments.detailDrawer.performance.xirr")} (${fundViewModel.xirrSinceYear})`
                      : t("investments.detailDrawer.performance.xirr"),
                  value: fundViewModel?.xirr ?? null,
                  valueKind: "percent" as const,
                  caption: null,
                },
                { id: "totalReturn", label: t("investments.detailDrawer.grid.totalReturn"), value: fundViewModel?.totalReturnAbsolute ?? null },
                { id: "netEarnings", label: t("investments.detailDrawer.grid.netEarnings"), value: fundViewModel?.netGain ?? null },
                { id: "incomeTax", label: t("investments.detailDrawer.grid.incomeTax"), value: fundViewModel?.incomeTax ?? null },
                { id: "iof", label: t("investments.detailDrawer.grid.iof"), value: fundViewModel?.iof ?? null },
              ],
        };

  const movementTransactions = React.useMemo(() => {
    if (kind === "fixedIncome") return fixedIncomeData?.investmentTransactions ?? [];
    if (kind === "fund" || kind === "equity") return fundData?.investmentTransactions ?? [];
    return [];
  }, [fixedIncomeData?.investmentTransactions, fundData?.investmentTransactions, kind]);

  const editorAsset = React.useMemo(
    () => (actionHoldingRow ? mapHoldingToResolvedAsset(actionHoldingRow) : null),
    [actionHoldingRow],
  );

  const editorAssetClass = React.useMemo(
    () => mapAssetTypeToTransactionEditorClass(actionHoldingRow?.asset?.assetType ?? null),
    [actionHoldingRow?.asset?.assetType],
  );

  const handleOpenRowBuyFromTransactionsTab = React.useCallback(() => {
    if (!actionHoldingRow || !editorAsset) return;
    const intent: RowBuyIntent = {
      kind: "row-buy",
      assetClass: editorAssetClass,
      holdingId: actionHoldingRow.id,
      asset: editorAsset,
    };
    onOpenTransactionEditor(intent);
  }, [actionHoldingRow, editorAsset, editorAssetClass, onOpenTransactionEditor]);

  const handleOpenEditFromTransactionsTab = React.useCallback((transactionId: string) => {
    if (!actionHoldingRow || !editorAsset) return;
    const intent: EditIntent = {
      kind: "edit",
      assetClass: editorAssetClass,
      holdingId: actionHoldingRow.id,
      transactionId,
      asset: editorAsset,
    };
    onOpenTransactionEditor(intent);
  }, [actionHoldingRow, editorAsset, editorAssetClass, onOpenTransactionEditor]);

  const movementCurrencyCode =
    fundData?.investmentHolding?.financialAccount?.currencyCode ??
    fixedIncomeData?.investmentHolding?.financialAccount?.currencyCode ??
    "BRL";

  const buyDirectionLabel =
    kind === "fixedIncome" || kind === "fund"
      ? t("investments.transactions.apply")
      : t("investments.transactions.buy");
  const sellDirectionLabel =
    kind === "fixedIncome" || kind === "fund"
      ? t("investments.transactions.redeem")
      : t("investments.transactions.sell");

  const holdingDeleteFooter = (
    <DrawerDeleteButton
      onClick={openHoldingRemoveConfirm}
      disabled={deletingHolding || !actionHoldingRow}
      data-testid="investment-holding-detail-delete"
    >
      {t("investments.table.rowActions.remove")}
    </DrawerDeleteButton>
  );

  const transactionsTabContent = (
    <MovimentacoesTab
      locale={locale}
      currencyCode={movementCurrencyCode}
      buyLabel={buyDirectionLabel}
      sellLabel={sellDirectionLabel}
      transactions={movementTransactions.map((transaction) => ({
        id: transaction.id,
        type: transaction.type,
        direction: transaction.direction,
        tradeDate: transaction.tradeDate,
        quantity: transaction.quantity,
        unitPrice: transaction.unitPrice,
        grossValue: transaction.grossValue,
        netValue: transaction.netValue,
      }))}
      onEditTransaction={handleOpenEditFromTransactionsTab}
      onDeleteTransaction={handleRequestDeleteTransaction}
    />
  );

  const parametersTabContent =
    kind === "fund" ? (
      fundData?.investmentHolding ? (
        <FundParametersTab
          holding={fundData.investmentHolding}
          locale={locale}
          onSaved={async () => {
            await fundQuery.refetch();
          }}
        />
      ) : fundQuery.loading ? (
        <FormStack spacing={2}>
          <Skeleton variant="text" width="40%" height={24} />
          <Skeleton variant="rectangular" height={56} />
          <Skeleton variant="text" width="35%" height={24} />
          <Skeleton variant="rectangular" height={40} />
          <Skeleton variant="text" width="85%" height={20} />
          <Box sx={{ display: "flex", justifyContent: "flex-start" }}>
            <Skeleton variant="rectangular" width={96} height={36} />
          </Box>
        </FormStack>
      ) : null
    ) : null;

  return (
    <>
      <HoldingDetailDrawerShell
        open={open}
        onClose={onClose}
        isMobile={isMobile}
        locale={locale}
        title={title}
        closeAriaLabel={t("actions.close")}
        headerActions={strategy.headerActions}
        footerActions={holdingDeleteFooter}
        headerModel={strategy.headerModel}
        assetName={strategy.assetName}
        logoUrl={strategy.logoUrl}
        priceReferenceLabel={priceReference?.label ?? null}
        priceReferenceValue={priceReference?.value ?? null}
        priceReferenceCurrencyCode={priceReference?.currencyCode ?? strategy.currencyCode}
        totalValueLabel={t("investments.detailDrawer.grid.grossTotal")}
        totalValue={strategy.grossValue}
        currencyCode={strategy.currencyCode}
        gainPercent={strategy.gainPercent}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        summaryTabLabel={t("investments.detailDrawer.transactions.tabs.summary")}
        transactionsTabLabel={t("investments.detailDrawer.transactions.tabs.transactions")}
        parametersTabLabel={kind === "fund" ? t("investments.detailDrawer.transactions.tabs.parameters") : null}
        transactionsTabAction={
          <ManagementHeaderActionButton
            variant="outlined"
            size="small"
            offsetBottom={0}
            startIcon={<AddIcon fontSize="small" />}
            onClick={handleOpenRowBuyFromTransactionsTab}
            disabled={!actionHoldingRow}
            sx={{
              alignSelf: "center",
              height: "auto",
              minHeight: "unset",
              py: 0.5,
              px: 1,
            }}
          >
            {t("investments.detailDrawer.transactions.add")}
          </ManagementHeaderActionButton>
        }
        summaryCards={strategy.summaryCards}
        body={strategy.body}
        transactionsContent={transactionsTabContent}
        parametersContent={parametersTabContent}
      />

      {strategy.extra}

      <ConfirmationDialog
        open={holdingRemoveConfirmOpen}
        onClose={closeHoldingRemoveConfirm}
        onConfirm={() => {
          void confirmRemoveHolding();
        }}
        title={t("investments.deleteConfirmation.title")}
        message={t("investments.deleteConfirmation.message")}
        cancelText={t("actions.cancel")}
        confirmText={t("actions.delete")}
        confirmColor="error"
        loading={deletingHolding}
      />

      <Dialog open={deleteConfirmOpen} onClose={handleCloseDeleteDialog} maxWidth="xs" fullWidth>
        <DialogTitle>{t("investments.detailDrawer.timeline.deleteConfirmTitle")}</DialogTitle>
        <DialogContent>
          <Typography variant="body2">{deletingTransactionSummary || "—"}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>
            {t("investments.detailDrawer.timeline.deleteConfirmWarning")}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDeleteDialog} disabled={deleteInProgress}>
            {t("actions.cancel")}
          </Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => {
              void handleConfirmDeleteTransaction();
            }}
            disabled={deleteInProgress || !deletingTransactionId}
            startIcon={deleteInProgress ? <CircularProgress size={16} color="inherit" /> : undefined}
          >
            {t("actions.delete")}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
