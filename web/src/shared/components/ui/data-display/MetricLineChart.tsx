import * as React from "react";
import { LineChart } from "./LineChart";
import { useTranslation } from "react-i18next";

export interface MetricLineChartProps {
  data: any[];
  label: string;
  valueType: "currency" | "percentage" | "number";
  height?: number;
  showLegend?: boolean;
}

export function MetricLineChart({
  data,
  label,
  valueType,
  height = 300,
  showLegend = true,
}: MetricLineChartProps) {
  const { t } = useTranslation("common");

  const isPercentage = valueType === "percentage";

  const chartData = React.useMemo(() => {
    if (isPercentage) {
      return data.map((item) => ({
        ...item,
        value:
          item.value === null || item.value === undefined
            ? item.value
            : Number(item.value) * 100,
      }));
    }
    return data;
  }, [data, isPercentage]);

  const xDateFormat = (value: any) => {
    const str = String(value);
    if (str.length !== 6) {
      return str;
    }
    const year = str.slice(0, 4);
    const month = str.slice(4, 6);
    return `${month}/${year}`;
  };

  return (
    <LineChart
      data={chartData}
      xKey="quarter"
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
