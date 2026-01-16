import * as React from "react";
import { useQuery } from "@apollo/client/react";
import { BACEN_INSTITUTION_REPORT_DETAILS } from "@/lib/graphql/operations/financial_data/bacen-institutions";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { BASILEIA_THRESHOLDS, IMMOBILIZATION_THRESHOLDS } from "@/features/financial_data/constants/metrics";
import type { MetricGridItem } from "@/shared/components/ui/data-display";

const REPORT_ID_RESUMO = "1";

export function useBacenInstitutionBalanceSummary(institutionId: string) {
  const { t } = useTranslation(financialDataTranslations);

  const { data: reportData, loading } = useQuery(
    BACEN_INSTITUTION_REPORT_DETAILS,
    {
      variables: {
        id: institutionId,
        reportId: REPORT_ID_RESUMO,
        range: { last: 5 * 4 },
      },
      skip: !institutionId,
    }
  );

  const accounts =
    (reportData as any)?.bacenFinanceInstitution?.reportDetails?.accounts || [];

  const getMetricData = React.useCallback(
    (
      accountName: string
    ): { actualValue: number | null; historical: number[]; growthPercentage: number | null } => {
      const account = accounts.find((a: any) => a.id === accountName);
      const valuesByQuarter = account?.valuesByQuarter ?? [];

      const latestRaw = valuesByQuarter[0]?.value;
      const actualValue =
        typeof latestRaw === "number" && Number.isFinite(latestRaw)
          ? latestRaw
          : null;

      const historical = valuesByQuarter
        .map((entry: any) => entry?.value)
        .filter(
          (value: any): value is number =>
            typeof value === "number" && Number.isFinite(value)
        );

      let growthPercentage: number | null = null;
      const baseRaw = valuesByQuarter[1]?.value;
      if (
        typeof latestRaw === "number" &&
        typeof baseRaw === "number" &&
        Number.isFinite(latestRaw) &&
        Number.isFinite(baseRaw) &&
        baseRaw !== 0
      ) {
        growthPercentage = latestRaw / baseRaw - 1;
      }

      return { actualValue, historical, growthPercentage };
    },
    [accounts]
  );

  const basileiaData = getMetricData("INDICE_DE_BASILEIA");
  const imobilizacaoData = getMetricData("INDICE_DE_IMOBILIZACAO");
  const totalAssets = getMetricData("ATIVO_TOTAL");
  const funding = getMetricData("CAPTACOES");
  const creditPortfolio = getMetricData("CARTEIRA_DE_CREDITO");
  const netEquity = getMetricData("PATRIMONIO_LIQUIDO");
  const netProfit = getMetricData("LUCRO_LIQUIDO");
  const referenceEquity = getMetricData(
    "PATRIMONIO_DE_REFERENCIA_PARA_COMPARACAO_COM_O_RWA"
  );

  const netProfitItem: MetricGridItem = {
    type: "metric",
    label: t("lastBalanceSummary.netProfit"),
    actualValue: netProfit.actualValue,
    valueType: "currency",
    growthPercentage: netProfit.growthPercentage,
    historical: netProfit.historical.reverse(),
  };

  const basileiaItem: MetricGridItem | null =
    basileiaData.actualValue != null
      ? {
          type: "metric",
          label: t("lastBalanceSummary.baselIndex"),
          actualValue: basileiaData.actualValue,
          valueType: "percentage",
          growthPercentage: basileiaData.growthPercentage,
          historical: basileiaData.historical.reverse(),
          percentageThresholdValues: {
            bad: BASILEIA_THRESHOLDS.critical,
            regular: BASILEIA_THRESHOLDS.warning,
            good: 100,
          },
          percentageThresholdInverse: false,
        }
      : null;

  const imobilizacaoItem: MetricGridItem | null =
    imobilizacaoData.actualValue != null
      ? {
          type: "metric",
          label: t("lastBalanceSummary.fixedAssetsIndex"),
          actualValue: imobilizacaoData.actualValue,
          valueType: "percentage",
          growthPercentage: imobilizacaoData.growthPercentage,
          historical: imobilizacaoData.historical.reverse(),
          percentageThresholdValues: {
            bad: IMMOBILIZATION_THRESHOLDS.critical,
            regular: IMMOBILIZATION_THRESHOLDS.warning,
            good: IMMOBILIZATION_THRESHOLDS.good,
          },
          percentageThresholdInverse: true,
        }
      : null;

  const totalAssetsItem: MetricGridItem = {
    type: "metric",
    label: t("lastBalanceSummary.totalAssets"),
    actualValue: totalAssets.actualValue,
    valueType: "currency",
    growthPercentage: totalAssets.growthPercentage,
    historical: totalAssets.historical.reverse(),
  };

  const fundingItem: MetricGridItem = {
    type: "metric",
    label: t("lastBalanceSummary.funding"),
    actualValue: funding.actualValue,
    valueType: "currency",
    growthPercentage: funding.growthPercentage,
    historical: funding.historical.reverse(),
  };

  const creditPortfolioItem: MetricGridItem = {
    type: "metric",
    label: t("lastBalanceSummary.creditPortfolio"),
    actualValue: creditPortfolio.actualValue,
    valueType: "currency",
    growthPercentage: creditPortfolio.growthPercentage,
    historical: creditPortfolio.historical.reverse(),
  };

  const netEquityItem: MetricGridItem = {
    type: "metric",
    label: t("lastBalanceSummary.netEquity"),
    actualValue: netEquity.actualValue,
    valueType: "currency",
    growthPercentage: netEquity.growthPercentage,
    historical: netEquity.historical.reverse(),
  };

  const referenceEquityItem: MetricGridItem = {
    type: "metric",
    label: t("lastBalanceSummary.referenceEquity"),
    actualValue: referenceEquity.actualValue,
    valueType: "currency",
    growthPercentage: referenceEquity.growthPercentage,
    historical: referenceEquity.historical.reverse(),
  };

  const primaryGridItems: MetricGridItem[] = [
    netProfitItem,
    ...(basileiaItem ? [basileiaItem] : []),
    ...(imobilizacaoItem ? [imobilizacaoItem] : []),
  ];

  const secondaryGridItems: MetricGridItem[] = [
    totalAssetsItem,
    fundingItem,
    creditPortfolioItem,
    netEquityItem,
    referenceEquityItem,
  ];

  return { primaryGridItems, secondaryGridItems, loading };
}
