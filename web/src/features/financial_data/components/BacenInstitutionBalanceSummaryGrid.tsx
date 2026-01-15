import * as React from "react";
import { Box } from "@mui/material";

import { useQuery } from "@apollo/client/react";
import { BACEN_INSTITUTION_REPORT_DETAILS, BACEN_INSTITUTION } from "@/lib/graphql/operations/financial_data/bacen-institutions";
import { MetricGrid, MetricGridItem } from "@/shared/components/ui/data-display";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { BASILEIA_THRESHOLDS, IMMOBILIZATION_THRESHOLDS } from "@/features/financial_data/constants/metrics";

// --- Types & Interfaces ---

interface BacenInstitutionBalanceSummaryGridProps {
  institutionId: string;
}

const REPORT_ID_RESUMO = "1";

export const BacenInstitutionBalanceSummaryGrid: React.FC<BacenInstitutionBalanceSummaryGridProps> = ({ institutionId }) => {
  const { t } = useTranslation(financialDataTranslations);

  // Fetch Report Details (Report 1 = Resumo)
  const { data: reportData, loading: reportLoading } = useQuery(BACEN_INSTITUTION_REPORT_DETAILS, {
    variables: {
      id: institutionId,
      reportId: REPORT_ID_RESUMO, // Resumo
      range: { last: 5 * 4 }, // 5 years * 4 quarters per year
    },
    skip: !institutionId,
  });

  // Fetch institution data for rwaReferenceEquity
  const { data: institutionData, loading: institutionLoading } = useQuery(BACEN_INSTITUTION, {
    variables: {
      id: institutionId,
    },
    skip: !institutionId,
  });

  const loading = reportLoading || institutionLoading;
  const accounts = (reportData as any)?.bacenFinanceInstitution?.reportDetails?.accounts || [];
  const institution = (institutionData as any)?.bacenFinanceInstitution;

  const getMetricData = (accountName: string): { actualValue: number | null; historical: number[]; growthPercentage: number | null } => {
    const account = accounts.find((a: any) => a.id === accountName);
    const valuesByQuarter = account?.valuesByQuarter ?? [];

    const latestRaw = valuesByQuarter[0]?.value;
    const actualValue =
      typeof latestRaw === "number" && Number.isFinite(latestRaw) ? latestRaw : null;

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
  };

  const getLatestValue = (accountName: string): number | null => {
    const account = accounts.find((a: any) => a.id === accountName);
    if (!account?.valuesByQuarter?.length) return null;
    const latest = account.valuesByQuarter[0]?.value;
    return typeof latest === "number" && Number.isFinite(latest) ? latest : null;
  };
  
  const basileiaValue = getLatestValue("INDICE_DE_BASILEIA");
  const basileiaThresholds = {
    bad: (v: number) => v < BASILEIA_THRESHOLDS.critical,
    regular: (v: number) => v >= BASILEIA_THRESHOLDS.critical && v <= BASILEIA_THRESHOLDS.warning,
    good: (v: number) => v > BASILEIA_THRESHOLDS.warning
  };
  const basileiaThresholdValues = {
    bad: BASILEIA_THRESHOLDS.critical,
    regular: BASILEIA_THRESHOLDS.warning,
    good: 100
  };

  const imobilizacaoValue = getLatestValue("INDICE_DE_IMOBILIZACAO");
  const imobilizacaoThresholds = {
    bad: (v: number) => v > IMMOBILIZATION_THRESHOLDS.warning,
    regular: (v: number) => v >= IMMOBILIZATION_THRESHOLDS.good && v <= IMMOBILIZATION_THRESHOLDS.warning,
    good: (v: number) => v < IMMOBILIZATION_THRESHOLDS.good
  };
  const imobilizacaoThresholdValues = {
    bad: IMMOBILIZATION_THRESHOLDS.critical,
    regular: IMMOBILIZATION_THRESHOLDS.warning,
    good: IMMOBILIZATION_THRESHOLDS.good
  };

  const totalAssets = getMetricData("ATIVO_TOTAL");
  const funding = getMetricData("CAPTACOES");
  const creditPortfolio = getMetricData("CARTEIRA_DE_CREDITO");
  const netEquity = getMetricData("PATRIMONIO_LIQUIDO");
  const netProfit = getMetricData("LUCRO_LIQUIDO");
  const referenceEquity = getMetricData("PATRIMONIO_DE_REFERENCIA");

  const netProfitItem: MetricGridItem = {
    type: 'metric',
    label: t("lastBalanceSummary.netProfit"),
    actualValue: netProfit.actualValue,
    valueType: 'currency',
    growthPercentage: netProfit.growthPercentage,
    historical: netProfit.historical.reverse(),
  };

  const basileiaItem: MetricGridItem | null =
    basileiaValue != null
      ? {
          type: 'percentage',
          label: t("lastBalanceSummary.baselIndex"),
          value: basileiaValue * 100,
          thresholds: basileiaThresholds,
          thresholdValues: basileiaThresholdValues,
          inverse: false,
        }
      : null;

  const imobilizacaoItem: MetricGridItem | null =
    imobilizacaoValue != null
      ? {
          type: 'percentage',
          label: t("lastBalanceSummary.fixedAssetsIndex"),
          value: imobilizacaoValue * 100,
          thresholds: imobilizacaoThresholds,
          thresholdValues: imobilizacaoThresholdValues,
          inverse: true,
        }
      : null;

  const totalAssetsItem: MetricGridItem = {
    type: 'metric',
    label: t("lastBalanceSummary.totalAssets"),
    actualValue: totalAssets.actualValue,
    valueType: 'currency',
    growthPercentage: totalAssets.growthPercentage,
    historical: totalAssets.historical.reverse(),
  };

  const fundingItem: MetricGridItem = {
    type: 'metric',
    label: t("lastBalanceSummary.funding"),
    actualValue: funding.actualValue,
    valueType: 'currency',
    growthPercentage: funding.growthPercentage,
    historical: funding.historical.reverse(),
  };

  const creditPortfolioItem: MetricGridItem = {
    type: 'metric',
    label: t("lastBalanceSummary.creditPortfolio"),
    actualValue: creditPortfolio.actualValue,
    valueType: 'currency',
    growthPercentage: creditPortfolio.growthPercentage,
    historical: creditPortfolio.historical.reverse(),
  };

  const netEquityItem: MetricGridItem = {
    type: 'metric',
    label: t("lastBalanceSummary.netEquity"),
    actualValue: netEquity.actualValue,
    valueType: 'currency',
    growthPercentage: netEquity.growthPercentage,
    historical: netEquity.historical.reverse(),
  };

  const referenceEquityItem: MetricGridItem = {
    type: 'metric',
    label: t("lastBalanceSummary.referenceEquity"),
    actualValue: institution?.rwaReferenceEquity ?? null,
    valueType: 'currency',
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

  if (!institutionId) return null;

  return (
    <Box sx={{ mt: 4 }}>
      <MetricGrid
        header={{
          title: t("lastBalanceSummary.title"),
          tooltip: t("lastBalanceSummary.tooltip")
        }}
        items={primaryGridItems}
        loading={loading}
        templateColumns={{
          xs: "repeat(1, 1fr)",
          sm: "repeat(1, 1fr)",
          md: "4fr 3fr 3fr",
          lg: "4fr 3fr 3fr",
        }}
        gap={2}
        skeletonHeights={{ metric: 140, percentage: 140 }}
      />
      <Box sx={{ mt: 2 }}>
        <MetricGrid
          items={secondaryGridItems}
          loading={loading}
          columns={{ xs: 1, sm: 2, md: 5 }}
          gap={2}
          skeletonHeights={{ metric: 140, percentage: 140 }}
        />
      </Box>
    </Box>
  );
};
