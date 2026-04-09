"use client";

import * as React from "react";
import { useQuery } from "@apollo/client/react";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { Avatar, Box, Divider, IconButton, Tooltip, Typography, useMediaQuery, useTheme } from "@mui/material";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { CurrencyAmountParts, GainPercent } from "@/shared/components/ui/data-display";
import { personalFinanceTranslations } from "../../i18n";
import { CloseIcon } from "@/shared/ui/mui-imports";
import { FixedIncomeHoldingDetailContent } from "./fixed-income/FixedIncomeHoldingDetailContent";
import { FixedIncomeHoldingDetailDocument } from "./fixed-income/api/queries.generated";
import { buildFixedIncomeHoldingDetailViewModel } from "./fixed-income/fixedIncomeHoldingDetailAdapter";
import { RecordFixedIncomeRedemptionDrawer } from "./fixed-income/RecordFixedIncomeRedemptionDrawer";
import { FundHoldingDetailContent } from "./fund/FundHoldingDetailContent";
import { FundHoldingDetailDocument } from "./fund/api/queries.generated";
import { buildFundHoldingDetailViewModel } from "./fund/fundHoldingDetailAdapter";
import type { DetailPerformancePeriod } from "./performance/detailPerformancePeriods";
import { parseUnknownDate, resolveDetailPerformanceWindow } from "./performance/detailPerformancePeriods";

export type InvestmentHoldingDetailKind = "fixedIncome" | "fund" | "equity";

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
  /** Shown under the main value (e.g. period start for TIR). */
  caption?: string | null;
  labelTooltip?: string;
}

interface HoldingDetailStrategy {
  headerActions: React.ReactNode;
  footerActions: React.ReactNode;
  body: React.ReactNode;
  extra: React.ReactNode;
  assetName: string;
  logoUrl: string | undefined;
  headerMeta: string;
  netValue: number | null;
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
  assetName: string;
  logoUrl: string | undefined;
  headerMeta: string;
  netValueLabel: string;
  netValue: number | null;
  currencyCode: string;
  gainPercent: number | null;
  summaryCards: HoldingDetailSummaryCardViewModel[];
  body: React.ReactNode;
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
  assetName,
  logoUrl,
  headerMeta,
  netValueLabel,
  netValue,
  currencyCode,
  gainPercent,
  summaryCards,
  body,
}: HoldingDetailDrawerShellProps) {
  return (
    <Drawer
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
        <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 2, mb: 2 }}>
          <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1.5, minWidth: 0, flex: "1 1 auto" }}>
            <Avatar
              src={logoUrl}
              alt={assetName}
              variant="rounded"
              sx={{ width: 40, height: 40, borderRadius: ".5rem", flexShrink: 0, mt: 0.25 }}
            >
              {assetName.slice(0, 1).toUpperCase()}
            </Avatar>
            <Box sx={{ minWidth: 0, flex: "1 1 auto" }}>
              <Typography
                variant="h6"
                fontWeight={700}
                sx={{
                  minWidth: 0,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  display: "-webkit-box",
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: "vertical",
                  wordBreak: "break-word",
                  lineHeight: 1.25,
                }}
              >
                {assetName}
              </Typography>
              <Typography variant="body2" noWrap sx={{ mt: 0.25, minWidth: 0 }}>
                {headerMeta}
              </Typography>
            </Box>
          </Box>

          <Box sx={{ display: "flex", flexDirection: "column", alignItems: "flex-end", flex: "0 0 auto" }}>
            <Label sx={{ mb: 0.25 }}>{netValueLabel}</Label>
            <CurrencyAmountParts
              value={netValue}
              currency={currencyCode}
              locale={locale}
              variant="h4"
              fontWeight={500}
              noWrap
              sx={{ lineHeight: 1.1, fontSize: "1.7rem", letterSpacing: -1 }}
            />
            {gainPercent != null ? (
              <Box sx={{ mt: 0.25, display: "flex", alignItems: "center", justifyContent: "flex-end" }}>
                <GainPercent value={gainPercent} locale={locale} iconSize={12} textSx={{ fontWeight: 600 }} />
              </Box>
            ) : (
              <Typography variant="caption" sx={{ mt: 0.25, visibility: "hidden" }}>
                —
              </Typography>
            )}
          </Box>
        </Box>

        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
            gap: 1.5,
            mb: 2,
          }}
        >
          {summaryCards.map((card) => (
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
                  currency={currencyCode}
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

        <Divider sx={{ mb: 3 }} />

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
  const locale = i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR";

  const shouldFetchFixedIncome = open && kind === "fixedIncome" && Boolean(holdingId);
  const shouldFetchFund = open && (kind === "fund" || kind === "equity") && Boolean(holdingId);
  const [fundPerformancePeriod, setFundPerformancePeriod] = React.useState<DetailPerformancePeriod>("ALL");
  const [fundFirstTradeDate, setFundFirstTradeDate] = React.useState<Date | null>(null);
  const fundPerformanceWindow = React.useMemo(
    () => resolveDetailPerformanceWindow(fundPerformancePeriod, fundFirstTradeDate),
    [fundFirstTradeDate, fundPerformancePeriod],
  );

  React.useEffect(() => {
    if (!open) return;
    setFundPerformancePeriod("ALL");
    setFundFirstTradeDate(null);
  }, [holdingId, kind, open]);

  const fixedIncomeQuery = useQuery(FixedIncomeHoldingDetailDocument, {
    variables: { holdingId: holdingId || "" },
    skip: !shouldFetchFixedIncome,
    errorPolicy: "all",
    fetchPolicy: "cache-and-network",
    nextFetchPolicy: "cache-and-network",
    notifyOnNetworkStatusChange: true,
  });

  const fundQuery = useQuery(FundHoldingDetailDocument, {
    variables: {
      holdingId: holdingId || "",
      performanceResolution: fundPerformanceWindow.resolution,
      performanceFromDate: fundPerformanceWindow.fromDate ?? null,
      performanceToDate: fundPerformanceWindow.toDate ?? null,
    },
    skip: !shouldFetchFund,
    errorPolicy: "all",
    fetchPolicy: "cache-and-network",
    nextFetchPolicy: "cache-and-network",
    notifyOnNetworkStatusChange: true,
  });

  const fixedIncomeData =
    fixedIncomeQuery.data ??
    (fixedIncomeQuery.previousData?.investmentHolding?.id === holdingId ? fixedIncomeQuery.previousData : undefined);
  const fundData =
    fundQuery.data ??
    (fundQuery.previousData?.investmentHolding?.id === holdingId ? fundQuery.previousData : undefined);

  React.useEffect(() => {
    const tradeDates = (fundData?.investmentTransactions ?? [])
      .map((transaction) => parseUnknownDate(transaction.tradeDate))
      .filter((value): value is Date => value != null)
      .sort((a, b) => a.getTime() - b.getTime());
    const nextFirstTradeDate = tradeDates[0] ?? null;
    setFundFirstTradeDate((current) => {
      const currentTime = current?.getTime() ?? null;
      const nextTime = nextFirstTradeDate?.getTime() ?? null;
      return currentTime === nextTime ? current : nextFirstTradeDate;
    });
  }, [fundData?.investmentTransactions]);

  const resolvedAssetType =
    (kind === "fixedIncome"
      ? fixedIncomeData?.investmentHolding?.asset.assetType
      : kind === "fund" || kind === "equity"
      ? fundData?.investmentHolding?.asset.assetType
      : null) ||
    initialAssetType ||
    null;

  type AssetGroupKey =
    | "variableIncome"
    | "bankFixedIncome"
    | "creditFixedIncome"
    | "mutualFunds"
    | "pension"
    | "structured"
    | "others";

  const ASSET_TYPES_BY_GROUP: Record<AssetGroupKey, readonly string[]> = {
    variableIncome: [
      "STOCK",
      "STOCK_RIGHTS",
      "BDR",
      "REAL_ESTATE_FUND",
      "AGRIBUSINESS_FUND",
      "ETF",
      "ETF_INTERNATIONAL",
    ],
    bankFixedIncome: ["CDB", "RDB", "LCI", "LCA", "LC", "LF", "LIG", "ETF_FIXED_INCOME", "TREASURY"],
    creditFixedIncome: ["DEBENTURES", "CRI", "CRA"],
    mutualFunds: ["MUTUAL_FUND", "FIXED_INCOME_FUND", "EQUITY_FUND", "MULTI_MARKET_FUND", "FOREX_FUND"],
    pension: ["PGBL", "VGBL"],
    structured: ["COE"],
    others: ["CRYPTO", "OPTION", "DERIVATIVES", "OTHER"],
  };

  function getAssetGroupKey(assetType: string | null | undefined): AssetGroupKey {
    if (!assetType) return "others";
    const normalized = String(assetType);
    const match =
      (Object.keys(ASSET_TYPES_BY_GROUP) as AssetGroupKey[]).find((groupKey) =>
        ASSET_TYPES_BY_GROUP[groupKey].includes(normalized),
      ) || null;
    return match || "others";
  }

  const FIXED_INCOME_GROUP_KEYS = new Set<AssetGroupKey>(["bankFixedIncome", "creditFixedIncome"]);

  const groupKey = getAssetGroupKey(resolvedAssetType);
  const groupDisplayKey =
    FIXED_INCOME_GROUP_KEYS.has(groupKey) ? "fixedIncome" : groupKey === "variableIncome" ? "variableIncome" : groupKey;

  const title =
    resolvedAssetType != null
      ? t(`investments.assetGroups.${groupDisplayKey}`)
      : t("investments.detailDrawer.title");

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

  const headerMetaFixedIncome = React.useMemo(() => {
    if (!fixedIncomeViewModel) return assetTypeLabel;
    const parts = [
      fixedIncomeViewModel.issuerLabel ?? null,
      assetTypeLabel,
      fixedIncomeViewModel.indexerLabel && fixedIncomeViewModel.indexerLabel !== "—" ? fixedIncomeViewModel.indexerLabel : null,
      fixedIncomeViewModel.taxExemptLabel ?? null,
    ].filter(Boolean) as string[];
    return parts.join(" • ");
  }, [assetTypeLabel, fixedIncomeViewModel]);

  const headerMetaFund = React.useMemo(() => {
    if (!fundViewModel) return assetTypeLabel;
    const parts = [fundViewModel.issuerLabel ?? null, fundViewModel.subtitle || assetTypeLabel].filter(Boolean) as string[];
    return parts.join(" • ");
  }, [assetTypeLabel, fundViewModel]);

  const [redemptionDrawerOpen, setRedemptionDrawerOpen] = React.useState(false);

  const redemptionHolding = React.useMemo(() => {
    const h = fixedIncomeData?.investmentHolding;
    if (!h || !h.financialAccount?.id || !h.asset?.id) return null;
    return {
      id: h.id,
      quantity: h.quantity,
      grossValue: h.grossValue,
      netValue: h.netValue,
      isTaxExempt: h.isTaxExempt,
      financialAccountId: h.financialAccount.id,
      assetId: h.asset.id,
    };
  }, [fixedIncomeData?.investmentHolding]);

  const canRegisterRedemption = kind === "fixedIncome" && Boolean(redemptionHolding) && (redemptionHolding?.quantity ?? 0) > 0;

  const strategy: HoldingDetailStrategy =
    kind === "fixedIncome"
      ? {
          headerActions: null,
          footerActions: canRegisterRedemption ? (
            <Drawer.CancelButton onClick={() => setRedemptionDrawerOpen(true)}>
              {t("investments.redemption.trigger")}
            </Drawer.CancelButton>
          ) : null,
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
            />
          ),
          extra: (
            <RecordFixedIncomeRedemptionDrawer
              open={redemptionDrawerOpen}
              onClose={() => setRedemptionDrawerOpen(false)}
              holding={redemptionHolding}
              onRecorded={() => void fixedIncomeQuery.refetch()}
            />
          ),
          assetName,
          logoUrl,
          headerMeta: headerMetaFixedIncome,
          netValue: fixedIncomeViewModel?.netValue ?? null,
          currencyCode: (fixedIncomeViewModel?.currencyCode ?? "BRL"),
          gainPercent: fixedIncomeViewModel?.netGainPercent ?? null,
          summaryCards: [
            { id: "principal", label: t("investments.detailDrawer.grid.principal"), value: fixedIncomeViewModel?.principal ?? null },
            { id: "grossTotal", label: t("investments.detailDrawer.grid.grossTotal"), value: fixedIncomeViewModel?.grossValue ?? null },
            { id: "netTotal", label: t("investments.detailDrawer.grid.netTotal"), value: fixedIncomeViewModel?.netValue ?? null },
            {
              id: "xirr",
              label: t("investments.detailDrawer.performance.xirr"),
              value: fixedIncomeViewModel?.xirr ?? null,
              valueKind: "percent",
              caption:
                fixedIncomeViewModel?.xirr != null && fixedIncomeViewModel.xirrSinceLabel
                  ? `${t("investments.detailDrawer.performance.since")} ${fixedIncomeViewModel.xirrSinceLabel}`
                  : null,
              labelTooltip: t("investments.detailDrawer.performance.xirrTooltip"),
            },
            { id: "totalReturn", label: t("investments.detailDrawer.grid.totalReturn"), value: fixedIncomeViewModel?.totalReturnAbsolute ?? null },
            { id: "netEarnings", label: t("investments.detailDrawer.grid.netEarnings"), value: fixedIncomeViewModel?.netGain ?? null },
            { id: "incomeTax", label: t("investments.detailDrawer.grid.incomeTax"), value: fixedIncomeViewModel?.incomeTax ?? null },
            { id: "iof", label: t("investments.detailDrawer.grid.iof"), value: fixedIncomeViewModel?.iof ?? null },
          ],
        }
      : {
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
            />
          ),
          extra: null,
          assetName,
          logoUrl,
          headerMeta: headerMetaFund,
          netValue: fundViewModel?.netValue ?? null,
          currencyCode: (fundViewModel?.currencyCode ?? "BRL"),
          gainPercent: fundViewModel?.netGainPercent ?? null,
          summaryCards: [
            { id: "principal", label: t("investments.detailDrawer.grid.principal"), value: fundViewModel?.principal ?? null },
            { id: "grossTotal", label: t("investments.detailDrawer.grid.grossTotal"), value: fundViewModel?.grossValue ?? null },
            { id: "netTotal", label: t("investments.detailDrawer.grid.netTotal"), value: fundViewModel?.netValue ?? null },
            {
              id: "xirr",
              label: t("investments.detailDrawer.performance.xirr"),
              value: fundViewModel?.xirr ?? null,
              valueKind: "percent",
              caption:
                fundViewModel?.xirr != null && fundViewModel.xirrSinceLabel
                  ? `${t("investments.detailDrawer.performance.since")} ${fundViewModel.xirrSinceLabel}`
                  : null,
              labelTooltip: t("investments.detailDrawer.performance.xirrTooltip"),
            },
            { id: "totalReturn", label: t("investments.detailDrawer.grid.totalReturn"), value: fundViewModel?.totalReturnAbsolute ?? null },
            { id: "netEarnings", label: t("investments.detailDrawer.grid.netEarnings"), value: fundViewModel?.netGain ?? null },
            { id: "incomeTax", label: t("investments.detailDrawer.grid.incomeTax"), value: fundViewModel?.incomeTax ?? null },
            { id: "iof", label: t("investments.detailDrawer.grid.iof"), value: fundViewModel?.iof ?? null },
            { id: "dividendsReceived", label: t("investments.detailDrawer.grid.dividendsReceived"), value: fundViewModel?.dividendsReceived ?? null },
          ],
        };

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
        assetName={strategy.assetName}
        logoUrl={strategy.logoUrl}
        headerMeta={strategy.headerMeta}
        netValueLabel={t("investments.detailDrawer.netValue")}
        netValue={strategy.netValue}
        currencyCode={strategy.currencyCode}
        gainPercent={strategy.gainPercent}
        summaryCards={strategy.summaryCards}
        body={strategy.body}
      />

      {strategy.extra}
    </>
  );
}

export default InvestmentHoldingDetailDrawer;
