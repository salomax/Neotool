"use client";

import type { ErrorLike } from "@apollo/client";
import React from "react";
import { buildChartSeriesByClass } from "./investmentGridChart";
import { buildGridMetricsAndCounts, type GridHoldingRow } from "./investmentGridBuckets";
import {
  INVESTMENT_GRID_CLASS_DEFINITIONS,
  RECORD_DRAWER_BY_CLASS,
  type GridRecordDrawer,
  type InvestmentGridClassDefinition,
  type InvestmentGridClassKey,
} from "./investmentGridConfig";
import { useInvestmentGridData } from "./useInvestmentGridData";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";

const SELECTED_CLASS_STORAGE_PREFIX = "investments.grid.selectedAssetClassKey";

function buildSelectedClassStorageKey(financialAccountId: string | null): string {
  return `${SELECTED_CLASS_STORAGE_PREFIX}:${financialAccountId ?? "all"}`;
}

function parseInvestmentGridClassKey(raw: string | null): InvestmentGridClassKey | null {
  if (!raw) return null;
  const match =
    INVESTMENT_GRID_CLASS_DEFINITIONS.find((item) => item.key === raw)?.key ?? null;
  return match;
}

function readSelectedAssetClassKey(storageKey: string): InvestmentGridClassKey | null {
  try {
    return parseInvestmentGridClassKey(sessionStorage.getItem(storageKey));
  } catch {
    return null;
  }
}

export interface UseInvestmentGridResult {
  data: ReturnType<typeof useInvestmentGridData>["data"];
  loading: boolean;
  error: ErrorLike | undefined;
  refetch: ReturnType<typeof useInvestmentGridData>["refetch"];
  refreshGridQueries: ReturnType<typeof useInvestmentGridData>["refreshGridQueries"];
  holdings: readonly GridHoldingRow[];
  metricsByKey: ReturnType<typeof buildGridMetricsAndCounts>["metricsByKey"];
  assetCountByKey: ReturnType<typeof buildGridMetricsAndCounts>["assetCountByKey"];
  chartSeriesByKey: Record<InvestmentGridClassKey, TimeSeriesDataPoint[]>;
  selectedAssetClassKey: InvestmentGridClassKey | null;
  selectedClassDefinition: InvestmentGridClassDefinition | null;
  recordDrawer: GridRecordDrawer;
  selectAssetClass: (key: InvestmentGridClassKey) => void;
  clearAssetClass: () => void;
  openRecordDrawerForCash: () => void;
  openRecordDrawerForClass: (key: InvestmentGridClassKey) => void;
  closeRecordDrawer: () => void;
  openRecordForSelectedClass: () => void;
}

export function useInvestmentGrid(financialAccountId: string | null): UseInvestmentGridResult {
  const { data: gridData, loading: gridLoading, error: gridError, refetch, refreshGridQueries } =
    useInvestmentGridData(financialAccountId);

  const selectedClassStorageKey = React.useMemo(
    () => buildSelectedClassStorageKey(financialAccountId),
    [financialAccountId],
  );

  const [selectedAssetClassKey, setSelectedAssetClassKey] = React.useState<InvestmentGridClassKey | null>(() => {
    if (typeof window === "undefined") return null;
    return readSelectedAssetClassKey(buildSelectedClassStorageKey(financialAccountId));
  });
  const [recordDrawer, setRecordDrawer] = React.useState<GridRecordDrawer>(null);

  React.useEffect(() => {
    setSelectedAssetClassKey(readSelectedAssetClassKey(selectedClassStorageKey));
  }, [selectedClassStorageKey]);

  const openRecordDrawerForClassKey = React.useCallback((key: InvestmentGridClassKey) => {
    const drawer = RECORD_DRAWER_BY_CLASS[key];
    setRecordDrawer(drawer);
  }, []);

  const closeRecordDrawer = React.useCallback(() => {
    setRecordDrawer(null);
  }, []);

  const openRecordForSelectedClass = React.useCallback(() => {
    if (!selectedAssetClassKey) return;
    openRecordDrawerForClassKey(selectedAssetClassKey);
  }, [openRecordDrawerForClassKey, selectedAssetClassKey]);

  const selectAssetClass = React.useCallback(
    (key: InvestmentGridClassKey) => {
      setSelectedAssetClassKey(key);
      try {
        sessionStorage.setItem(selectedClassStorageKey, key);
      } catch {
        // ignore
      }
    },
    [selectedClassStorageKey],
  );

  const clearAssetClass = React.useCallback(() => {
    setSelectedAssetClassKey(null);
    try {
      sessionStorage.removeItem(selectedClassStorageKey);
    } catch {
      // ignore
    }
  }, [selectedClassStorageKey]);

  const openRecordDrawerForCash = React.useCallback(() => {
    setRecordDrawer("cash");
  }, []);

  const openRecordDrawerForClass = React.useCallback(
    (key: InvestmentGridClassKey) => {
      openRecordDrawerForClassKey(key);
    },
    [openRecordDrawerForClassKey],
  );

  const holdings = React.useMemo(() => gridData?.investmentHoldings ?? [], [gridData?.investmentHoldings]);

  const { metricsByKey, assetCountByKey } = React.useMemo(
    () => buildGridMetricsAndCounts(holdings, gridData?.investmentCashBalance),
    [holdings, gridData?.investmentCashBalance],
  );

  const selectedClassDefinition = React.useMemo(
    () => INVESTMENT_GRID_CLASS_DEFINITIONS.find((item) => item.key === selectedAssetClassKey) ?? null,
    [selectedAssetClassKey],
  );

  const chartSeriesByKey = React.useMemo(() => buildChartSeriesByClass(gridData), [gridData]);

  return {
    data: gridData,
    loading: gridLoading,
    error: gridError,
    refetch,
    refreshGridQueries,
    holdings,
    metricsByKey,
    assetCountByKey,
    chartSeriesByKey,
    selectedAssetClassKey,
    selectedClassDefinition,
    recordDrawer,
    selectAssetClass,
    clearAssetClass,
    openRecordDrawerForCash,
    openRecordDrawerForClass,
    closeRecordDrawer,
    openRecordForSelectedClass,
  };
}
