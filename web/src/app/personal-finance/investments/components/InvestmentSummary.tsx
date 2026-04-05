"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import {
  alpha,
  Box,
  Button,
  CircularProgress,
  Divider,
  Link,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
  useTheme,
} from "@mui/material";
import { CurrencyAmountParts, PercentageGain, ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Label } from "@/shared/components/ui/primitives/Label";
import { useTranslation } from "@/shared/i18n";
import { usePortfolioRecalculationStatus } from "@/lib/hooks/usePortfolioRecalculationStatus";
import { extractErrorMessage } from "@/shared/utils/error";
import { getAuthToken, getCurrentAccountIdFromAccessToken } from "@/shared/utils/auth";
import { personalFinanceTranslations } from "../../i18n";
import { INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import type {
  InvestmentPortfolioSummaryQuery,
  InvestmentPortfolioSummaryQueryVariables,
} from "@/lib/graphql/operations/banking/queries.generated";
import { GroupProgressRow, type SummaryGroupKey } from "./GroupProgressRow";
import { getCurrentLocale } from "./investmentTableHelpers";

interface InvestmentSummaryProps {
  financialAccountId?: string | null;
  onGroupClick?: (groupKey: string) => void;
  activeGroupFilter?: string | null;
}

interface SummaryMetricRowProps {
  color: string;
  label: string;
  value?: number | null;
  percentage?: number | null;
  currencyCode: string;
  locale: string;
  loading?: boolean;
}

const PERIOD_STORAGE_KEY = "investment-summary-period";
const PERIOD_OPTIONS = ["MTD", "YTD", "ONE_YEAR", "THREE_YEARS", "ALL"] as const;
type SummaryPeriod = (typeof PERIOD_OPTIONS)[number];

function SummaryMetricRow({
  color,
  label,
  value,
  percentage,
  currencyCode,
  locale,
  loading = false,
}: SummaryMetricRowProps) {
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, py: 0.5 }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
          {label}
        </Typography>
      </Box>

      <Stack spacing={0.35} alignItems="flex-end">
        {loading ? (
          <Skeleton width={118} height={24} />
        ) : (
          <CurrencyAmountParts
            value={value}
            currency={currencyCode}
            locale={locale}
            variant="subtitle2"
            sx={{
              fontWeight: 700,
              color,
            }}
          />
        )}
        {percentage != null ? (
          loading ? (
            <Skeleton width={62} height={18} />
          ) : (
            <PercentageGain value={percentage} locale={locale} showIcon={false} />
          )
        ) : null}
      </Stack>
    </Box>
  );
}

export function InvestmentSummary({
  financialAccountId,
  onGroupClick,
  activeGroupFilter = null,
}: InvestmentSummaryProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const locale = React.useMemo(() => getCurrentLocale(), []);
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  const [selectedPeriod, setSelectedPeriod] = React.useState<SummaryPeriod>("YTD");

  React.useEffect(() => {
    if (typeof window === "undefined") return;
    const stored = window.sessionStorage.getItem(PERIOD_STORAGE_KEY);
    if (stored && PERIOD_OPTIONS.includes(stored as SummaryPeriod)) {
      setSelectedPeriod(stored as SummaryPeriod);
    }
  }, []);

  React.useEffect(() => {
    if (typeof window === "undefined") return;
    window.sessionStorage.setItem(PERIOD_STORAGE_KEY, selectedPeriod);
  }, [selectedPeriod]);

  const { data, loading, error, refetch } = useQuery<
    InvestmentPortfolioSummaryQuery,
    InvestmentPortfolioSummaryQueryVariables
  >(INVESTMENT_PORTFOLIO_SUMMARY, {
    variables: {
      financialAccountId: financialAccountId ?? undefined,
      period: selectedPeriod as InvestmentPortfolioSummaryQueryVariables["period"],
    },
    errorPolicy: "all",
  });

  const summary = data?.investmentPortfolioSummary;
  const currencyCode = summary?.currencyCode ?? "BRL";
  const groups = summary?.byAssetGroup ?? [];
  const inlineGroups = groups.slice(0, 5);
  const performance = summary?.performance;
  const recalculationStatus = summary?.recalculationStatus;
  const isCalculating = Boolean(recalculationStatus?.isCalculating);
  const selectedPeriodLabel = t(`investments.summary.performance.period.${selectedPeriod}`);
  const workspaceAccountId = React.useMemo(
    () => getCurrentAccountIdFromAccessToken(getAuthToken()),
    []
  );

  usePortfolioRecalculationStatus(isCalculating ? workspaceAccountId : null, {
    onEvent: (payload) => {
      const matchesCurrentSummary =
        financialAccountId == null || payload.financialAccountId === financialAccountId;
      if (!matchesCurrentSummary) return;
      void refetch();
    },
    onConnectionError: () => {
      if (!isCalculating) return;
      void refetch();
    },
  });

  const resolveGroupLabel = React.useCallback(
    (groupKey: string, fallback: string) => t(`investments.table.groups.${groupKey}`, { defaultValue: fallback }),
    [t],
  );

  return (
    <>
      <Box
        data-testid="investment-summary-section"
        sx={{ width: "100%" }}
      >
        {error && !summary ? (
          <Stack spacing={1.25} alignItems="center" sx={{ py: 4, textAlign: "center" }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              {t("investments.summary.error.title")}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 460 }}>
              {extractErrorMessage(error) || t("investments.summary.error.description")}
            </Typography>
            <Button variant="outlined" onClick={() => void refetch()}>
              {t("investments.summary.error.retry")}
            </Button>
          </Stack>
        ) : (
          <Stack spacing={2}>
            <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
              <Box sx={{ minWidth: 0 }}>
                <Box sx={{ mb: 0.75 }}>
                  <Label>{t("investments.summary.totalPortfolio")}</Label>
                </Box>
                {loading ? (
                  <Skeleton width={260} height={58} />
                ) : (
                  <Box sx={{ position: "relative", display: "inline-flex", overflow: "visible" }}>
                    <CurrencyAmountParts
                      value={summary?.totalPortfolioValue}
                      currency={currencyCode}
                      locale={locale}
                      variant="h3"
                      sx={{ fontSize: "2.3rem", fontWeight: 700, lineHeight: 1.05 }}
                    />
                    {summary?.hasEstimatedValues ? (
                      <ValueOriginIndicator
                        origin="estimado_app"
                        title={t("investments.table.valueOrigin.labels.estimado_app")}
                        description={t("investments.table.valueOrigin.descriptions.estimado_app")}
                        ariaLabel={t("investments.table.valueOrigin.ariaLabel")}
                        offsetPx={16}
                        sizePx={7}
                      />
                    ) : null}
                  </Box>
                )}
              </Box>
            </Stack>

            <Divider />

            <Stack spacing={1}>
              <Label>{t("investments.summary.performance.title")}</Label>

              <Box
                sx={{
                  p: 2,
                  borderRadius: 2,
                  border: "1px solid",
                  borderColor: "divider",
                  bgcolor: alpha(theme.palette.primary.main, 0.02),
                }}
              >
                <Stack spacing={1.5}>
                  <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 2 }}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                        {`${t("investments.summary.performance.portfolioReturn")} (${selectedPeriodLabel})`}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {t("investments.summary.performance.absolute")}
                      </Typography>
                    </Box>

                    {isCalculating ? (
                      <CircularProgress size={18} />
                    ) : performance?.twr != null ? (
                      <PercentageGain
                        value={performance.twr}
                        locale={locale}
                        textSx={{ fontWeight: 700 }}
                        showIcon={false}
                      />
                    ) : (
                      <Tooltip title={t("investments.summary.performance.noHistory")}>
                        <Typography variant="subtitle2" color="text.secondary">
                          —
                        </Typography>
                      </Tooltip>
                    )}
                  </Box>

                  <CurrencyAmountParts
                    value={summary?.grossGain}
                    currency={currencyCode}
                    locale={locale}
                    variant="h5"
                    sx={{ fontWeight: 700 }}
                  />

                  {isCalculating ? (
                    <Box>
                      <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                        {recalculationStatus?.message || t("investments.summary.performance.recalculating")}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {t("investments.summary.performance.recalculatingDetail")}
                      </Typography>
                    </Box>
                  ) : performance?.twr == null ? (
                    <Typography variant="caption" color="text.secondary">
                      {t("investments.summary.performance.noHistory")}
                    </Typography>
                  ) : null}

                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {PERIOD_OPTIONS.map((period) => (
                      <Link
                        key={period}
                        component="button"
                        type="button"
                        underline={selectedPeriod === period ? "always" : "hover"}
                        onClick={() => setSelectedPeriod(period)}
                        aria-current={selectedPeriod === period ? "page" : undefined}
                        sx={{
                          fontSize: "0.8125rem",
                          fontWeight: selectedPeriod === period ? 700 : 600,
                          color:
                            selectedPeriod === period
                              ? theme.palette.primary.main
                              : theme.palette.text.secondary,
                          textUnderlineOffset: 3,
                          "&:hover": {
                            color: theme.palette.primary.main,
                          },
                        }}
                      >
                        {t(`investments.summary.performance.period.${period}`)}
                      </Link>
                    ))}
                  </Stack>
                </Stack>
              </Box>

              <Divider sx={{ my: 1 }} />

              <SummaryMetricRow
                color={
                  (summary?.grossGain ?? 0) > 0
                    ? theme.palette.success.main
                    : (summary?.grossGain ?? 0) < 0
                      ? theme.palette.error.main
                      : theme.palette.text.primary
                }
                label={t("investments.summary.grossGain")}
                value={summary?.grossGain}
                percentage={summary?.grossGainPercent}
                currencyCode={currencyCode}
                locale={locale}
                loading={loading}
              />
              <SummaryMetricRow
                color={theme.palette.text.primary}
                label={t("investments.summary.totalInvested")}
                value={summary?.totalCostBasis}
                currencyCode={currencyCode}
                locale={locale}
                loading={loading}
              />
              <SummaryMetricRow
                color={(summary?.cashBalance ?? 0) < 0 ? theme.palette.error.main : theme.palette.text.primary}
                label={t("investments.summary.cashBalance")}
                value={summary?.cashBalance}
                currencyCode={currencyCode}
                locale={locale}
                loading={loading}
              />
            </Stack>

            <Divider />

            <Stack spacing={1}>
              <Label>
                {t("investments.summary.allocation")}
              </Label>

              {loading
                ? Array.from({ length: 4 }).map((_, index) => (
                    <Skeleton key={index} variant="rounded" height={56} />
                  ))
                : inlineGroups.map((group) => (
                    <GroupProgressRow
                      key={group.groupKey}
                      groupKey={group.groupKey as SummaryGroupKey}
                      label={resolveGroupLabel(group.groupKey, group.label)}
                      count={group.count}
                      allocationPercent={group.allocationPercent}
                      isCash={group.isCash}
                      locale={locale}
                      active={activeGroupFilter === group.groupKey}
                      onClick={onGroupClick ? () => onGroupClick(group.groupKey) : undefined}
                    />
                  ))}

              {!loading && groups.length > 5 ? (
                <Box sx={{ display: "flex", justifyContent: "flex-end", pt: 0.5 }}>
                  <Button onClick={() => setDrawerOpen(true)}>{t("investments.summary.viewAllGroups")}</Button>
                </Box>
              ) : null}
            </Stack>
          </Stack>
        )}
      </Box>

      <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} anchor="right" size="sm">
        <Drawer.Header title={t("investments.summary.drawer.title")} />
        <Drawer.Body>
          <Stack spacing={1}>
            {groups.map((group) => (
              <GroupProgressRow
                key={group.groupKey}
                groupKey={group.groupKey as SummaryGroupKey}
                label={resolveGroupLabel(group.groupKey, group.label)}
                count={group.count}
                allocationPercent={group.allocationPercent}
                isCash={group.isCash}
                locale={locale}
                active={activeGroupFilter === group.groupKey}
                onClick={
                  onGroupClick
                    ? () => {
                        onGroupClick(group.groupKey);
                        setDrawerOpen(false);
                      }
                    : undefined
                }
              />
            ))}
          </Stack>
        </Drawer.Body>
      </Drawer>
    </>
  );
}
