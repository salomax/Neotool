"use client";

import * as React from "react";
import { Box, Paper, Stack, Typography } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import { Amount } from "@/shared/components/ui/data-display";
import { EmptyState, QueryErrorState } from "@/shared/components/ui/feedback";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { Label } from "@/shared/components/ui/primitives/Label";
import { buildFundHoldingDetailViewModel } from "./fundHoldingDetailAdapter";
import type { FundHoldingDetailQuery } from "./api/queries.generated";
import { HoldingPerformanceChart } from "../performance/HoldingPerformanceChart";
import { RefreshIcon } from "@/shared/ui/mui-imports";

interface FundHoldingDetailContentProps {
  holding: FundHoldingDetailQuery["investmentHolding"] | null | undefined;
  transactions: FundHoldingDetailQuery["investmentTransactions"] | undefined;
  performance: FundHoldingDetailQuery["holdingPerformance"] | null | undefined;
  loading: boolean;
  error?: unknown;
  locale: string;
  onRetry?: () => void;
}

export function FundHoldingDetailContent({
  holding,
  transactions,
  performance,
  loading,
  error,
  locale,
  onRetry,
}: FundHoldingDetailContentProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const viewModel = React.useMemo(() => {
    if (!holding) return null;
    return buildFundHoldingDetailViewModel(holding, transactions ?? [], performance, t, locale);
  }, [holding, locale, performance, t, transactions]);

  const currencyFormatter = React.useMemo(() => {
    if (!viewModel?.currencyCode) return null;
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: viewModel.currencyCode,
    });
  }, [locale, viewModel]);

  if (loading && !holding) {
    return (
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr" }, gap: 2 }}>
        <Skeleton height={120} />
        <Skeleton height={320} />
        <Skeleton height={320} />
      </Box>
    );
  }

  if (error && !holding) {
    return (
      <QueryErrorState
        error={error}
        title={t("investments.detailDrawer.errorTitle")}
        onRetry={onRetry}
      />
    );
  }

  if (!holding || !viewModel) {
    return (
      <EmptyState title={t("shared.empty.title")} description={t("shared.empty.description")} />
    );
  }

  return (
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr" }, gap: 2 }}>

      <HoldingPerformanceChart
        points={viewModel.chartPoints}
        currencyCode={viewModel.currencyCode}
        locale={locale}
        isTaxExempt={holding.isTaxExempt}
        title={t("investments.detailDrawer.chart.title")}
        emptyTitle={t("investments.detailDrawer.chart.emptyTitle")}
        emptyDescription={t("investments.detailDrawer.chart.emptyDescription")}
        principalLabel={t("investments.detailDrawer.chart.principalLabel")}
        grossLabel={t("investments.detailDrawer.chart.grossLabel")}
        netLabel={t("investments.detailDrawer.chart.netLabel")}
        projectionToggleLabel={t("investments.detailDrawer.chart.modes.projected")}
      />

      <Box>
        <Box
          sx={{
            display: "flex",
            alignItems: { xs: "flex-start", md: "center" },
            justifyContent: "space-between",
            gap: 2,
            mb: 2,
          }}
        >
          <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            <RefreshIcon sx={{ fontSize: 18, color: "text.secondary" }} />
            <Label sx={{ mb: 0 }}>{t("investments.detailDrawer.timeline.title")}</Label>
          </Box>
        </Box>
        {viewModel.timelineItems.length === 0 ? (
          <EmptyState
            title={t("investments.detailDrawer.timeline.emptyTitle")}
            description={t("investments.detailDrawer.timeline.emptyDescription")}
          />
        ) : (
          <Stack spacing={1.5}>
            {viewModel.timelineItems.map((item, index) => {
              const signedGrossValue = item.direction === "OUTFLOW" ? -item.amount : item.amount;
              const moneyParts = [
                item.netAmount != null
                  ? t("investments.detailDrawer.timeline.netAmount", {
                      value: currencyFormatter?.format(item.netAmount) ?? String(item.netAmount),
                    })
                  : null,
                item.incomeTax != null
                  ? t("investments.detailDrawer.timeline.incomeTax", {
                      value: currencyFormatter?.format(item.incomeTax) ?? String(item.incomeTax),
                    })
                  : null,
                item.iof != null
                  ? t("investments.detailDrawer.timeline.iof", {
                      value: currencyFormatter?.format(item.iof) ?? String(item.iof),
                    })
                  : null,
              ]
                .filter(Boolean)
                .join(" · ");

              const detailText = [
                item.settlementDateLabel
                  ? t("investments.detailDrawer.timeline.settlementDate", { date: item.settlementDateLabel })
                  : null,
                moneyParts || null,
              ]
                .filter(Boolean)
                .join(" · ");

              return (
                <Box
                  key={item.id}
                  sx={{
                    display: "grid",
                    gridTemplateColumns: "16px minmax(0, 1fr)",
                    columnGap: 1.5,
                    alignItems: "stretch",
                  }}
                >
                  <Box sx={{ position: "relative", display: "flex", justifyContent: "center" }}>
                    <Box sx={{ mt: 1.25, width: 6, height: 6, borderRadius: 999, bgcolor: "primary.main" }} />
                    {index < viewModel.timelineItems.length - 1 ? (
                      <Box sx={{ position: "absolute", top: 18, bottom: -12, width: 2, bgcolor: "divider" }} />
                    ) : null}
                  </Box>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: 2,
                      borderRadius: 2,
                      minWidth: 0,
                      width: { xs: "100%", sm: 420 },
                    }}
                  >
                    <Box sx={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 2, minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={700} noWrap sx={{ minWidth: 0 }}>
                        {item.title}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" noWrap>
                        {item.tradeDateLabel}
                      </Typography>
                    </Box>
                    <Box sx={{ mt: 0.5 }}>
                      <Amount
                        value={signedGrossValue}
                        currency={viewModel.currencyCode}
                        locale={locale}
                        tone={signedGrossValue >= 0 ? "positive" : "negative"}
                        variant="h6"
                        fontWeight={700}
                        noWrap
                      />
                    </Box>
                    {detailText ? (
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: "block" }}>
                        {detailText}
                      </Typography>
                    ) : null}
                  </Paper>
                </Box>
              );
            })}
          </Stack>
        )}
      </Box>
    </Box>
  );
}

export default FundHoldingDetailContent;
