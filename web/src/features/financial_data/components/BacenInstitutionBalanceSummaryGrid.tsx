import * as React from "react";
import { Box } from "@mui/material";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";

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

// --- Main Component ---

export const BacenInstitutionBalanceSummaryGrid: React.FC<BacenInstitutionBalanceSummaryGridProps> = ({ institutionId }) => {
  const { t } = useTranslation(financialDataTranslations);

  // Fetch Report Details (Report 1 = Resumo)
  const { data: reportData, loading: reportLoading } = useQuery(BACEN_INSTITUTION_REPORT_DETAILS, {
    variables: {
      id: institutionId,
      reportId: "1", // Resumo
      range: { last: 1 }, // Latest available
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

  // Helper to get value by account number
  const getValue = (accountNumber: string): number | null => {
    const account = accounts.find((a: any) => a.accountNumber === accountNumber);
    if (!account?.valuesByQuarter?.length) return null;
    return account.valuesByQuarter[0].value;
  };
  
  // Basileia
  const basileiaValue = getValue("79664");
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

  // Imobilizacao
  const imobilizacaoValue = getValue("79662");
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

  // Grid items in the exact order from the mock:
  // Row 1: Basileia Index, Ativo Total, Captações, Carteira de Crédito, Patrimônio Líquido
  // Row 2: Índice de Imobilização, Patrimônio de Ref., Lucro Líquido, Nº de Agências, Nº de Pontos Atend.
  const gridItems: MetricGridItem[] = [
    // Row 1
    ...(basileiaValue != null ? [{
      type: 'percentage' as const,
      label: t("lastBalanceSummary.baselIndex"),
      value: basileiaValue,
      thresholds: basileiaThresholds,
      thresholdValues: basileiaThresholdValues,
      inverse: false
    }] : []),
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.totalAssets"),
      value: getValue("140220") ?? getValue("78182"),
      currencyLabels: {
        billions: t("currency.billions"),
        millions: t("currency.millions"),
        billionsLong: t("currency.billionsLong"),
        millionsLong: t("currency.millionsLong"),
      }
    },
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.funding"),
      value: getValue("140239") ?? getValue("78185"),
      currencyLabels: {
        billions: t("currency.billions"),
        millions: t("currency.millions"),
        billionsLong: t("currency.billionsLong"),
        millionsLong: t("currency.millionsLong"),
      }
    },
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.creditPortfolio"),
      value: getValue("141873") ?? getValue("78183"),
      currencyLabels: {
        billions: t("currency.billions"),
        millions: t("currency.millions"),
        billionsLong: t("currency.billionsLong"),
        millionsLong: t("currency.millionsLong"),
      }
    },
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.netEquity"),
      value: getValue("140246") ?? getValue("78186"),
      currencyLabels: {
        billions: t("currency.billions"),
        millions: t("currency.millions"),
        billionsLong: t("currency.billionsLong"),
        millionsLong: t("currency.millionsLong"),
      }
    },
    // Row 2
    ...(imobilizacaoValue != null ? [{
      type: 'percentage' as const,
      label: t("lastBalanceSummary.fixedAssetsIndex"),
      value: imobilizacaoValue,
      thresholds: imobilizacaoThresholds,
      thresholdValues: imobilizacaoThresholdValues,
      inverse: true
    }] : []),
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.referenceEquity"),
      value: institution?.rwaReferenceEquity ?? null,
      currencyLabels: {
        billions: t("currency.billions"),
        millions: t("currency.millions"),
        billionsLong: t("currency.billionsLong"),
        millionsLong: t("currency.millionsLong"),
      }
    },
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.netProfit"),
      value: getValue("141870") ?? getValue("78187"),
      currencyLabels: {
        billions: t("currency.billions"),
        millions: t("currency.millions"),
        billionsLong: t("currency.billionsLong"),
        millionsLong: t("currency.millionsLong"),
      }
    },
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.numberOfBranches"),
      value: null, // Not available in data model yet
      isNumber: true
    },
    { 
      type: 'metric' as const,
      label: t("lastBalanceSummary.numberOfServicePoints"),
      value: null, // Not available in data model yet
      isNumber: true
    },
  ];

  if (!institutionId) return null;

  return (
    <Box sx={{ mt: 4 }}>
      <MetricGrid
        header={{
          title: t("lastBalanceSummary.title"),
          icon: <TrendingUpIcon color="primary" />,
          tooltip: t("lastBalanceSummary.tooltip")
        }}
        items={gridItems}
        loading={loading}
        columns={{ xs: 1, sm: 2, md: 5 }}
        gap={2}
        skeletonHeights={{ metric: 120, percentage: 140 }}
      />
    </Box>
  );
};
