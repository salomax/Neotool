"use client";

import * as React from "react";
import { useQuery } from "@apollo/client/react";
import { Avatar, Box, Divider, IconButton, Typography, useMediaQuery, useTheme } from "@mui/material";
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
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, mb: 2 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, minWidth: 0, flex: "1 1 auto" }}>
            <Avatar
              src={logoUrl}
              alt={assetName}
              variant="rounded"
              sx={{ width: 40, height: 40, borderRadius: ".5rem" }}
            >
              {assetName.slice(0, 1).toUpperCase()}
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="h6" fontWeight={700} noWrap sx={{ minWidth: 0 }}>
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
              <Label sx={{ mb: 0.25 }}>{card.label}</Label>
              <CurrencyAmountParts
                value={card.value}
                currency={currencyCode}
                locale={locale}
                variant="h6"
                fontWeight={500}
                noWrap
                sx={{ lineHeight: 1.1, fontSize: { xs: "1.05rem", md: "1.15rem" } }}
              />
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
  const shouldFetchFund = open && kind === "fund" && Boolean(holdingId);

  const fixedIncomeQuery = useQuery(FixedIncomeHoldingDetailDocument, {
    variables: { holdingId: holdingId || "" },
    skip: !shouldFetchFixedIncome,
    errorPolicy: "all",
    fetchPolicy: "cache-and-network",
    nextFetchPolicy: "cache-and-network",
    notifyOnNetworkStatusChange: true,
  });

  const fundQuery = useQuery(FundHoldingDetailDocument, {
    variables: { holdingId: holdingId || "" },
    skip: !shouldFetchFund,
    errorPolicy: "all",
    fetchPolicy: "cache-and-network",
    nextFetchPolicy: "cache-and-network",
    notifyOnNetworkStatusChange: true,
  });

  const resolvedAssetType =
    (kind === "fixedIncome"
      ? fixedIncomeQuery.data?.investmentHolding?.asset.assetType
      : kind === "fund"
      ? fundQuery.data?.investmentHolding?.asset.assetType
      : null) ||
    initialAssetType ||
    null;

  type AssetGroupKey =
    | "variableIncome"
    | "bankFixedIncome"
    | "creditFixedIncome"
    | "treasuryDirect"
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
    bankFixedIncome: ["CDB", "RDB", "LCI", "LCA", "LC", "LF", "LIG", "ETF_FIXED_INCOME"],
    creditFixedIncome: ["DEBENTURES", "CRI", "CRA"],
    treasuryDirect: ["TREASURY"],
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

  const FIXED_INCOME_GROUP_KEYS = new Set<AssetGroupKey>(["bankFixedIncome", "creditFixedIncome", "treasuryDirect"]);

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
    const holding = fixedIncomeQuery.data?.investmentHolding;
    if (!holding) return null;
    return buildFixedIncomeHoldingDetailViewModel(
      holding,
      fixedIncomeQuery.data?.investmentTransactions ?? [],
      fixedIncomeQuery.data?.holdingPerformance,
      t,
      locale,
    );
  }, [fixedIncomeQuery.data?.holdingPerformance, fixedIncomeQuery.data?.investmentHolding, fixedIncomeQuery.data?.investmentTransactions, kind, locale, t]);

  const fundViewModel = React.useMemo(() => {
    if (kind !== "fund") return null;
    const holding = fundQuery.data?.investmentHolding;
    if (!holding) return null;
    return buildFundHoldingDetailViewModel(
      holding,
      fundQuery.data?.investmentTransactions ?? [],
      fundQuery.data?.holdingPerformance,
      t,
      locale,
    );
  }, [fundQuery.data?.holdingPerformance, fundQuery.data?.investmentHolding, fundQuery.data?.investmentTransactions, kind, locale, t]);

  const assetName = fixedIncomeViewModel?.title || fundViewModel?.title || initialTitle || "—";
  const logoUrl =
    fixedIncomeQuery.data?.investmentHolding?.asset.logoAsset?.publicUrl ??
    fixedIncomeQuery.data?.investmentHolding?.financialAccount?.issuer?.logoAsset?.publicUrl ??
    fundQuery.data?.investmentHolding?.asset.logoAsset?.publicUrl ??
    fundQuery.data?.investmentHolding?.financialAccount?.issuer?.logoAsset?.publicUrl ??
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
    const h = fixedIncomeQuery.data?.investmentHolding;
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
  }, [fixedIncomeQuery.data?.investmentHolding]);

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
              holding={fixedIncomeQuery.data?.investmentHolding}
              transactions={fixedIncomeQuery.data?.investmentTransactions}
              performance={fixedIncomeQuery.data?.holdingPerformance}
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
            { id: "grossEarnings", label: t("investments.detailDrawer.grid.grossEarnings"), value: fixedIncomeViewModel?.grossGain ?? null },
            { id: "incomeTax", label: t("investments.detailDrawer.grid.incomeTax"), value: fixedIncomeViewModel?.incomeTax ?? null },
            { id: "iof", label: t("investments.detailDrawer.grid.iof"), value: fixedIncomeViewModel?.iof ?? null },
            { id: "netEarnings", label: t("investments.detailDrawer.grid.netEarnings"), value: fixedIncomeViewModel?.netGain ?? null },
          ],
        }
      : {
          headerActions: null,
          footerActions: null,
          body: (
            <FundHoldingDetailContent
              holding={fundQuery.data?.investmentHolding}
              transactions={fundQuery.data?.investmentTransactions}
              performance={fundQuery.data?.holdingPerformance}
              loading={fundQuery.loading}
              error={fundQuery.error}
              locale={locale}
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
            { id: "grossEarnings", label: t("investments.detailDrawer.grid.grossEarnings"), value: fundViewModel?.grossGain ?? null },
            { id: "incomeTax", label: t("investments.detailDrawer.grid.incomeTax"), value: fundViewModel?.incomeTax ?? null },
            { id: "netEarnings", label: t("investments.detailDrawer.grid.netEarnings"), value: fundViewModel?.netGain ?? null },
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
