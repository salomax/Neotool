"use client";

import React from "react";
import { Box, Typography, alpha, useTheme } from "@mui/material";
import { Chip } from "@/shared/components/ui/primitives/Chip";
import { PercentageGain, ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { formatTableDate } from "@/shared/utils/date";
import { getHoldingValueOrigin } from "@/shared/utils/holdingValueOrigin";
import { FinancialAccountIdentity } from "@/shared/components/ui/data-display/FinancialAccountIdentity";
import { FixedIncomeAssetIdentity, resolveHoldingIdentity } from "../HoldingIdentity";
import {
  computePrincipal,
  formatPercentageRate,
  formatValueOrPlaceholder,
  type InvestmentTableTranslate,
} from "./investmentTableRowHelpers";
import type { FixedIncomeTableLayout } from "./fixedIncomeTableLayout";
import type { HoldingRow } from "./investmentTableHelpers";
import { HoldingRowActionsColumn } from "./HoldingRowActionsColumn";

const SECONDARY_ROW_SX = { mt: 0.25, minHeight: 16, display: "flex", alignItems: "center" } as const;

export interface FixedIncomeHoldingRowProps {
  holding: HoldingRow;
  layout: FixedIncomeTableLayout;
  locale: string;
  dividerColor: string;
  hoverColor: string;
  t: InvestmentTableTranslate;
  onOpenDetail: (holding: HoldingRow) => void;
  onOpenMaturityConfirm: (holding: HoldingRow) => void;
  onOpenActions: (anchorEl: HTMLElement, holding: HoldingRow) => void;
  onSell?: (holding: HoldingRow) => void;
  sellLabel?: string;
}

export const FixedIncomeHoldingRow = React.memo(function FixedIncomeHoldingRow({
  holding,
  layout,
  locale,
  dividerColor,
  hoverColor,
  t,
  onOpenDetail,
  onOpenMaturityConfirm,
  onOpenActions,
  onSell,
  sellLabel,
}: FixedIncomeHoldingRowProps) {
  const theme = useTheme();
  const [hovered, setHovered] = React.useState(false);

  const maturityChipSx = React.useMemo(() => {
    const warningMain = theme.palette.warning.main;
    const warningText = theme.palette.mode === "dark" ? theme.palette.warning.light : theme.palette.warning.dark;

    return {
      height: 28,
      color: warningText,
      bgcolor: alpha(warningMain, theme.palette.mode === "dark" ? 0.18 : 0.12),
      borderColor: alpha(warningMain, theme.palette.mode === "dark" ? 0.38 : 0.3),
      borderWidth: 1,
      borderStyle: "solid",
      "&:hover": {
        bgcolor: alpha(warningMain, theme.palette.mode === "dark" ? 0.3 : 0.22),
        borderColor: alpha(warningMain, theme.palette.mode === "dark" ? 0.55 : 0.5),
      },
      transition: "background-color 0.15s ease, border-color 0.15s ease",
      "& .MuiChip-label": { px: 1, minWidth: 0, maxWidth: "100%" },
    } as const;
  }, [theme]);

  const assetType = holding.asset?.assetType ?? null;
  const maturityLabel = holding.maturityDate ? formatTableDate(String(holding.maturityDate), locale) : "—";

  const principal = computePrincipal(holding);
  const gross = typeof holding.grossValue === "number" ? holding.grossValue : null;
  const gain = gross != null && principal != null ? gross - principal : null;
  const gainPercent = gross != null && principal != null && principal > 0 ? (gross - principal) / principal : null;
  const valueOriginKey = getHoldingValueOrigin(holding);

  const isExpiredPending = Boolean(holding.isExpiredPending);
  const isExpired = Boolean(holding.isExpired);
  const showMaturityBadge = isExpiredPending || isExpired;
  const maturityBadgeLabel = isExpiredPending
    ? t("investments.table.maturityPendingBadge")
    : t("investments.table.expiredBadge");

  const indexerLabel = holding.indexer != null ? t(`investments.recordHolding.indexers.${holding.indexer}`) : null;
  const indexerRateLabel =
    typeof holding.indexerPercentage === "number"
      ? formatPercentageRate(holding.indexerPercentage, locale)
      : typeof holding.fixedAnnualRate === "number"
        ? formatPercentageRate(holding.fixedAnnualRate, locale)
        : null;
  const indexerDisplay = [indexerLabel, indexerRateLabel].filter(Boolean).join(" ");

  const identity = resolveHoldingIdentity(holding);
  const custodianAccount = holding.financialAccount;

  return (
    <Box
      role="row"
      onClick={() => onOpenDetail(holding)}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      sx={{
        display: "flex",
        alignItems: "center",
        px: 2,
        py: 1.5,
        bgcolor: "background.paper",
        borderTop: `1px solid ${dividerColor}`,
        cursor: "pointer",
        "&:hover": { bgcolor: hoverColor },
      }}
    >
      <Box sx={{ ...layout.assetColSx, minWidth: 0 }}>
        <FixedIncomeAssetIdentity
          assetType={assetType}
          assetName={identity.assetName}
          issuerName={identity.issuerName}
          size={32}
          data-testid="fixed-income-type-avatar"
        />
      </Box>

      {layout.showConsolidatedCustodianColumn ? (
        <Box sx={{ ...layout.custodianColSx, minWidth: 0 }}>
          <FinancialAccountIdentity
            account={custodianAccount ?? undefined}
            justify="flex-start"
            avatarSize={24}
            nameTitle={custodianAccount?.name ?? holding.financialAccountId}
            data-testid="holding-custodian-account"
          />
        </Box>
      ) : null}

      <Box sx={{ ...layout.indexerColSx, minWidth: 0 }}>
        <Typography variant="body2" noWrap>
          {indexerDisplay || "—"}
        </Typography>
        <Box sx={SECONDARY_ROW_SX} />
      </Box>

      <Box sx={{ ...layout.maturityColSx, minWidth: 0, textAlign: "center" }}>
        <Typography variant="body2" noWrap sx={{ textAlign: "center" }}>
          {maturityLabel}
        </Typography>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: "center" }}>
          {showMaturityBadge ? (
            <Chip
              onClick={(event) => {
                event.stopPropagation();
                onOpenMaturityConfirm(holding);
              }}
              label={
                <Typography variant="body2" sx={{ fontSize: "0.75rem", lineHeight: 1.2, fontWeight: 600 }}>
                  {maturityBadgeLabel}
                </Typography>
              }
              size="small"
              variant="filled"
              clickable
              sx={maturityChipSx}
            />
          ) : null}
        </Box>
      </Box>

      <Box sx={{ ...layout.gainColSx, minWidth: 0, textAlign: "right" }}>
        <Typography variant="body2" noWrap>
          {formatValueOrPlaceholder(gain, holding.asset?.currencyCode, locale)}
        </Typography>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: "flex-end" }}>
          {gainPercent != null ? <PercentageGain value={gainPercent} locale={locale} textSx={{ lineHeight: 1.2 }} /> : null}
        </Box>
      </Box>

      <Box sx={{ ...layout.grossValueColSx, minWidth: 0, textAlign: "right" }}>
        <Box
          sx={{
            position: "relative",
            display: "flex",
            alignItems: "center",
            justifyContent: "flex-end",
            width: "100%",
            overflow: "visible",
          }}
        >
          <Typography variant="body2" noWrap>
            {formatValueOrPlaceholder(gross, holding.asset?.currencyCode, locale)}
          </Typography>
          {gross != null ? (
            <ValueOriginIndicator
              origin={valueOriginKey}
              title={t(`investments.table.valueOrigin.labels.${valueOriginKey}`)}
              description={t(`investments.table.valueOrigin.descriptions.${valueOriginKey}`)}
              ariaLabel={t("investments.table.valueOrigin.ariaLabel")}
              offsetPx={18}
            />
          ) : null}
        </Box>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: "flex-end" }} />
      </Box>

      <HoldingRowActionsColumn
        actionColWidth={layout.actionColWidth}
        hovered={hovered}
        holding={holding}
        t={t}
        onOpenActions={onOpenActions}
        onSell={onSell}
        sellLabel={sellLabel}
      />
    </Box>
  );
});
