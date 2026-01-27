import * as React from "react";
import { useQuery } from "@apollo/client/react";
import { BACEN_INSTITUTION_REPORT_DETAILS } from "../api";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import {
  BASILEIA_THRESHOLDS,
  IMMOBILIZATION_THRESHOLDS,
} from "@/entities/financial-institution";
import type { MetricGridItem } from "@/shared/components/ui/data-display";
import {
  buildMetricItem,
  extractMetricData,
  type MetricData,
} from "@/shared/utils/buildMetricItem";

const REPORT_ID_RESUMO = "1";

/**
 * Metric configuration for balance summary items
 */
interface MetricConfig {
  id: string;
  labelKey: string;
  valueType: "currency" | "percentage";
  percentageThresholdValues?: {
    bad: number;
    regular: number;
    good: number;
  };
  percentageThresholdInverse?: boolean;
}

/**
 * Configuration for all balance summary metrics
 */
const BALANCE_METRICS: MetricConfig[] = [
  { id: "LUCRO_LIQUIDO", labelKey: "lastBalanceSummary.netProfit", valueType: "currency" },
  {
    id: "INDICE_DE_BASILEIA",
    labelKey: "lastBalanceSummary.baselIndex",
    valueType: "percentage",
    percentageThresholdValues: {
      bad: BASILEIA_THRESHOLDS.critical,
      regular: BASILEIA_THRESHOLDS.warning,
      good: 100,
    },
    percentageThresholdInverse: false,
  },
  {
    id: "INDICE_DE_IMOBILIZACAO",
    labelKey: "lastBalanceSummary.fixedAssetsIndex",
    valueType: "percentage",
    percentageThresholdValues: {
      bad: IMMOBILIZATION_THRESHOLDS.critical,
      regular: IMMOBILIZATION_THRESHOLDS.warning,
      good: IMMOBILIZATION_THRESHOLDS.good,
    },
    percentageThresholdInverse: true,
  },
  { id: "ATIVO_TOTAL", labelKey: "lastBalanceSummary.totalAssets", valueType: "currency" },
  { id: "CAPTACOES", labelKey: "lastBalanceSummary.funding", valueType: "currency" },
  { id: "CARTEIRA_DE_CREDITO", labelKey: "lastBalanceSummary.creditPortfolio", valueType: "currency" },
  { id: "PATRIMONIO_LIQUIDO", labelKey: "lastBalanceSummary.netEquity", valueType: "currency" },
  {
    id: "PATRIMONIO_DE_REFERENCIA_PARA_COMPARACAO_COM_O_RWA",
    labelKey: "lastBalanceSummary.referenceEquity",
    valueType: "currency",
  },
  {
    id: "CARTEIRA_DE_CREDITO_CLASSIFICADA",
    labelKey: "lastBalanceSummary.classifiedCreditPortfolio",
    valueType: "currency",
  },
  {
    id: "PASSIVO_CIRCULANTE_E_EXIGIVEL_A_LONGO_PRAZO_E_RESULTADOS_DE_EXERCICIOS_FUTUROS",
    labelKey: "lastBalanceSummary.currentAndLongTermLiabilities",
    valueType: "currency",
  },
  { id: "PASSIVO_EXIGIVEL", labelKey: "lastBalanceSummary.liabilities", valueType: "currency" },
  { id: "TITULOS_E_VALORES_MOBILIARIOS", labelKey: "lastBalanceSummary.securities", valueType: "currency" },
];

export function useBacenInstitutionBalanceSummary(institutionId: string) {
  const { t } = useTranslation(financialDataTranslations);

  const { data: reportData, loading } = useQuery(
    BACEN_INSTITUTION_REPORT_DETAILS,
    {
      variables: {
        id: institutionId,
        reportId: REPORT_ID_RESUMO,
        range: { last: 5 * 4 + 1 },
      },
      skip: !institutionId,
    }
  );

  const gridItems = React.useMemo<MetricGridItem[]>(() => {
    const accounts =
      (reportData as any)?.bacenFinanceInstitution?.reportDetails?.accounts || [];

    if (accounts.length === 0) {
      return [];
    }

    const items: MetricGridItem[] = [];

    for (const config of BALANCE_METRICS) {
      const data: MetricData = extractMetricData(accounts, config.id);

      // Skip percentage metrics with no data
      if (config.valueType === "percentage" && data.actualValue == null) {
        continue;
      }

      const item = buildMetricItem({
        label: t(config.labelKey),
        valueType: config.valueType,
        data,
        percentageThresholdValues: config.percentageThresholdValues,
        percentageThresholdInverse: config.percentageThresholdInverse,
      });

      items.push(item);
    }

    return items;
  }, [reportData, t]);

  return { gridItems, loading };
}
