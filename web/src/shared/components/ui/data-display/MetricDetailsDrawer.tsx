"use client";

import React from "react";
import { Box } from "@mui/material";
import { useTranslation } from "react-i18next";
import { MetricValueDisplay } from "./MetricValueDisplay";
import { MetricLineChart } from "./MetricLineChart";
import { MetricHistoryTable } from "./MetricHistoryTable";
import { GrowthChip } from "./GrowthChip";
import { SegmentedControl } from "../inputs";
import { Drawer } from "../layout/Drawer";

import { type MetricValueType } from "@/shared/utils/formatMetricValue";
import { type CurrencyLabels } from "@/shared/utils/currency";

export interface MetricDetailsDrawerProps {
  /**
   * Whether the drawer is open
   */
  open: boolean;
  /**
   * Callback when the drawer is closed
   */
  onClose: () => void;
  /**
   * Title of the drawer. If not provided, it falls back to metric.label or "Details"
   */
  title?: string;
  /**
   * The metric object containing data to display
   */
  metric: {
    label?: string;
    actualValue?: number | null;
    value?: number | null; // Support both actualValue and value
    valueType?: MetricValueType;
    currency?: string;
    locale?: string;
    totalizerFormat?: "short" | "long";
    currencyLabels?: CurrencyLabels;
  } | null;
  /**
   * Growth percentage to display
   */
  growth?: number | null;
  /**
   * Chart data for the line chart and history table
   */
  chartData?: any[];
  /**
   * Current selected period for the chart
   */
  period?: string;
  /**
   * Callback when period changes
   */
  onPeriodChange?: (period: string) => void;
  /**
   * Options for the period selector
   */
  periodOptions?: Array<{ value: string; label: string }>;
  /**
   * Size of the drawer
   * @default "lg"
   */
  size?: 'sm' | 'md' | 'lg' | 'full';
  /**
   * Title for the history table
   */
  historyTableTitle?: string;
  /**
   * Column header for the period/date column in history table
   */
  historyTablePeriodLabel?: string;
  /**
   * Custom formatter for the period/date column
   */
  onFormatHistoryPeriod?: (value: string) => React.ReactNode;
  /**
   * Key for the X-axis in the chart (defaults to "quarter")
   */
  chartXKey?: string;
  /**
   * Custom formatter for the chart X-axis ticks
   */
  chartXTickFormatter?: (value: any) => string;
  /**
   * Whether to disable multiplying percentage values by 100.
   */
  disablePercentageScaling?: boolean;
}

/**
 * A specialized drawer component for displaying detailed financial metric information,
 * including value display, growth, chart, and history table.
 */
export const MetricDetailsDrawer: React.FC<MetricDetailsDrawerProps> = ({
  open,
  onClose,
  title,
  metric,
  growth,
  chartData,
  period,
  onPeriodChange,
  periodOptions,
  size = "lg",
  historyTableTitle,
  historyTablePeriodLabel,
  onFormatHistoryPeriod,
  chartXKey,
  chartXTickFormatter,
  disablePercentageScaling: disablePercentageScalingProp,
}) => {
  const { t } = useTranslation("common");

  // Determine effective disablePercentageScaling
  const disablePercentageScaling = disablePercentageScalingProp ?? (metric as any)?.disablePercentageScaling ?? false;

  const displayTitle = title || metric?.label || "Details";
  
  // Normalize value
  const displayValue = metric?.actualValue ?? metric?.value;

  // Default valueType to 'number' if not provided
  const valueType = metric?.valueType || "number";

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size={size}
      anchor="right"
    >
      <Drawer.Header
        title={displayTitle}
        titleTypographyProps={{
          sx: { textTransform: 'uppercase', fontWeight: 700, flex: 1 }
        }}
      />
      <Drawer.Body>
        <Box sx={{ p: 2, pt: 0, display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', width: '100%' }}>
            <Box>
              {metric && (
                <MetricValueDisplay
                  actualValue={displayValue}
                  valueType={valueType}
                  currency={metric.currency}
                  locale={metric.locale}
                  totalizerFormat={metric.totalizerFormat}
                  currencyLabels={metric.currencyLabels}
                  align="left"
                  disablePercentageScaling={disablePercentageScaling}
                  valueVariant="h3"
                />
              )}
              {growth != null && (
                <GrowthChip growthPercentage={growth} />
              )}
            </Box>
            {metric && chartData && chartData.length > 0 && period && onPeriodChange && periodOptions && (
              <Box>
                <SegmentedControl
                  value={period}
                  onChange={onPeriodChange}
                  options={periodOptions}
                />
              </Box>
            )}
          </Box>
          {metric && chartData && chartData.length > 0 && (
            <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
              <MetricLineChart
                data={chartData}
                label={metric.label || displayTitle}
                valueType={valueType}
                showLegend={false}
                xKey={chartXKey}
                xTickFormatter={chartXTickFormatter}
                disablePercentageScaling={disablePercentageScaling}
                currency={metric.currency}
                locale={metric.locale}
              />
              <MetricHistoryTable
                data={chartData}
                valueType={valueType}
                currency={metric.currency}
                title={historyTableTitle}
                periodLabel={historyTablePeriodLabel}
                locale={metric.locale}
                onFormatPeriod={onFormatHistoryPeriod}
                disablePercentageScaling={disablePercentageScaling}
              />
            </Box>
          )}
        </Box>
      </Drawer.Body>
    </Drawer>
  );
};
