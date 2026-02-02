/**
 * Shared utility for formatting metric values
 * Used by MetricCard, MetricValueDisplay, and other metric components
 */

import { parseCurrencyValue, parseNumberValue, type CurrencyLabels } from "./currency";

export type MetricValueType = "currency" | "number" | "percentage";

export interface ValueParts {
  prefix: string;
  main: string;
  suffix: string;
}

export interface FormatMetricValueOptions {
  currency?: string;
  locale?: string;
  currencyLabels?: CurrencyLabels;
  totalizerFormat?: "short" | "long";
  decimals?: number;
}

/**
 * Formats a metric value into its component parts (prefix, main, suffix)
 *
 * @param value - The numeric value to format
 * @param valueType - The type of metric ("currency", "number", or "percentage")
 * @param options - Formatting options
 * @returns Parsed value parts
 *
 * @example
 * ```ts
 * const parts = formatMetricValue(1500000000, "currency", { currency: "BRL" });
 * // { prefix: "R$", main: "1,5", suffix: "bi" }
 *
 * const parts = formatMetricValue(0.156, "percentage", { locale: "pt-BR" });
 * // { prefix: "", main: "15,6", suffix: "%" }
 * ```
 */
export function formatMetricValue(
  value: number | null | undefined,
  valueType: MetricValueType,
  options: FormatMetricValueOptions = {}
): ValueParts {
  const {
    currency = "BRL",
    locale = "pt-BR",
    currencyLabels,
    totalizerFormat = "short",
    decimals,
  } = options;

  if (typeof value !== "number" || !Number.isFinite(value)) {
    return {
      prefix: "",
      main: "-",
      suffix: "",
    };
  }

  if (valueType === "currency") {
    const parts = parseCurrencyValue(
      value,
      currency,
      locale,
      currencyLabels,
      totalizerFormat
    );
    return {
      prefix: parts.symbol,
      main: parts.number,
      suffix: parts.totalizer ?? "",
    };
  }

  if (valueType === "number") {
    const parts = parseNumberValue(
      value,
      locale,
      currencyLabels,
      totalizerFormat
    );
    return {
      prefix: "",
      main: parts.number,
      suffix: parts.totalizer ?? "",
    };
  }

  if (valueType === "percentage") {
    const percentageValue = value * 100;
    const formatted = percentageValue.toLocaleString(locale, {
      minimumFractionDigits: decimals !== undefined ? decimals : 0,
      maximumFractionDigits: decimals !== undefined ? decimals : 1,
    });
    return {
      prefix: "",
      main: formatted,
      suffix: "%",
    };
  }

  return {
    prefix: "",
    main: String(value),
    suffix: "",
  };
}
