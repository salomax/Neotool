"use client";

import React from "react";
import GridViewIcon from "@mui/icons-material/GridView";
import { Box, LinearProgress, Tab, Tabs } from "@mui/material";
import { DrawerSubmitButton } from "@/shared/components/ui/layout/Drawer";
import { InvestmentAssetIcon } from "../InvestmentAssetIcon";
import { InvestmentTable } from "../table/InvestmentTable";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import { INVESTMENT_GRID_CLASS_DEFINITIONS, type InvestmentGridClassKey } from "./investmentGridConfig";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";

const TAB_PANEL_ID = "investment-grid-class-tabpanel";

/** Tab index 0 = overview (Grid); 1..n match `INVESTMENT_GRID_CLASS_DEFINITIONS` order. */
const GRID_TAB_INDEX = 0;

export interface InvestmentGridTabsPanelProps {
  selectedAssetClassKey: InvestmentGridClassKey;
  onClassChange: (key: InvestmentGridClassKey) => void;
  onBackToGrid: () => void;
  onRecordCta: () => void;
  selectedAccountId: string | null;
  bankAccounts: NonNullable<FinancialAccountsQuery["financialAccounts"]>;
  investmentAccounts: IssuerSelectionAccount[];
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
}

export function InvestmentGridTabsPanel({
  selectedAssetClassKey,
  onClassChange,
  onBackToGrid,
  onRecordCta,
  selectedAccountId,
  bankAccounts,
  investmentAccounts,
  searchQuery,
  onSelectSearchAsset,
}: InvestmentGridTabsPanelProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const [holdingsFetching, setHoldingsFetching] = React.useState(false);

  const activeDefinition = React.useMemo(
    () => INVESTMENT_GRID_CLASS_DEFINITIONS.find((d) => d.key === selectedAssetClassKey) ?? null,
    [selectedAssetClassKey],
  );

  const selectedTabIndex = React.useMemo(() => {
    const idx = INVESTMENT_GRID_CLASS_DEFINITIONS.findIndex((d) => d.key === selectedAssetClassKey);
    return idx >= 0 ? idx + 1 : GRID_TAB_INDEX;
  }, [selectedAssetClassKey]);

  const handleTabsChange = React.useCallback(
    (_: React.SyntheticEvent, value: number) => {
      if (value === GRID_TAB_INDEX) {
        onBackToGrid();
        return;
      }
      const def = INVESTMENT_GRID_CLASS_DEFINITIONS[value - 1];
      if (def) onClassChange(def.key);
    },
    [onBackToGrid, onClassChange],
  );

  return (
    <Box>
      <Box sx={{ position: "relative", mb: 2 }}>
        <Tabs
          value={selectedTabIndex}
          onChange={handleTabsChange}
          variant="scrollable"
          scrollButtons="auto"
          allowScrollButtonsMobile
          sx={{
            borderBottom: 1,
            borderColor: "divider",
            pr: { xs: 15, sm: 18 },
            "& .MuiTab-root": { minHeight: 44 },
          }}
          aria-label={t("investments.grid.tabs.ariaLabel")}
        >
          <Tab
            value={GRID_TAB_INDEX}
            id="investment-grid-class-tab-grid"
            aria-controls={TAB_PANEL_ID}
            label={t("investments.grid.tabs.grid")}
            icon={<GridViewIcon sx={{ fontSize: 20 }} />}
            iconPosition="start"
          />
          {INVESTMENT_GRID_CLASS_DEFINITIONS.map((def, tabIdx) => (
            <Tab
              key={def.key}
              value={tabIdx + 1}
              id={`investment-grid-class-tab-${def.key}`}
              aria-controls={TAB_PANEL_ID}
              label={t(def.titleKey)}
              icon={<InvestmentAssetIcon kind={def.iconKind} sx={{ fontSize: 20 }} />}
              iconPosition="start"
            />
          ))}
        </Tabs>
        <Box
          aria-hidden
          sx={{
            position: "absolute",
            left: 0,
            right: 0,
            bottom: 0,
            height: 4,
            pointerEvents: "none",
            zIndex: 1,
          }}
        >
          <LinearProgress
            sx={{
              height: "100%",
              opacity: holdingsFetching ? 1 : 0,
              transition: "opacity 150ms linear",
            }}
          />
        </Box>
        <DrawerSubmitButton
          type="button"
          size="medium"
          onClick={onRecordCta}
          data-testid="investment-grid-record-cta"
          aria-label={
            selectedAssetClassKey === "cash"
              ? t("investments.grid.addMovementCta")
              : t("investments.grid.firstInvestmentCta")
          }
          sx={{
            position: "absolute",
            right: 8,
            bottom: 10,
            zIndex: 2,
            whiteSpace: "nowrap",
          }}
        >
          {selectedAssetClassKey === "cash"
            ? t("investments.grid.addMovementCta")
            : t("investments.grid.firstInvestmentCta")}
        </DrawerSubmitButton>
      </Box>

      <Box
        role="tabpanel"
        id={TAB_PANEL_ID}
        aria-labelledby={`investment-grid-class-tab-${selectedAssetClassKey}`}
      >
        {activeDefinition ? (
          <InvestmentTable
            key={selectedAssetClassKey}
            selectedAccountId={selectedAccountId}
            bankAccounts={bankAccounts}
            investmentAccounts={investmentAccounts}
            searchQuery={searchQuery}
            onSelectSearchAsset={onSelectSearchAsset}
            visibleGroupKeys={activeDefinition.visibleGroupKeys}
            visibleAssetTypes={activeDefinition.visibleAssetTypes}
            includeCashCard={activeDefinition.includeCashCard ?? false}
            hideGroupHeader
            onHoldingsFetchingChange={setHoldingsFetching}
          />
        ) : null}
      </Box>
    </Box>
  );
}
