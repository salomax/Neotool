"use client";

import React from "react";
import ViewModuleIcon from "@mui/icons-material/ViewModule";
import { useQuery } from "@apollo/client/react";
import { Badge, Box, ButtonBase, LinearProgress, Popover, Tab, Tabs, Typography, alpha, useTheme } from "@mui/material";
import { DrawerSubmitButton } from "@/shared/components/ui/layout/Drawer";
import { ExpandableSearchInput } from "@/shared/components/ui/forms/ExpandableSearchInput";
import { Logo } from "@/shared/components/ui/primitives";
import { LoadingSpinner } from "@/shared/components/ui/primitives/LoadingSpinner";
import { InvestmentAssetIcon } from "../InvestmentAssetIcon";
import { InvestmentTable } from "../table/InvestmentTable";
import { INVESTMENT_HOLDINGS } from "@/lib/graphql/operations/banking/queries";
import type { InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables } from "@/lib/graphql/operations/banking/queries.generated";
import type { IssuerSelectionAccount } from "../IssuerSelection";
import { INVESTMENT_GRID_CLASS_DEFINITIONS, type InvestmentGridClassKey } from "./investmentGridConfig";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import { getAssetGroupKey } from "../table/investmentTableHelpers";

const TAB_PANEL_ID = "investment-grid-class-tabpanel";

/** Tab index 0 = overview (Grid); 1..n match `INVESTMENT_GRID_CLASS_DEFINITIONS` order. */
const GRID_TAB_INDEX = 0;
const EMPTY_HOLDINGS: InvestmentHoldingsQuery["investmentHoldings"] = [];

export interface InvestmentGridTabsPanelProps {
  selectedAssetClassKey: InvestmentGridClassKey;
  onClassChange: (key: InvestmentGridClassKey) => void;
  onBackToGrid: () => void;
  onOpenRecordDrawerForClass: (key: InvestmentGridClassKey) => void;
  selectedAccountId: string | null;
  investmentAccounts: IssuerSelectionAccount[];
  searchQuery?: string;
  onSelectSearchAsset?: (assetName: string) => void;
}

export function InvestmentGridTabsPanel({
  selectedAssetClassKey,
  onClassChange,
  onBackToGrid,
  onOpenRecordDrawerForClass,
  selectedAccountId,
  investmentAccounts,
  searchQuery,
  onSelectSearchAsset,
}: InvestmentGridTabsPanelProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [holdingsFetching, setHoldingsFetching] = React.useState(false);
  const [recordPopoverAnchorEl, setRecordPopoverAnchorEl] = React.useState<HTMLElement | null>(null);
  const recordDisabled = selectedAccountId == null;

  const recordPopoverOpen = Boolean(recordPopoverAnchorEl);
  const recordPopoverId = recordPopoverOpen ? "investment-grid-record-popover" : undefined;

  const handleOpenRecordPopover = React.useCallback((event: React.MouseEvent<HTMLElement>) => {
    if (recordDisabled) return;
    setRecordPopoverAnchorEl(event.currentTarget);
  }, [recordDisabled]);

  const handleCloseRecordPopover = React.useCallback(() => {
    setRecordPopoverAnchorEl(null);
  }, []);

  const handleSelectRecordClass = React.useCallback(
    (key: InvestmentGridClassKey) => {
      handleCloseRecordPopover();
      onClassChange(key);
      onOpenRecordDrawerForClass(key);
    },
    [handleCloseRecordPopover, onClassChange, onOpenRecordDrawerForClass],
  );

  const holdingsQuery = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(INVESTMENT_HOLDINGS, {
    variables: { financialAccountId: selectedAccountId ?? null },
    errorPolicy: "all",
  });

  const holdings = React.useMemo(
    () => holdingsQuery.data?.investmentHoldings ?? EMPTY_HOLDINGS,
    [holdingsQuery.data?.investmentHoldings],
  );
  const tabsLoading = holdingsQuery.loading && holdingsQuery.data?.investmentHoldings == null;
  const normalizedSearchQuery = React.useMemo(() => (searchQuery ?? "").trim().toLowerCase(), [searchQuery]);
  const hasSearchQuery = Boolean(normalizedSearchQuery);

  const scopeHoldingsForDefinition = React.useCallback(
    (def: (typeof INVESTMENT_GRID_CLASS_DEFINITIONS)[number]) => {
      const visibleAssetTypes = def.visibleAssetTypes;
      const visibleGroupKeys = def.visibleGroupKeys;

      const scopedByAssetType =
        visibleAssetTypes && visibleAssetTypes.length > 0
          ? holdings.filter((holding) => {
              const assetType = holding.asset?.assetType;
              return assetType ? visibleAssetTypes.includes(assetType) : false;
            })
          : holdings;

      if (visibleGroupKeys === undefined) return scopedByAssetType;
      if (visibleGroupKeys.length === 0) return EMPTY_HOLDINGS;
      return scopedByAssetType.filter((holding) => visibleGroupKeys.includes(getAssetGroupKey(holding.asset?.assetType)));
    },
    [holdings],
  );

  const holdingsCountByClassKey = React.useMemo(() => {
    const result = new Map<InvestmentGridClassKey, number>();
    INVESTMENT_GRID_CLASS_DEFINITIONS.forEach((def) => {
      result.set(def.key, scopeHoldingsForDefinition(def).length);
    });
    return result;
  }, [scopeHoldingsForDefinition]);

  const visibleTabDefinitions = React.useMemo(
    () =>
      INVESTMENT_GRID_CLASS_DEFINITIONS.filter((def) => {
        if (def.key === "cash") return true;
        return (holdingsCountByClassKey.get(def.key) ?? 0) > 0;
      }),
    [holdingsCountByClassKey],
  );

  const matchCountByClassKey = React.useMemo(() => {
    if (!hasSearchQuery) return new Map<InvestmentGridClassKey, number>();

    const result = new Map<InvestmentGridClassKey, number>();

    visibleTabDefinitions.forEach((def) => {
      const scoped = scopeHoldingsForDefinition(def);
      const matched = scoped.filter((holding) => {
        const haystack = [
          holding.asset?.name ?? "",
          holding.asset?.ticker ?? "",
          holding.asset?.issuerName ?? "",
          holding.asset?.assetType ?? "",
          holding.financialAccount?.name ?? "",
        ]
          .join(" ")
          .toLowerCase();

        return haystack.includes(normalizedSearchQuery);
      });

      result.set(def.key, matched.length);
    });

    return result;
  }, [hasSearchQuery, normalizedSearchQuery, scopeHoldingsForDefinition, visibleTabDefinitions]);

  const activeDefinition = React.useMemo(
    () => visibleTabDefinitions.find((d) => d.key === selectedAssetClassKey) ?? null,
    [selectedAssetClassKey, visibleTabDefinitions],
  );

  const selectedTabIndex = React.useMemo(() => {
    const idx = visibleTabDefinitions.findIndex((d) => d.key === selectedAssetClassKey);
    return idx >= 0 ? idx + 1 : GRID_TAB_INDEX;
  }, [selectedAssetClassKey, visibleTabDefinitions]);

  React.useEffect(() => {
    if (tabsLoading || holdingsQuery.error) return;
    if (selectedAssetClassKey === "cash") return;
    if (visibleTabDefinitions.some((def) => def.key === selectedAssetClassKey)) return;
    onClassChange("cash");
  }, [
    holdingsQuery.error,
    onClassChange,
    selectedAssetClassKey,
    tabsLoading,
    visibleTabDefinitions,
  ]);

  const handleTabsChange = React.useCallback(
    (_: React.SyntheticEvent, value: number) => {
      if (value === GRID_TAB_INDEX) {
        onBackToGrid();
        return;
      }
      const def = visibleTabDefinitions[value - 1];
      if (def) onClassChange(def.key);
    },
    [onBackToGrid, onClassChange, visibleTabDefinitions],
  );

  return (
    <Box>
      {tabsLoading ? <LoadingSpinner message={t("actions.loading")} minHeight={220} /> : null}
      {!tabsLoading ? (
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
            pr: { xs: 24, sm: 30 },
            "& .MuiTab-root": {
              minHeight: 44,
              overflow: "visible",
              transition: "color 150ms linear",
            },
            "& .MuiTabs-flexContainer": {
              overflow: "visible",
            },
            "& .MuiTab-root:not(.Mui-selected) .investmentTabLabel": {
              opacity: 0.65,
            },
            "& .MuiTab-root:not(.Mui-selected) .MuiTab-iconWrapper": {
              opacity: 0.65,
            },
            "& .MuiTab-root.Mui-selected .MuiTab-iconWrapper": {
              opacity: 1,
            },
            "& .MuiTab-root.Mui-selected .investmentTabLabel": {
              opacity: 1,
            },
            "& .investmentTabLabel": {
              display: "inline-flex",
              alignItems: "center",
              transition: "opacity 150ms linear",
            },
          }}
          aria-label={t("investments.grid.tabs.ariaLabel")}
        >
          <Tab
            value={GRID_TAB_INDEX}
            id="investment-grid-class-tab-grid"
            aria-controls={TAB_PANEL_ID}
            aria-label={t("investments.grid.tabs.grid")}
            icon={<ViewModuleIcon sx={{ fontSize: 20 }} />}
            sx={{
              minWidth: 48,
              px: 1,
              opacity: 0.65,
              "& .MuiTab-iconWrapper": {
                transition: "opacity 150ms linear",
              },
            }}
          />
          {visibleTabDefinitions.map((def, tabIdx) => (
            <Tab
              key={def.key}
              value={tabIdx + 1}
              id={`investment-grid-class-tab-${def.key}`}
              aria-controls={TAB_PANEL_ID}
              label={
                <Badge
                  color="primary"
                  badgeContent={matchCountByClassKey.get(def.key) ?? 0}
                  invisible={!hasSearchQuery || (matchCountByClassKey.get(def.key) ?? 0) === 0}
                  sx={{
                    "& .MuiBadge-badge": {
                      top: 2,
                      right: -12,
                      zIndex: 2,
                    },
                  }}
                >
                  <Box component="span" className="investmentTabLabel">
                    {t(def.titleKey)}
                  </Box>
                </Badge>
              }
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
        <Box
          sx={{
            position: "absolute",
            right: 8,
            bottom: 10,
            zIndex: 2,
            display: "flex",
            alignItems: "center",
            gap: 2,
          }}
        >
          <ExpandableSearchInput
            searchQuery={searchQuery ?? ""}
            onSearchQueryChange={(q: string) => onSelectSearchAsset?.(q)}
            placeholder={t("investments.table.searchPlaceholder")}
            ariaClearLabel={t("investments.table.actions.clearSearchAriaLabel")}
            sx={{
              "& .MuiOutlinedInput-root": {
                bgcolor: "background.default",
              },
            }}
          />
          <DrawerSubmitButton
            type="button"
            size="medium"
            onClick={handleOpenRecordPopover}
            disabled={recordDisabled}
            data-testid="investment-grid-record-cta"
            aria-haspopup="dialog"
            aria-controls={recordPopoverId}
            aria-expanded={recordPopoverOpen ? "true" : "false"}
            aria-label={
              selectedAssetClassKey === "cash"
                ? t("investments.grid.addMovementCta")
                : t("investments.grid.firstInvestmentCta")
            }
            sx={{
              whiteSpace: "nowrap",
            }}
          >
            {selectedAssetClassKey === "cash"
              ? t("investments.grid.addMovementCta")
              : t("investments.grid.firstInvestmentCta")}
          </DrawerSubmitButton>
          <Popover
            id={recordPopoverId}
            open={recordPopoverOpen}
            anchorEl={recordPopoverAnchorEl}
            onClose={handleCloseRecordPopover}
            anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
            transformOrigin={{ vertical: "top", horizontal: "right" }}
            PaperProps={{
              "aria-label": t("investments.grid.recordPopover.ariaLabel"),
              sx: {
                mt: 1,
                p: 2,
                width: 332,
                maxWidth: "calc(100vw - 32px)",
                borderRadius: 3,
                border: `1px solid ${alpha(theme.palette.divider, 0.85)}`,
                boxShadow:
                  theme.palette.mode === "dark"
                    ? `0 18px 48px ${alpha(theme.palette.common.black, 0.52)}`
                    : `0 18px 48px ${alpha(theme.palette.common.black, 0.18)}`,
              },
            }}
          >
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                gap: 1.5,
              }}
            >
              {INVESTMENT_GRID_CLASS_DEFINITIONS.map((def) => {
                return (
                  <ButtonBase
                    key={def.key}
                    onClick={() => handleSelectRecordClass(def.key)}
                    data-testid={`investment-grid-record-option-${def.key}`}
                    sx={{
                      borderRadius: 2,
                      border: `1px solid ${alpha(theme.palette.divider, 0.9)}`,
                      bgcolor: "background.paper",
                      py: 2,
                      px: 1.5,
                      display: "flex",
                      flexDirection: "column",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: 1,
                      minHeight: 96,
                      transition: "box-shadow 180ms ease, transform 180ms ease, border-color 180ms ease",
                      "&:hover": {
                        boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.12)}`,
                        borderColor: alpha(theme.palette.primary.main, 0.35),
                        transform: "translateY(-1px)",
                      },
                      "&:focus-visible": {
                        outline: `2px solid ${alpha(theme.palette.primary.main, 0.45)}`,
                        outlineOffset: 2,
                      },
                    }}
                  >
                    <Logo
                      size="md"
                      sx={{
                        bgcolor: alpha(theme.palette.primary.main, 0.08),
                        color: "primary.main",
                      }}
                    >
                      <InvestmentAssetIcon kind={def.iconKind} sx={{ fontSize: 20 }} />
                    </Logo>
                    <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.2, textAlign: "center" }}>
                      {t(def.titleKey)}
                    </Typography>
                  </ButtonBase>
                );
              })}
            </Box>
          </Popover>
        </Box>
      </Box>
      ) : null}

      {!tabsLoading ? (
        <Box
          role="tabpanel"
          id={TAB_PANEL_ID}
          aria-labelledby={
            selectedTabIndex === GRID_TAB_INDEX
              ? "investment-grid-class-tab-grid"
              : `investment-grid-class-tab-${selectedAssetClassKey}`
          }
        >
          {activeDefinition ? (
            <InvestmentTable
              key={selectedAssetClassKey}
              selectedAccountId={selectedAccountId}
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
      ) : null}
    </Box>
  );
}
