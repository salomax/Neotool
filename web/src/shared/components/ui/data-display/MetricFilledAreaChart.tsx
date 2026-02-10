import * as React from "react";
import { AreaChart } from "./AreaChart";
import { useTranslation } from "react-i18next";

export interface MetricFilledAreaChartProps {
  data: any[];
  label: string;
  valueType: "currency" | "percentage" | "number";
  height?: number;
  showLegend?: boolean;
  xKey?: string;
  xTickFormatter?: (value: any) => string;
  /**
   * Whether to disable multiplying percentage values by 100.
   */
  disablePercentageScaling?: boolean;
}

export function MetricFilledAreaChart({
  data,
  label,
  valueType,
  height = 300,
  showLegend = true,
  xKey = "quarter",
  xTickFormatter,
  disablePercentageScaling = false,
}: MetricFilledAreaChartProps) {
  const { t } = useTranslation("common");

  const isPercentage = valueType === "percentage";

  const chartData = React.useMemo(() => {
    if (isPercentage) {
      return data.map((item) => ({
        ...item,
        value:
          item.value === null || item.value === undefined
            ? item.value
            : (disablePercentageScaling ? Number(item.value) : Number(item.value) * 100),
      }));
    }
    return data;
  }, [data, isPercentage, disablePercentageScaling]);

  const defaultXDateFormat = (value: any) => {
    const str = String(value);
    if (str.length !== 6) {
      return str;
    }
    const year = str.slice(0, 4);
    const month = str.slice(4, 6);
    return `${month}/${year}`;
  };

  const xDateFormat = xTickFormatter || defaultXDateFormat;

  return (
    <AreaChart
      data={chartData}
      xKey={xKey}
      xDateFormat={xDateFormat}
      height={height}
      series={[
        {
          id: "value",
          name: label || t("value") || "Valor",
          dataKey: "value",
          yAxisId: "left-axis",
        },
      ]}
      axes={[
        {
          id: "left-axis",
          side: "left",
          label: label,
          format: isPercentage ? "percent" : "currency",
          currency: "BRL",
        },
      ]}
      scale
      scaleMinPadding={0.05}
      scaleMaxPadding={0.15}
      consolidateDecimal
      showArea
      showLegend={showLegend}
    />
  );
}
