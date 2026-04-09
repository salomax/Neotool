"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import GridViewIcon from "@mui/icons-material/GridView";
import { Box, ButtonBase, Fade, IconButton, Skeleton, Stack, Tooltip, Typography, alpha, useTheme } from "@mui/material";
import { InvestmentCard } from "./InvestmentCard";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";
import { InvestmentAssetIcon, type InvestmentIconKind } from "../InvestmentAssetIcon";
import { InvestmentTable } from "../table/InvestmentTable";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import type { AssetGroupKey } from "../table/investmentTableHelpers";
import { getAssetGroupKey } from "../table/investmentTableHelpers";
import { INVESTMENT_GRID_DATA } from "@/lib/graphql/operations/banking/queries";

const makeChartSeries = (values: number[]): TimeSeriesDataPoint[] =>
  values.map((value, idx) => ({
    quarter: `Q${idx + 1}`,
    value,
  }));

type InvestmentClassItem = {
  key: string;
  title: string;
  iconKind: InvestmentIconKind;
  primaryActionLabel: string;
  secondaryActionLabel: string;
  chartData: TimeSeriesDataPoint[];
  visibleGroupKeys?: AssetGroupKey[];
  visibleAssetTypes?: string[];
  includeCashCard?: boolean;
};

const INITIAL_GRID_ITEMS: InvestmentClassItem[] = [
  {
    key: "cash",
    title: "Caixa",
    iconKind: "OTHER",
    primaryActionLabel: "Aportar",
    secondaryActionLabel: "Resgatar",
    chartData: makeChartSeries([9.2, 10.1, 10.8, 11.4, 11.9, 12.2, 12.54]),
    includeCashCard: true,
    visibleGroupKeys: [],
  },
  {
    key: "fixed-income",
    title: "Renda Fixa",
    iconKind: "FIXED_INCOME",
    primaryActionLabel: "Aplicar",
    secondaryActionLabel: "Resgatar",
    chartData: makeChartSeries([39.1, 40.2, 41.0, 42.6, 44.4, 46.1, 48.32]),
    includeCashCard: false,
    visibleGroupKeys: ["bankFixedIncome", "creditFixedIncome"],
  },
  {
    key: "funds",
    title: "Fundos",
    iconKind: "FUND",
    primaryActionLabel: "Aplicar",
    secondaryActionLabel: "Resgatar",
    chartData: makeChartSeries([24.2, 25.6, 26.1, 27.9, 29.4, 30.6, 31.87]),
    includeCashCard: false,
    visibleGroupKeys: ["mutualFunds", "pension"],
  },
  {
    key: "stocks",
    title: "Ações",
    iconKind: "STOCK",
    primaryActionLabel: "Comprar",
    secondaryActionLabel: "Vender",
    chartData: makeChartSeries([18.6, 20.1, 19.8, 22.7, 24.9, 27.3, 28.91]),
    includeCashCard: false,
    visibleAssetTypes: ["STOCK", "STOCK_RIGHTS", "BDR", "ETF", "ETF_INTERNATIONAL"],
  },
  {
    key: "reits",
    title: "FIIs",
    iconKind: "REAL_ESTATE_FUND",
    primaryActionLabel: "Comprar",
    secondaryActionLabel: "Vender",
    chartData: makeChartSeries([12.2, 12.8, 13.1, 13.8, 14.5, 15.0, 15.78]),
    includeCashCard: false,
    visibleAssetTypes: ["REAL_ESTATE_FUND", "AGRIBUSINESS_FUND"],
  },
  {
    key: "crypto",
    title: "Cripto",
    iconKind: "CRYPTO",
    primaryActionLabel: "Comprar",
    secondaryActionLabel: "Vender",
    chartData: makeChartSeries([7.9, 8.6, 9.4, 10.2, 9.8, 9.6, 9.43]),
    includeCashCard: false,
    visibleAssetTypes: ["CRYPTO"],
  },
];

interface InvestmentGridProps {
  selectedAccountId: string | null;
  bankAccounts: NonNullable<FinancialAccountsQuery["financialAccounts"]>;
  investmentAccounts: IssuerSelectionAccount[];
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
}

export function InvestmentGrid({
  selectedAccountId,
  bankAccounts,
  investmentAccounts,
  searchQuery,
  onSelectSearchAsset,
}: InvestmentGridProps) {
  const theme = useTheme();
  const [selectedClassKey, setSelectedClassKey] = React.useState<string | null>(null);

  const { data: gridData, loading: gridLoading } = useQuery<{
    investmentCashBalance: number | null;
    investmentHoldings: Array<{
      id: string;
      quantity: number;
      averageCost: number;
      grossValue: number | null;
      asset: { assetType: string | null } | null;
    }>;
    perfCash?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
    perfBankFixedIncome?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
    perfCreditFixedIncome?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
    perfMutualFunds?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
    perfPension?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
    perfStocks?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
    perfReits?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
    perfCrypto?: {
      includedHoldingsCount?: number | null;
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null;
    } | null;
  }>(INVESTMENT_GRID_DATA, {
    variables: { financialAccountId: selectedAccountId ?? undefined, includeProjection: true },
    errorPolicy: "all",
  });

  const holdings = React.useMemo(() => gridData?.investmentHoldings ?? [], [gridData?.investmentHoldings]);
  const cashBalance = gridData?.investmentCashBalance ?? null;

  const computeGroupMetrics = React.useCallback((filtered: typeof holdings) => {
    let totalGrossValue = 0;
    let totalPrincipal = 0;
    let hasGrossValue = false;
    let hasPrincipal = false;

    filtered.forEach((holding) => {
      if (typeof holding.grossValue === "number") {
        totalGrossValue += holding.grossValue;
        hasGrossValue = true;
      }
      if (typeof holding.averageCost === "number" && typeof holding.quantity === "number") {
        totalPrincipal += holding.averageCost * holding.quantity;
        hasPrincipal = true;
      }
    });

    return {
      grossValue: hasGrossValue ? totalGrossValue : 0,
      percentageGain: hasPrincipal && totalPrincipal > 0 && hasGrossValue ? (totalGrossValue - totalPrincipal) / totalPrincipal : null,
    };
  }, []);

  const metricsByKey = React.useMemo(() => {
    const fixedIncomeHoldings = holdings.filter((h) => {
      const key = getAssetGroupKey(h.asset?.assetType);
      return key === "bankFixedIncome" || key === "creditFixedIncome";
    });
    const fundsHoldings = holdings.filter((h) => {
      const key = getAssetGroupKey(h.asset?.assetType);
      return key === "mutualFunds" || key === "pension";
    });
    const stocksHoldings = holdings.filter((h) => {
      const type = h.asset?.assetType;
      return type === "STOCK" || type === "STOCK_RIGHTS" || type === "BDR" || type === "ETF" || type === "ETF_INTERNATIONAL";
    });
    const reitsHoldings = holdings.filter((h) => {
      const type = h.asset?.assetType;
      return type === "REAL_ESTATE_FUND" || type === "AGRIBUSINESS_FUND";
    });
    const cryptoHoldings = holdings.filter((h) => h.asset?.assetType === "CRYPTO");

    return {
      cash: {
        grossValue: typeof cashBalance === "number" && Number.isFinite(cashBalance) ? cashBalance : 0,
        percentageGain: null as number | null,
      },
      "fixed-income": computeGroupMetrics(fixedIncomeHoldings),
      funds: computeGroupMetrics(fundsHoldings),
      stocks: computeGroupMetrics(stocksHoldings),
      reits: computeGroupMetrics(reitsHoldings),
      crypto: computeGroupMetrics(cryptoHoldings),
    };
  }, [cashBalance, computeGroupMetrics, holdings]);

  const selectedClass = React.useMemo(
    () => INITIAL_GRID_ITEMS.find((item) => item.key === selectedClassKey) ?? null,
    [selectedClassKey],
  );

  const chartSeriesByKey = React.useMemo(() => {
    const mergePoints = (
      ...groups: Array<Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null | undefined>
    ) => {
      const acc = new Map<string, number>();
      for (const group of groups) {
        for (const point of group ?? []) {
          const ts = point?.timestamp ? String(point.timestamp) : null;
          const value = typeof point?.grossValue === "number" ? point.grossValue : null;
          if (!ts || value == null || !Number.isFinite(value)) continue;
          acc.set(ts, (acc.get(ts) ?? 0) + value);
        }
      }
      return Array.from(acc.entries())
        .map(([timestamp, grossValue]) => ({ timestamp, grossValue }))
        .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
    };

    const toSeries = (
      points?: Array<{ timestamp?: string | null; grossValue?: number | null } | null> | null,
    ): TimeSeriesDataPoint[] => {
      const parsed = (points ?? [])
        .map((point, idx) => ({
          quarter: point?.timestamp ? String(point.timestamp) : `P${idx + 1}`,
          value: typeof point?.grossValue === "number" ? point.grossValue : null,
        }))
        .filter(
          (point): point is { quarter: string; value: number } =>
            typeof point.value === "number" && Number.isFinite(point.value),
        )
        .reverse();
      // Backend often returns a single MONTH bucket; still valid for sparkline (MetricSparkline duplicates if needed).
      return parsed.length >= 1 ? parsed : [];
    };

    return {
      cash: toSeries(gridData?.perfCash?.points),
      "fixed-income": toSeries(mergePoints(gridData?.perfBankFixedIncome?.points, gridData?.perfCreditFixedIncome?.points)),
      funds: toSeries(mergePoints(gridData?.perfMutualFunds?.points, gridData?.perfPension?.points)),
      stocks: toSeries(gridData?.perfStocks?.points),
      reits: toSeries(gridData?.perfReits?.points),
      crypto: toSeries(gridData?.perfCrypto?.points),
    };
  }, [gridData]);

  const assetCountByKey = React.useMemo(() => {
    const fixedIncomeCount = holdings.filter((h) => {
      const key = getAssetGroupKey(h.asset?.assetType);
      return key === "bankFixedIncome" || key === "creditFixedIncome";
    }).length;
    const fundsCount = holdings.filter((h) => {
      const key = getAssetGroupKey(h.asset?.assetType);
      return key === "mutualFunds" || key === "pension";
    }).length;
    const stocksCount = holdings.filter((h) => {
      const type = h.asset?.assetType;
      return type === "STOCK" || type === "STOCK_RIGHTS" || type === "BDR" || type === "ETF" || type === "ETF_INTERNATIONAL";
    }).length;
    const reitsCount = holdings.filter((h) => {
      const type = h.asset?.assetType;
      return type === "REAL_ESTATE_FUND" || type === "AGRIBUSINESS_FUND";
    }).length;
    const cryptoCount = holdings.filter((h) => h.asset?.assetType === "CRYPTO").length;

    return {
      cash: null,
      "fixed-income": fixedIncomeCount,
      funds: fundsCount,
      stocks: stocksCount,
      reits: reitsCount,
      crypto: cryptoCount,
    };
  }, [holdings]);

  if (gridLoading && !gridData) {
    return (
      <Box
        sx={{
          mt: 2,
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" },
          gap: 2,
        }}
      >
        {Array.from({ length: 6 }).map((_, idx) => (
          <Box
            key={`investment-grid-skeleton-${idx}`}
            sx={{
              border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
              borderRadius: 2,
              minHeight: 180,
              p: 2,
              display: "flex",
              flexDirection: "column",
              justifyContent: "space-between",
              bgcolor: "background.paper",
            }}
          >
            <Stack direction="row" alignItems="center" spacing={1}>
              <Skeleton variant="rounded" width={26} height={26} />
              <Skeleton variant="text" width="48%" height={24} />
            </Stack>
            <Box sx={{ mt: 2 }}>
              <Skeleton variant="rectangular" width="100%" height={32} sx={{ borderRadius: 1, mb: 1 }} />
              <Stack direction="row" justifyContent="space-between" alignItems="flex-end">
                <Box>
                  <Skeleton variant="text" width={76} height={14} />
                  <Skeleton variant="text" width={124} height={30} />
                </Box>
                <Skeleton variant="text" width={64} height={20} />
              </Stack>
            </Box>
            <Box sx={{ mt: 1.5, pt: 1, borderTop: `1px solid ${alpha(theme.palette.divider, 0.6)}` }}>
              <Stack direction="row" alignItems="center" spacing={1}>
                <Skeleton variant="text" width="38%" height={24} />
                <Skeleton variant="rectangular" width={1} height={18} />
                <Skeleton variant="text" width="38%" height={24} />
                <Skeleton variant="rectangular" width={1} height={18} />
                <Skeleton variant="circular" width={22} height={22} />
              </Stack>
            </Box>
          </Box>
        ))}
      </Box>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      <Fade in={!selectedClass} timeout={220} unmountOnExit>
        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" }, gap: 2 }}>
          {INITIAL_GRID_ITEMS.map((item) => (
            <Box key={item.key} onClick={() => setSelectedClassKey(item.key)}>
              <InvestmentCard
                title={item.title}
                assetCount={assetCountByKey[item.key as keyof typeof assetCountByKey] ?? null}
                grossValue={metricsByKey[item.key as keyof typeof metricsByKey]?.grossValue ?? 0}
                currency="BRL"
                percentageGain={metricsByKey[item.key as keyof typeof metricsByKey]?.percentageGain ?? null}
                iconKind={item.iconKind}
                primaryActionLabel={item.primaryActionLabel}
                secondaryActionLabel={item.secondaryActionLabel}
                chartData={chartSeriesByKey[item.key as keyof typeof chartSeriesByKey] ?? []}
              />
            </Box>
          ))}
        </Box>
      </Fade>

      <Fade in={Boolean(selectedClass)} timeout={260} unmountOnExit>
        <Box>
          <Stack direction="row" spacing={1} sx={{ mb: 2, overflowX: "auto", pb: 0.5 }}>
            <Tooltip title="Voltar para visao em grade">
              <IconButton
                size="small"
                aria-label="Voltar para visao em grade"
                onClick={() => setSelectedClassKey(null)}
                sx={{
                  color: "text.secondary",
                  flexShrink: 0,
                  "&:hover": { color: "primary.main", bgcolor: "transparent" },
                }}
              >
                <GridViewIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            {INITIAL_GRID_ITEMS.map((item) => {
              const active = item.key === selectedClassKey;
              return (
                <ButtonBase
                  key={item.key}
                  onClick={() => setSelectedClassKey(item.key)}
                  sx={{
                    border: "1px solid",
                    borderColor: active ? "primary.main" : alpha(theme.palette.divider, 0.8),
                    bgcolor: active ? alpha(theme.palette.primary.main, 0.08) : "background.paper",
                    borderRadius: 1.5,
                    px: 1.5,
                    py: 1,
                    minWidth: "fit-content",
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                    transition: "all 180ms ease",
                  }}
                >
                  <InvestmentAssetIcon kind={item.iconKind} sx={{ fontSize: 16, color: active ? "primary.main" : "text.secondary" }} />
                  <Typography variant="body2" sx={{ fontWeight: active ? 700 : 500 }}>
                    {item.title}
                  </Typography>
                </ButtonBase>
              );
            })}
          </Stack>

          {selectedClass ? (
            <InvestmentTable
              selectedAccountId={selectedAccountId}
              bankAccounts={bankAccounts}
              investmentAccounts={investmentAccounts}
              searchQuery={searchQuery}
              onSelectSearchAsset={onSelectSearchAsset}
              visibleGroupKeys={selectedClass.visibleGroupKeys}
              visibleAssetTypes={selectedClass.visibleAssetTypes}
              includeCashCard={selectedClass.includeCashCard ?? false}
              hideGroupHeader
            />
          ) : null}
        </Box>
      </Fade>
    </Box>
  );
}
