"use client";

import React, { useState, useMemo } from "react";
import {
  Box,
  Typography,
  TextField,
  Autocomplete,
  Tooltip,
  IconButton,
  Paper,
  Divider,
  InputAdornment,
  useTheme,
  useMediaQuery,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import {
  MetricAreaChart,
  MetricValueDisplay,
  TrendIndicator,
  MetricDetailsDrawer,
} from "@/shared/components/ui/data-display";
import { useChartPeriodFilter } from "@/shared/hooks/charts";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";
import { isPercentageMetric as isPercentageMetricById } from "@/shared/types/metrics";

interface ReportAccount {
  id: string;
  name: string;
  description?: string;
  group?: string;
  valuesByQuarter?: TimeSeriesDataPoint[];
}

interface ReportDetails {
  name: string;
  accounts: ReportAccount[];
}

interface BacenInstitutionReportDetailProps {
  reportDetails: ReportDetails;
  quarterMessage?: string | null;
}

/**
 * Helper to determine if account represents a percentage metric
 * Uses the shared metric configuration system
 */
function isPercentageMetric(account: ReportAccount | null): boolean {
  if (!account) return false;
  return isPercentageMetricById(account.id);
}

export function BacenInstitutionReportDetail({
  reportDetails,
  quarterMessage,
}: BacenInstitutionReportDetailProps) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { t, tCommon } = useTranslation(financialDataTranslations);
  const [filterValue, setFilterValue] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState("");
  const [selectedAccount, setSelectedAccount] = useState<ReportAccount | null>(null);
  const [period, setPeriod] = useState<string>("5Y");

  // Prepare chart data for the drawer (reverse to oldest -> newest)
  const drawerChartDataRaw = useMemo(() => {
    if (!selectedAccount?.valuesByQuarter) return [];
    return [...selectedAccount.valuesByQuarter].reverse();
  }, [selectedAccount]);

  // Use the shared hook for period filtering
  const { filteredData: drawerChartData, periodGrowth: drawerGrowth } = useChartPeriodFilter(
    drawerChartDataRaw,
    period,
    {
      valueType: isPercentageMetric(selectedAccount) ? "percentage" : "currency",
    }
  );

  const accounts = useMemo(() => {
    if (!reportDetails || !reportDetails.accounts) return [];
    return reportDetails.accounts;
  }, [reportDetails]);

  // Filter accounts based on search
  const filteredAccounts = useMemo(() => {
    if (!filterValue && !inputValue) return accounts;

    const search = (filterValue || inputValue).toLowerCase();
    return accounts.filter((account) =>
      account.name?.toLowerCase().includes(search)
    );
  }, [accounts, filterValue, inputValue]);

  // Group accounts
  const groupedAccounts = useMemo(() => {
    const groups: Record<string, ReportAccount[]> = {};
    const noGroup: ReportAccount[] = [];

    // Sort accounts alphabetically by name first
    const sortedAccounts = [...filteredAccounts].sort((a, b) =>
      (a.name || "").localeCompare(b.name || "")
    );

    const overrideNoGroupIds = ["ATIVO_TOTAL", "ATIVO_TOTAL_AJUSTADO"];

    sortedAccounts.forEach((account) => {
      let groupName = account.group;

      // Force specific metrics to have no group
      if (overrideNoGroupIds.includes(account.id)) {
        groupName = undefined;
      }

      if (groupName) {
        if (!groups[groupName]) {
          groups[groupName] = [];
        }
        groups[groupName]?.push(account);
      } else {
        noGroup.push(account);
      }
    });

    // Sort group names alphabetically
    const sortedGroupNames = Object.keys(groups).sort((a, b) =>
      a.localeCompare(b)
    );

    return { groups, sortedGroupNames, noGroup };
  }, [filteredAccounts]);

  // Options for Autocomplete
  const searchOptions = useMemo(() => {
    const names = accounts
      .map((acc) => acc.name)
      .filter(Boolean);

    // Distinct and sort
    return Array.from(new Set(names))
      .sort((a, b) => a.localeCompare(b));
  }, [accounts]);

  const renderAccountRow = (account: ReportAccount, index: number) => {
    const latestEntry = account.valuesByQuarter?.[0];
    const prevEntry = account.valuesByQuarter?.[1];

    const rawValue = latestEntry?.value;
    const prevValue = prevEntry?.value;

    const isPctMetric = isPercentageMetric(account);

    // Calculate growth
    let growth: number | null = null;
    if (rawValue != null && prevValue != null && prevValue !== 0) {
      growth = (rawValue - prevValue) / Math.abs(prevValue);
    }

    // Prepare chart data
    const chartData = account.valuesByQuarter || [];
    // Only consider the data points that will actually be displayed (top 12) for the visibility check
    const chartDataToDisplay = chartData.slice(0, 12);
    const allZeros = chartDataToDisplay.length === 0 || chartDataToDisplay.every((d) => !d.value || Number(d.value) === 0);

    const customTheme = theme as any;
    const positiveColor = customTheme.custom?.palette?.currencyPositive || theme.palette.success.main;
    const negativeColor = customTheme.custom?.palette?.currencyNegative || theme.palette.error.main;

    let chartColor = theme.palette.primary.main;

    if (rawValue != null) {
      if (rawValue > 0) chartColor = positiveColor;
      else if (rawValue < 0) chartColor = negativeColor;
    }

    return (
      <Box
        key={`${account.group || 'ungrouped'}-${account.id}-${index}`}
        sx={{
          display: "grid",
          gridTemplateColumns: isMobile ? "1fr 1fr 40px" : "2fr 1.5fr 1fr 0.8fr 40px",
          alignItems: "center",
          gap: 2,
          py: 2,
          borderBottom: "1px solid",
          borderColor: "divider",
          "&:last-child": {
            borderBottom: "none",
          },
        }}
      >
        {/* Name and Info */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Tooltip title={account.description || account.name}>
            <Typography variant="body2" fontWeight={500} color="text.primary" sx={{ cursor: "help" }}>
              {account.name}
            </Typography>
          </Tooltip>
        </Box>

        {/* Mini Chart - Hidden on mobile */}
        {!isMobile && (
          <Box sx={{ height: 32, display: "flex", alignItems: "center" }}>
            {!allZeros && (
              <MetricAreaChart
                data={chartData}
                width="100%"
                height={32}
                color={chartColor}
              />
            )}
          </Box>
        )}

        {/* Value */}
        <Box sx={{ textAlign: "right" }}>
          {rawValue != null ? (
            <MetricValueDisplay
              actualValue={rawValue}
              valueType={isPctMetric ? "percentage" : "currency"}
              currency="BRL"
              align="right"
              valueVariant="subtitle1"
            />
          ) : (
            <Typography variant="body2" color="text.secondary">
              -
            </Typography>
          )}
        </Box>

        {/* Growth - Hidden on mobile */}
        {!isMobile && (
          <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
            {growth != null ? (
              <TrendIndicator
                value={growth}
                variant="body2"
                valueSx={{ fontWeight: 600 }}
              />
            ) : (
              <Typography variant="caption" color="text.secondary">
                -
              </Typography>
            )}
          </Box>
        )}

        {/* Action */}
        <Box sx={{ display: "flex", justifyContent: "center" }}>
           <IconButton
             size="small"
             onClick={() => setSelectedAccount(account)}
             aria-label={t("actions.viewDetails")}
           >
             <OpenInNewIcon fontSize="small" color="action" />
           </IconButton>
        </Box>
      </Box>
    );
  };

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h5" sx={{ mb: 3 }}>
          {reportDetails.name}
        </Typography>

        <Box sx={{ mb: 3, display: "flex", flexDirection: { xs: "column", md: "row" }, alignItems: { xs: "stretch", md: "center" }, justifyContent: "space-between", gap: 2 }}>
          <Box sx={{ maxWidth: { xs: "100%", md: 400 }, flex: 1 }}>
            <Autocomplete
              freeSolo
              filterOptions={(options, params) => {
                const { inputValue } = params;
                if (!inputValue) return options;

                const normalize = (str: string) =>
                  str.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");

                const input = normalize(inputValue);

                return options
                  .filter((option) => normalize(option as string).includes(input))
                  .sort((a, b) => (a as string).localeCompare(b as string));
              }}
              options={searchOptions}
              value={filterValue}
              onChange={(_, newValue) => setFilterValue(newValue as string | null)}
              inputValue={inputValue}
              onInputChange={(_, newInputValue) => setInputValue(newInputValue)}
              renderInput={(params) => (
                <TextField
                  {...params}
                  placeholder={t("actions.searchMetrics")}
                  size="small"
                  sx={{ bgcolor: "background.paper" }}
                  InputProps={{
                    ...params.InputProps,
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon color="action" />
                      </InputAdornment>
                    ),
                  }}
                />
              )}
            />
          </Box>
          {quarterMessage && (
            <Typography variant="caption" color="text.secondary" sx={{ textAlign: { xs: "left", md: "right" } }}>
              {quarterMessage}
            </Typography>
          )}
        </Box>

        <Paper variant="outlined">
          {/* No Group Items First */}
          {groupedAccounts.noGroup.length > 0 && (
            <>
              <Box sx={{ px: 2 }}>
                {groupedAccounts.noGroup.map(renderAccountRow)}
              </Box>
              {groupedAccounts.sortedGroupNames.length > 0 && <Divider />}
            </>
          )}

          {/* Grouped Items */}
          {groupedAccounts.sortedGroupNames.map((groupName, index) => {
            const groupItems = groupedAccounts.groups[groupName] || [];
            return (
              <React.Fragment key={groupName}>
                <Box sx={{ p: 2, bgcolor: "action.hover" }}>
                  <Typography
                    variant="caption"
                    fontWeight={700}
                    color="text.secondary"
                    sx={{ textTransform: "uppercase", letterSpacing: 1 }}
                  >
                    {groupName}
                  </Typography>
                </Box>
                <Divider />
                <Box sx={{ px: 2 }}>
                  {groupItems.map(renderAccountRow)}
                </Box>
                {index < groupedAccounts.sortedGroupNames.length - 1 && <Divider />}
              </React.Fragment>
            );
          })}

          {filteredAccounts.length === 0 && (
             <Box sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  {t("messages.noResults")}
                </Typography>
             </Box>
          )}
        </Paper>
      </Box>

      <MetricDetailsDrawer
        open={!!selectedAccount}
        onClose={() => setSelectedAccount(null)}
        title={selectedAccount?.name || t("actions.viewDetails")}
        metric={selectedAccount ? {
          label: selectedAccount.name,
          actualValue: selectedAccount.valuesByQuarter?.[0]?.value,
          valueType: isPercentageMetric(selectedAccount) ? "percentage" : "currency",
          currency: "BRL"
        } : null}
        growth={drawerGrowth}
        chartData={drawerChartData}
        period={period}
        onPeriodChange={setPeriod}
        periodOptions={[
          { value: "1Y", label: t("periods.1y") },
          { value: "3Y", label: t("periods.3y") },
          { value: "5Y", label: t("periods.5y") },
        ]}
        historyTablePeriodLabel={tCommon("calendar.quarter")}
      />
    </Box>
  );
}
