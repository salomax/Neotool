/**
 * Shared types and configuration for financial metrics
 */

import type { MetricValueType } from "@/shared/utils/formatMetricValue";

export type { MetricValueType };

/**
 * Threshold configuration for percentage metrics
 */
export interface ThresholdConfig {
  bad: number;
  regular: number;
  good: number;
}

/**
 * Configuration for a specific metric type
 */
export interface MetricConfig {
  /** Unique identifier for the metric */
  id: string;
  /** How to interpret and format the value */
  valueType: MetricValueType;
  /** Threshold values for visual indicators (percentage metrics only) */
  thresholds?: ThresholdConfig;
  /** Whether lower values are better (inverts threshold colors) */
  thresholdInverse?: boolean;
}

/**
 * Standard metric configurations for Brazilian financial institutions
 * Based on Central Bank of Brazil (BACEN) regulations
 */
export const METRIC_CONFIGS: Record<string, MetricConfig> = {
  // Percentage metrics with thresholds
  INDICE_DE_BASILEIA: {
    id: "INDICE_DE_BASILEIA",
    valueType: "percentage",
    thresholds: {
      bad: 11,    // Below 11%: Critical (non-compliant)
      regular: 14, // 11-14%: Warning (minimum compliance)
      good: 100,   // Above 14%: Good
    },
    thresholdInverse: false,
  },
  INDICE_DE_IMOBILIZACAO: {
    id: "INDICE_DE_IMOBILIZACAO",
    valueType: "percentage",
    thresholds: {
      bad: 100,    // Above 50%: Critical
      regular: 50, // 40-50%: Warning
      good: 40,    // Below 40%: Good
    },
    thresholdInverse: true,
  },

  // Currency metrics (no thresholds)
  ATIVO_TOTAL: {
    id: "ATIVO_TOTAL",
    valueType: "currency",
  },
  ATIVO_TOTAL_AJUSTADO: {
    id: "ATIVO_TOTAL_AJUSTADO",
    valueType: "currency",
  },
  CAPTACOES: {
    id: "CAPTACOES",
    valueType: "currency",
  },
  CARTEIRA_DE_CREDITO: {
    id: "CARTEIRA_DE_CREDITO",
    valueType: "currency",
  },
  PATRIMONIO_LIQUIDO: {
    id: "PATRIMONIO_LIQUIDO",
    valueType: "currency",
  },
  LUCRO_LIQUIDO: {
    id: "LUCRO_LIQUIDO",
    valueType: "currency",
  },
  PATRIMONIO_DE_REFERENCIA_PARA_COMPARACAO_COM_O_RWA: {
    id: "PATRIMONIO_DE_REFERENCIA_PARA_COMPARACAO_COM_O_RWA",
    valueType: "currency",
  },
  CARTEIRA_DE_CREDITO_CLASSIFICADA: {
    id: "CARTEIRA_DE_CREDITO_CLASSIFICADA",
    valueType: "currency",
  },
  PASSIVO_CIRCULANTE_E_EXIGIVEL_A_LONGO_PRAZO_E_RESULTADOS_DE_EXERCICIOS_FUTUROS: {
    id: "PASSIVO_CIRCULANTE_E_EXIGIVEL_A_LONGO_PRAZO_E_RESULTADOS_DE_EXERCICIOS_FUTUROS",
    valueType: "currency",
  },
  PASSIVO_EXIGIVEL: {
    id: "PASSIVO_EXIGIVEL",
    valueType: "currency",
  },
  TITULOS_E_VALORES_MOBILIARIOS: {
    id: "TITULOS_E_VALORES_MOBILIARIOS",
    valueType: "currency",
  },
} as const;

/**
 * Check if a metric ID corresponds to a percentage metric
 */
export function isPercentageMetric(metricId: string): boolean {
  const config = METRIC_CONFIGS[metricId];
  return config?.valueType === "percentage";
}

/**
 * Get metric configuration by ID
 * Returns undefined if not found
 */
export function getMetricConfig(metricId: string): MetricConfig | undefined {
  return METRIC_CONFIGS[metricId];
}

/**
 * Get value type for a metric, with fallback
 */
export function getMetricValueType(metricId: string, fallback: MetricValueType = "currency"): MetricValueType {
  return METRIC_CONFIGS[metricId]?.valueType ?? fallback;
}
