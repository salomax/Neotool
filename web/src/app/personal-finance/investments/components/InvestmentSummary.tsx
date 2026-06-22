"use client";

import React from "react";
import { NetworkStatus } from "@apollo/client";
import { useQuery } from "@apollo/client/react";
import {
  Box,
  Button,
  CircularProgress,
  Divider,
  Skeleton,
  Stack,
  Typography,
  useTheme,
  type SxProps,
  type Theme,
} from "@mui/material";
import {
  CurrencyAmountParts,
  PercentageGain,
  ValueOriginIndicator,
} from "@/shared/components/ui/data-display";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Label } from "@/shared/components/ui/primitives/Label";
import { useTranslation } from "@/shared/i18n";
import { useStoredState } from "@/shared/hooks/useStoredState";
import { usePortfolioRecalculationStatus } from "@/lib/hooks/usePortfolioRecalculationStatus";
import { extractErrorMessage } from "@/shared/utils/error";
import {
  getAuthToken,
  getCurrentAccountIdFromAccessToken,
} from "@/shared/utils/auth";
import { personalFinanceTranslations } from "../../i18n";
import { INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import type {
  InvestmentPortfolioSummaryQuery,
  InvestmentPortfolioSummaryQueryVariables,
} from "@/lib/graphql/operations/banking/queries.generated";
import { GroupProgressRow, type SummaryGroupKey } from "./GroupProgressRow";
import { getCurrentLocale } from "./table/investmentTableHelpers";
import {
  PeriodSelector,
  type SummaryPeriod,
} from "./PeriodSelector";

interface InvestmentSummaryProps {
  financialAccountId?: string | null;
  onGroupClick?: (groupKey: string) => void;
  activeGroupFilter?: string | null;
  /** Reporta carregamento/refetch do resumo para o divider acima (sidebar). */
  onSummaryFetchingChange?: (fetching: boolean) => void;
  onSummaryValueChange?: (summary: {
    totalPortfolioValue: number | null | undefined;
    currencyCode: string;
  }) => void;
}

interface SummaryMetricRowProps {
  color: string;
  label: string;
  value?: number | null;
  percentage?: number | null;
  currencyCode: string;
  locale: string;
  loading?: boolean;
  rowSx?: SxProps<Theme>;
  /** E2E: test id on the formatted amount only (see `tests/e2e/helpers/currency-dom.ts`). */
  amountTestId?: string;
}

const PERIOD_STORAGE_KEY = "investment-summary-period";

/** Matches `CurrencyAmountParts` total row line box so skeleton and value share height during refresh. */
const TOTAL_PORTFOLIO_AMOUNT_TYPOGRAPHY_SX = {
  fontSize: "2.3rem",
  fontWeight: 700,
  lineHeight: 1.05,
} as const;
const TOTAL_PORTFOLIO_LINE_BOX_HEIGHT = "calc(2.3rem * 1.05)";
const DIVIDER_VERTICAL_SPACING = "1rem";

function SummaryMetricRow({
  color,
  label,
  value,
  percentage,
  currencyCode,
  locale,
  loading = false,
  rowSx,
  amountTestId,
}: SummaryMetricRowProps) {
  const formattedValue =
    value == null
      ? "—"
      : new Intl.NumberFormat(locale, {
          style: "currency",
          currency: currencyCode,
        }).format(value);

  return (
    <Box
      sx={[
        { display: "flex", alignItems: "center", gap: 1.5, py: 0.5 },
        ...(Array.isArray(rowSx) ? rowSx : rowSx ? [rowSx] : []),
      ]}
    >
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
          {label}
        </Typography>
      </Box>

      <Stack spacing={0.35} alignItems="flex-end">
        {loading ? (
          <Skeleton width={118} height={24} />
        ) : (
          <Typography
            variant="subtitle2"
            data-testid={amountTestId}
            sx={{
              fontWeight: 700,
              color,
            }}
          >
            {formattedValue}
          </Typography>
        )}
        {percentage != null ? (
          <PercentageGain
            loading={loading}
            value={percentage}
            locale={locale}
            showIcon={false}
          />
        ) : null}
      </Stack>
    </Box>
  );
}

export function InvestmentSummary({
  financialAccountId,
  onGroupClick,
  activeGroupFilter = null,
  onSummaryFetchingChange,
  onSummaryValueChange,
}: InvestmentSummaryProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const locale = React.useMemo(() => getCurrentLocale(), []);
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  const [selectedPeriod, setSelectedPeriod] = useStoredState<SummaryPeriod>(
    PERIOD_STORAGE_KEY,
    "YTD",
    "session",
  );
  const [sseReconnectKey, setSseReconnectKey] = React.useState(0);

  const { data, loading, error, refetch, networkStatus, startPolling, stopPolling } = useQuery<
    InvestmentPortfolioSummaryQuery,
    InvestmentPortfolioSummaryQueryVariables
  >(INVESTMENT_PORTFOLIO_SUMMARY, {
    variables: {
      financialAccountId: financialAccountId ?? undefined,
      period:
        selectedPeriod as InvestmentPortfolioSummaryQueryVariables["period"],
    },
    errorPolicy: "all",
    notifyOnNetworkStatusChange: true,
  });

  const summary = data?.investmentPortfolioSummary;
  const summaryFetching =
    !error &&
    (loading ||
      networkStatus === NetworkStatus.refetch ||
      networkStatus === NetworkStatus.setVariables);
  const showSummarySkeletons = Boolean(loading && !summary);
  const currencyCode = summary?.currencyCode ?? "BRL";
  const groups = summary?.byAssetGroup ?? [];
  const inlineGroups = groups.slice(0, 5);
  const showAllocationSection = showSummarySkeletons || groups.length > 0;
  const performance = summary?.performance;
  const grossGainColor =
    (summary?.grossGain ?? 0) > 0
      ? theme.palette.success.main
      : (summary?.grossGain ?? 0) < 0
        ? theme.palette.error.main
        : theme.palette.text.primary;
  const recalculationStatus = summary?.recalculationStatus;
  const isCalculating = Boolean(recalculationStatus?.isCalculating);

  React.useEffect(() => {
    if (isCalculating) {
      startPolling(5000);
    } else {
      stopPolling();
    }
    return () => stopPolling();
  }, [isCalculating, startPolling, stopPolling]);

  const selectedPeriodLabel = t(
    `investments.summary.performance.period.${selectedPeriod}`,
  );
  const workspaceAccountId = React.useMemo(
    () => getCurrentAccountIdFromAccessToken(getAuthToken()),
    [],
  );

  usePortfolioRecalculationStatus(
    isCalculating ? workspaceAccountId : null,
    {
      onEvent: (payload) => {
        const matchesCurrentSummary =
          financialAccountId == null ||
          payload.financialAccountId === financialAccountId;
        if (!matchesCurrentSummary) return;
        void refetch();
      },
      onConnectionError: () => {
        if (!isCalculating) return;
        void refetch().then(() => {
          setSseReconnectKey((k) => k + 1);
        });
      },
    },
    sseReconnectKey,
  );

  const resolveGroupLabel = React.useCallback(
    (groupKey: string, fallback: string) =>
      t(`investments.table.groups.${groupKey}`, { defaultValue: fallback }),
    [t],
  );

  React.useEffect(() => {
    onSummaryFetchingChange?.(summaryFetching);
  }, [summaryFetching, onSummaryFetchingChange]);

  React.useEffect(() => {
    onSummaryValueChange?.({
      totalPortfolioValue: summary?.totalPortfolioValue,
      currencyCode,
    });
  }, [currencyCode, onSummaryValueChange, summary?.totalPortfolioValue]);

  React.useEffect(() => {
    return () => onSummaryFetchingChange?.(false);
  }, [onSummaryFetchingChange]);

  return (
    <>
      <Box data-testid="investment-summary-section" sx={{ width: "100%" }}>
        {error && !summary ? (
          <Stack
            spacing={1}
            alignItems="center"
            sx={{ py: 4, textAlign: "center" }}
          >
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              {t("investments.summary.error.title")}
            </Typography>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ maxWidth: 460 }}
            >
              {extractErrorMessage(error) ||
                t("investments.summary.error.description")}
            </Typography>
            <Button variant="outlined" onClick={() => void refetch()}>
              {t("investments.summary.error.retry")}
            </Button>
          </Stack>
        ) : (
          <Stack spacing={0}>
            <Stack
              direction="row"
              alignItems="center"
              justifyContent="space-between"
            >
              <Box>
                <Label>{t("investments.summary.totalPortfolio")}</Label>
                <Box
                  sx={{
                    minHeight: TOTAL_PORTFOLIO_LINE_BOX_HEIGHT,
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  {showSummarySkeletons ? (
                    <Skeleton
                      variant="rounded"
                      width={260}
                      sx={{
                        height: TOTAL_PORTFOLIO_LINE_BOX_HEIGHT,
                        transform: "none",
                        borderRadius: 1,
                      }}
                    />
                  ) : (
                    <Box
                      sx={{
                        position: "relative",
                        display: "inline-flex",
                        overflow: "visible",
                      }}
                    >
                      <CurrencyAmountParts
                        value={summary?.totalPortfolioValue}
                        currency={currencyCode}
                        locale={locale}
                        variant="h3"
                        sx={TOTAL_PORTFOLIO_AMOUNT_TYPOGRAPHY_SX}
                        data-testid="investment-summary-total-portfolio-value"
                      />
                      {summary?.hasEstimatedValues ? (
                        <ValueOriginIndicator
                          origin="estimado_app"
                          title={t(
                            "investments.table.valueOrigin.labels.estimado_app",
                          )}
                          description={t(
                            "investments.table.valueOrigin.descriptions.estimado_app",
                          )}
                          ariaLabel={t(
                            "investments.table.valueOrigin.ariaLabel",
                          )}
                          dataTestId="investment-summary-estimated-indicator"
                          offsetPx={16}
                          sizePx={7}
                        />
                      ) : null}
                    </Box>
                  )}
                </Box>
              </Box>
            </Stack>

            <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />

            <Stack spacing={0}>
              <Label sx={{ mb: 1 }}>
                {t("investments.summary.performance.portfolioReturn")}
              </Label>

              <Stack spacing={1}>
                <Box
                  sx={{
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "space-between",
                    gap: 2,
                    width: "100%",
                  }}
                >
                  <PeriodSelector
                    selectedPeriod={selectedPeriod}
                    onSelectPeriod={setSelectedPeriod}
                    getPeriodLabel={(period) =>
                      t(`investments.summary.performance.period.${period}`)
                    }
                  />
                </Box>

                <Box
                  sx={{
                    display: "flex",
                    alignItems: "flex-end",
                    justifyContent: "space-between",
                    gap: 1.5,
                    width: "100%",
                  }}
                >
                  <CurrencyAmountParts
                    value={summary?.grossGain}
                    currency={currencyCode}
                    locale={locale}
                    variant="h4"
                    data-testid="investment-summary-gross-gain-amount"
                    sx={{
                      color: grossGainColor,
                      fontSize: "2rem",
                      fontWeight: 700,
                      lineHeight: 1.05,
                    }}
                  />

                  {isCalculating ? (
                    <CircularProgress size={18} sx={{ mb: 0.25 }} />
                  ) : performance?.twr != null || summaryFetching ? (
                    <Box
                      data-testid="investment-summary-performance-twr"
                      sx={{
                        display: "flex",
                        justifyContent: "flex-end",
                        position: "relative",
                        top: (t) => t.spacing(0.25),
                      }}
                    >
                      <PercentageGain
                        loading={summaryFetching}
                        value={performance?.twr}
                        locale={locale}
                        iconSize={18}
                        textSx={{ fontWeight: 700, fontSize: "1rem" }}
                      />
                    </Box>
                  ) : null}
                </Box>

                {isCalculating ? (
                  <Box>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: "block" }}
                    >
                      {(recalculationStatus?.errorCode
                        ? t(`errors.${recalculationStatus.errorCode}`, {
                            defaultValue: t(
                              "investments.summary.performance.recalculating",
                            ),
                          })
                        : undefined) ||
                        t("investments.summary.performance.recalculating")}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {t("investments.summary.performance.recalculatingDetail")}
                    </Typography>
                  </Box>
                ) : null}
              </Stack>

              <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />
              <SummaryMetricRow
                color={theme.palette.text.primary}
                label={t("investments.summary.totalContributed")}
                value={summary?.totalCostBasis}
                currencyCode={currencyCode}
                locale={locale}
                loading={showSummarySkeletons}
                amountTestId="investment-summary-total-contributed-amount"
              />
              <SummaryMetricRow
                color={
                  (summary?.cashBalance ?? 0) < 0
                    ? theme.palette.error.main
                    : theme.palette.text.primary
                }
                label={t("investments.summary.cashBalance")}
                value={summary?.cashBalance}
                currencyCode={currencyCode}
                locale={locale}
                loading={showSummarySkeletons}
                rowSx={{ pt: 0,  }}
                amountTestId="investment-summary-cash-balance-amount"
              />

              {showAllocationSection ? (
                <>
                  <Divider sx={{ my: DIVIDER_VERTICAL_SPACING }} />

                  <Label sx={{ mb: 1 }}>{t("investments.summary.allocation")}</Label>

                  {showSummarySkeletons
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
                          onClick={
                            onGroupClick
                              ? () => onGroupClick(group.groupKey)
                              : undefined
                          }
                        />
                      ))}

                  {!showSummarySkeletons && groups.length > 5 ? (
                    <Box
                      sx={{ display: "flex", justifyContent: "flex-end", pt: 0.5 }}
                    >
                      <Button onClick={() => setDrawerOpen(true)}>
                        {t("investments.summary.viewAllGroups")}
                      </Button>
                    </Box>
                  ) : null}
                </>
              ) : null}
            </Stack>

          </Stack>
        )}
      </Box>

      <Drawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        anchor="right"
        size="sm"
      >
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
