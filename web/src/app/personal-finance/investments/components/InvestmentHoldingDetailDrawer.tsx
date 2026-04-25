"use client";

import * as React from "react";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import EditIcon from "@mui/icons-material/Edit";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import RemoveIcon from "@mui/icons-material/Remove";
import {
  alpha,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { AssetType, InvestmentTransactionDirection } from "@/lib/graphql/types/__generated__/graphql";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { CurrencyAmountParts, GainPercent } from "@/shared/components/ui/data-display";
import { ConfirmationDialog } from "@/shared/components/ui/feedback";
import { personalFinanceTranslations } from "../../i18n";
import { CloseIcon } from "@/shared/ui/mui-imports";
import { FixedIncomeHoldingDetailContent } from "./fixed-income/FixedIncomeHoldingDetailContent";
import { useHoldingDetailDrawer } from "./useHoldingDetailDrawer";
import { buildFixedIncomeHoldingDetailViewModel } from "./fixed-income/fixedIncomeHoldingDetailAdapter";
import { MaturityConfirmDrawer } from "../MaturityConfirmDrawer";
import { CouponPaymentConfirmDrawer } from "../CouponPaymentConfirmDrawer";
import { FundHoldingDetailContent } from "./fund/FundHoldingDetailContent";
import { buildFundHoldingDetailViewModel } from "./fund/fundHoldingDetailAdapter";
import { RecordEquityHoldingDrawer } from "./RecordEquityHoldingDrawer";
import { RecordFixedIncomeDrawer } from "./RecordFixedIncomeDrawer";
import { RecordFundHoldingDrawer } from "./RecordFundHoldingDrawer";
import { InvestmentEventSelectorDrawer } from "./InvestmentEventSelectorDrawer";
import { getAssetGroupKey, type HoldingKind } from "./table/investmentTableHelpers";
import { HoldingDetailHeader } from "./HoldingDetailHeader";
import type { HoldingDetailHeaderModel } from "./holdingDetailHeaderPresenter";

export type InvestmentHoldingDetailKind = "fixedIncome" | "fund" | "equity";

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
  initialTitle?: string | null;
  initialAssetType?: string | null;
  initialLogoUrl?: string | null;
  initialIssuerLogoUrl?: string | null;
  onClose: () => void;
}

interface HoldingDetailSummaryCardViewModel {
  id: string;
  label: string;
  value: number | null;
  /** default: monetary amount; percent: annualized IRR / XIRR */
  valueKind?: "currency" | "percent";
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
  actionBar?: React.ReactNode;
  summaryCards: HoldingDetailSummaryCardViewModel[];
  body: React.ReactNode;
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
  actionBar,
  summaryCards,
  body,
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

        {actionBar ? (
          <>
            <Divider sx={{ my: 2 }} />
            {actionBar}
            <Divider sx={{ my: 2 }} />
          </>
        ) : null}

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
      </Drawer.Body>

      {footerActions ? (
        <Drawer.Footer>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 2 }}>
            {footerActions}
          </Box>
        </Drawer.Footer>
      ) : null}
    </Drawer>
  );
}

export function InvestmentHoldingDetailDrawer({
  open,
  holdingId,
  kind,
  initialTitle,
  initialAssetType,
  initialLogoUrl,
  initialIssuerLogoUrl,
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
    openMaturity,
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
    editingTransactionId,
    deletingTransactionId,
    deleteConfirmOpen,
    deleteInProgress,
    editingTransaction,
    deletingTransaction,
    openEditTransaction: handleEditTransaction,
    closeEditTransaction,
    openDeleteTransaction: handleRequestDeleteTransaction,
    closeDeleteConfirm: handleCloseDeleteDialog,
    confirmDeleteTransaction: handleConfirmDeleteTransaction,
    quickTransactionDirection,
    setQuickTransactionDirection,
    moreActionsAnchorEl,
    openMoreActions: openMoreActionsMenu,
    closeMoreActions: closeMoreActionsMenu,
    eventSelectorOpen,
    openEventSelector,
    closeEventSelector,
    holdingRemoveConfirmOpen,
    openHoldingRemoveConfirm,
    closeHoldingRemoveConfirm,
    confirmRemoveHolding: handleConfirmRemoveHolding,
    performancePeriod: fundPerformancePeriod,
    setPerformancePeriod: setFundPerformancePeriod,
    locale,
    actionHoldingRow,
    actionHoldingGroupKey,
    buyLabel,
    sellLabel,
    deletingHolding,
  } = useHoldingDetailDrawer({
    open,
    holdingId,
    kind,
    onClose,
  });

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
      fixedIncomeViewModel?.currencyCode ??
      fundViewModel?.currencyCode ??
      fixedIncomeData?.investmentHolding?.asset.currencyCode ??
      fundData?.investmentHolding?.asset.currencyCode ??
      "BRL";
    const valueLabel = new Intl.NumberFormat(locale, { style: "currency", currency: currencyCode }).format(
      deletingTransaction.grossValue,
    );
    return `${directionLabel} · ${tradeDateLabel} · ${valueLabel}`;
  }, [
    deletingTransaction,
    fixedIncomeData?.investmentHolding?.asset.currencyCode,
    fixedIncomeViewModel?.currencyCode,
    fundData?.investmentHolding?.asset.currencyCode,
    fundViewModel?.currencyCode,
    locale,
    t,
  ]);

  const canRegisterRedemption =
    kind === "fixedIncome" && Boolean(maturityHolding) && (fixedIncomeData?.investmentHolding?.quantity ?? 0) > 0;

  const equityRecordVariant = React.useMemo(() => {
    if (resolvedAssetType === AssetType.RealEstateFund || resolvedAssetType === AssetType.AgribusinessFund) return "fii" as const;
    if (resolvedAssetType === AssetType.Stock || resolvedAssetType === AssetType.StockRights) return "stock" as const;
    return "mixed" as const;
  }, [resolvedAssetType]);

  const fixedIncomeEditHolding = React.useMemo(() => {
    const holding = fixedIncomeData?.investmentHolding;
    if (!holding) return null;
    return {
      id: holding.id,
      asset: {
        id: holding.asset.id,
        name: holding.asset.name,
        assetType: holding.asset.assetType,
        issuerName: holding.asset.issuerName ?? null,
      },
      indexer: holding.indexer ?? null,
      indexerPercentage: holding.indexerPercentage ?? null,
      fixedAnnualRate: holding.fixedAnnualRate ?? null,
      maturityDate: holding.maturityDate ?? null,
      isTaxExempt: holding.isTaxExempt,
      couponFrequency: holding.couponFrequency ?? null,
      couponDayOfMonth: holding.couponDayOfMonth ?? null,
    };
  }, [fixedIncomeData?.investmentHolding]);

  const fundEditHolding = React.useMemo(() => {
    const holding = fundData?.investmentHolding;
    if (!holding) return null;
    return {
      id: holding.id,
      pricingMode: null,
      valuationReferenceDate: holding.valuationReferenceDate ?? null,
      grossValue: holding.grossValue ?? null,
      netValue: holding.netValue ?? null,
      asset: {
        id: holding.asset.id,
        name: holding.asset.name,
        assetType: holding.asset.assetType as AssetType,
      },
    };
  }, [fundData?.investmentHolding]);

  const equityEditHolding = React.useMemo(() => {
    const holding = fundData?.investmentHolding;
    if (!holding) return null;
    return {
      id: holding.id,
      asset: {
        id: holding.asset.id,
        ticker: null,
        name: holding.asset.name,
        assetType: holding.asset.assetType as AssetType,
      },
    };
  }, [fundData?.investmentHolding]);

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
              onEditTransaction={handleEditTransaction}
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
              subtitleLabel: fundViewModel?.subtitle ?? null,
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
              onEditTransaction={handleEditTransaction}
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
                { id: "principal", label: t("investments.detailDrawer.grid.principal"), value: fundViewModel?.principal ?? null },
                { id: "netTotal", label: t("investments.detailDrawer.grid.netTotal"), value: fundViewModel?.netValue ?? null },
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

  const actionBuyLabel = kind === "fixedIncome" ? t("investments.grid.actions.contributeCash") : buyLabel;
  const actionSellLabel = kind === "fixedIncome" ? t("investments.transactions.redeem") : sellLabel;
  const hasTransactionActions = Boolean(actionBuyLabel && actionSellLabel);
  const moreActionsLabel = t("investments.detailDrawer.actions.moreActions");
  const actionButtonBaseSx = React.useMemo(
    () => ({
      height: 34,
      borderRadius: 1,
    }),
    [],
  );

  const actionBar = React.useMemo(
    () => (
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1.5 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
          {hasTransactionActions ? (
            <>
              <Button
                variant="outlined"
                color="success"
                startIcon={<AddIcon />}
                onClick={() => {
                  if (kind === "fixedIncome") {
                    openEventSelector();
                    return;
                  }
                  setQuickTransactionDirection("buy");
                }}
                size="small"
                sx={{
                  ...actionButtonBaseSx,
                  color: alpha(theme.palette.success.dark, theme.palette.mode === "dark" ? 0.9 : 0.75),
                  "&:hover": {
                    bgcolor: alpha(theme.palette.success.main, theme.palette.mode === "dark" ? 0.26 : 0.16),
                    color: theme.palette.success.main,
                  },
                }}
              >
                {actionBuyLabel}
              </Button>
              <Button
                variant="outlined"
                color="error"
                startIcon={<RemoveIcon />}
                onClick={() => {
                  if (kind === "fixedIncome") {
                    openMaturity();
                    return;
                  }
                  setQuickTransactionDirection("sell");
                }}
                size="small"
                sx={{
                  ...actionButtonBaseSx,
                  color: theme.palette.error.main,
                  "&:hover": {
                    bgcolor: alpha(theme.palette.error.main, theme.palette.mode === "dark" ? 0.26 : 0.16),
                    color: theme.palette.error.main,
                  },
                }}
              >
                {actionSellLabel}
              </Button>
            </>
          ) : null}
        </Box>

        <Button
          variant="outlined"
          size="small"
          color="inherit"
          endIcon={<MoreVertIcon fontSize="small" />}
          onClick={(event) => openMoreActionsMenu(event.currentTarget)}
          disabled={!actionHoldingRow}
          aria-label={moreActionsLabel}
          sx={{
            ...actionButtonBaseSx,
            borderColor: "divider",
            color: "text.secondary",
            "&:hover": {
              borderColor: "primary.main",
              color: "primary.main",
              backgroundColor: "transparent",
            },
          }}
        >
          {moreActionsLabel}
        </Button>
      </Box>
    ),
    [
      actionBuyLabel,
      actionButtonBaseSx,
      actionHoldingRow,
      actionSellLabel,
      hasTransactionActions,
      kind,
      moreActionsLabel,
      openEventSelector,
      openMaturity,
      openMoreActionsMenu,
      setQuickTransactionDirection,
      theme,
    ],
  );

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
        footerActions={strategy.footerActions}
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
        actionBar={actionBar}
        summaryCards={strategy.summaryCards}
        body={strategy.body}
      />

      <Menu
        open={Boolean(moreActionsAnchorEl)}
        anchorEl={moreActionsAnchorEl}
        onClose={closeMoreActionsMenu}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <MenuItem
          disabled={!actionHoldingRow}
          onClick={() => {
            closeMoreActionsMenu();
            openEventSelector();
          }}
        >
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t("investments.table.rowActions.viewEvents")}</ListItemText>
        </MenuItem>

        <MenuItem
          disabled={!actionHoldingRow}
          onClick={() => {
            closeMoreActionsMenu();
            openHoldingRemoveConfirm();
          }}
          sx={{ color: "error.main" }}
        >
          <ListItemIcon sx={{ color: "inherit" }}>
            <DeleteOutlineIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t("investments.table.rowActions.remove")}</ListItemText>
        </MenuItem>
      </Menu>

      <InvestmentEventSelectorDrawer
        open={eventSelectorOpen}
        holding={actionHoldingRow}
        kind={(kind as HoldingKind | null) ?? null}
        financialAccountId={actionHoldingRow?.financialAccountId ?? null}
        investmentAccounts={[]}
        onClose={closeEventSelector}
        onRecorded={async () => {
          closeEventSelector();
          if (kind === "fixedIncome") {
            await fixedIncomeQuery.refetch();
            return;
          }
          await fundQuery.refetch();
        }}
      />

      <RecordFundHoldingDrawer
        open={
          kind === "fund" &&
          Boolean(quickTransactionDirection) &&
          !editingTransactionId &&
          (actionHoldingGroupKey === "mutualFunds" || actionHoldingGroupKey === "pension")
        }
        onClose={() => setQuickTransactionDirection(null)}
        financialAccountId={fundData?.investmentHolding?.financialAccount?.id ?? null}
        financialAccounts={[]}
        prefillHolding={fundEditHolding ? { id: fundEditHolding.id, asset: fundEditHolding.asset } : null}
        initialDirection={
          quickTransactionDirection === "sell" ? InvestmentTransactionDirection.Outflow : InvestmentTransactionDirection.Inflow
        }
        onRecorded={() => {
          setQuickTransactionDirection(null);
          void fundQuery.refetch();
        }}
      />

      <RecordEquityHoldingDrawer
        open={
          kind === "equity" &&
          Boolean(quickTransactionDirection) &&
          !editingTransactionId &&
          actionHoldingGroupKey === "variableIncome"
        }
        onClose={() => setQuickTransactionDirection(null)}
        financialAccountId={fundData?.investmentHolding?.financialAccount?.id ?? null}
        financialAccounts={[]}
        prefillHolding={equityEditHolding}
        initialDirection={
          quickTransactionDirection === "sell" ? InvestmentTransactionDirection.Outflow : InvestmentTransactionDirection.Inflow
        }
        equityRecordVariant={equityRecordVariant}
        onRecorded={() => {
          setQuickTransactionDirection(null);
          void fundQuery.refetch();
        }}
      />

      {strategy.extra}

      <RecordFixedIncomeDrawer
        open={kind === "fixedIncome" && Boolean(editingTransactionId) && Boolean(editingTransaction)}
        onClose={closeEditTransaction}
        financialAccountId={fixedIncomeData?.investmentHolding?.financialAccount?.id ?? null}
        financialAccounts={[]}
        editHolding={fixedIncomeEditHolding}
        editTransaction={editingTransaction}
        onRecorded={() => {
          closeEditTransaction();
          void fixedIncomeQuery.refetch();
        }}
      />

      <RecordFundHoldingDrawer
        open={kind === "fund" && Boolean(editingTransactionId) && Boolean(editingTransaction)}
        onClose={closeEditTransaction}
        financialAccountId={fundData?.investmentHolding?.financialAccount?.id ?? null}
        financialAccounts={[]}
        editHolding={fundEditHolding}
        editTransaction={editingTransaction}
        onRecorded={() => {
          closeEditTransaction();
          void fundQuery.refetch();
        }}
      />

      <RecordEquityHoldingDrawer
        open={kind === "equity" && Boolean(editingTransactionId) && Boolean(editingTransaction)}
        onClose={closeEditTransaction}
        financialAccountId={fundData?.investmentHolding?.financialAccount?.id ?? null}
        financialAccounts={[]}
        editHolding={equityEditHolding}
        editTransaction={editingTransaction}
        onRecorded={() => {
          closeEditTransaction();
          void fundQuery.refetch();
        }}
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

      <ConfirmationDialog
        open={holdingRemoveConfirmOpen}
        onClose={closeHoldingRemoveConfirm}
        onConfirm={handleConfirmRemoveHolding}
        title={t("investments.deleteConfirmation.title")}
        message={t("investments.deleteConfirmation.message")}
        cancelText={t("actions.cancel")}
        confirmText={t("actions.delete")}
        confirmColor="error"
        loading={deletingHolding}
      />
    </>
  );
}
