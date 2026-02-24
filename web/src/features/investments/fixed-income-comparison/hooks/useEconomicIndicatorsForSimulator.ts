"use client";

import { useQuery } from "@apollo/client/react";
import { useMemo } from "react";
import {
  EconomicIndicatorsLatestDocument,
  EconomicIndicatorsLatestQuery,
} from "../api/queries.generated";

const SLIDER_MIN = -5;
const SLIDER_GAP = 20;

function getLatestValue(data: EconomicIndicatorsLatestQuery | undefined): number | null {
  const indicators = data?.economicIndicators ?? [];
  const indicator = indicators[0];
  const variation = indicator?.variations?.[0];
  const values = variation?.series?.values ?? [];
  if (values.length === 0) return null;
  const sorted = [...values].sort(
    (a, b) => new Date(b.referenceDate as string).getTime() - new Date(a.referenceDate as string).getTime()
  );
  return sorted[0]?.value ?? null;
}

export function useEconomicIndicatorsForSimulator() {
  const { data: cdiData, loading: loadingCdi } = useQuery(EconomicIndicatorsLatestDocument, {
    variables: { indicatorLabel: "CDI", variationLabel: "ROLLING_12M" },
  });

  const { data: ipcaData, loading: loadingIpca } = useQuery(EconomicIndicatorsLatestDocument, {
    variables: { indicatorLabel: "IPCA", variationLabel: "ROLLING_12M" },
  });

  const cdiPercent = useMemo(() => getLatestValue(cdiData), [cdiData]);
  const ipcaPercent = useMemo(() => getLatestValue(ipcaData), [ipcaData]);

  const cdiMin = SLIDER_MIN;
  const cdiMax = useMemo(
    () => (cdiPercent != null ? cdiPercent + SLIDER_GAP : 25),
    [cdiPercent]
  );
  const ipcaMin = SLIDER_MIN;
  const ipcaMax = useMemo(
    () => (ipcaPercent != null ? ipcaPercent + SLIDER_GAP : 25),
    [ipcaPercent]
  );

  return {
    cdiPercent: cdiPercent ?? 0,
    ipcaPercent: ipcaPercent ?? 0,
    cdiMin,
    cdiMax,
    ipcaMin,
    ipcaMax,
    loading: loadingCdi || loadingIpca,
    error: null,
  };
}
