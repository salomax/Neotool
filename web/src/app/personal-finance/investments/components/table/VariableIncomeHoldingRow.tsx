"use client";

import React from "react";
import { Box, Typography } from "@mui/material";
import { PercentageGain, ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { getHoldingValueOrigin } from "@/shared/utils/holdingValueOrigin";
import { HoldingIdentity, resolveHoldingIdentity } from "../HoldingIdentity";
import {
  computePrincipal,
  formatQuantity,
  formatValueOrPlaceholder,
  type InvestmentTableTranslate,
} from "./investmentTableRowHelpers";
import type { InvestmentGroupLayout } from "./investmentTableLayout";
import type { HoldingRow } from "./investmentTableHelpers";
import { HoldingRowActionsColumn } from "./HoldingRowActionsColumn";

const SECONDARY_ROW_SX = { mt: 0.25, minHeight: 16, display: "flex", alignItems: "center" } as const;

export interface VariableIncomeHoldingRowProps {
  holding: HoldingRow;
  layout: InvestmentGroupLayout;
  locale: string;
  dividerColor: string;
  hoverColor: string;
  t: InvestmentTableTranslate;
  onOpenDetail: (holding: HoldingRow) => void;
  onOpenActions: (anchorEl: HTMLElement, holding: HoldingRow) => void;
  onBuy?: (holding: HoldingRow) => void;
  onSell?: (holding: HoldingRow) => void;
  buyLabel?: string;
  sellLabel?: string;
}

export const VariableIncomeHoldingRow = React.memo(function VariableIncomeHoldingRow({
  holding,
  layout,
  locale,
  dividerColor,
  hoverColor,
  t,
  onOpenDetail,
  onOpenActions,
  onBuy,
  onSell,
  buyLabel,
  sellLabel,
}: VariableIncomeHoldingRowProps) {
  const [hovered, setHovered] = React.useState(false);
  const identity = resolveHoldingIdentity(holding);

  const principal = computePrincipal(holding);
  const gross = typeof holding.grossValue === "number" ? holding.grossValue : null;
  const gain = gross != null && principal != null ? gross - principal : null;
  const gainPercent = gross != null && principal != null && principal > 0 ? (gross - principal) / principal : null;
  const valueOriginKey = getHoldingValueOrigin(holding);
  const latestPrice = React.useMemo(() => {
    if (gross == null) return null;
    if (typeof holding.quantity !== "number" || !Number.isFinite(holding.quantity) || holding.quantity <= 0) return null;
    const computed = gross / holding.quantity;
    return Number.isFinite(computed) ? computed : null;
  }, [gross, holding.quantity]);

  const latestPriceDateLabel = React.useMemo(() => {
    if (!holding.valuationReferenceDate) return null;
    const parsed = new Date(String(holding.valuationReferenceDate));
    if (Number.isNaN(parsed.getTime())) return null;
    return new Intl.DateTimeFormat(locale, { day: "2-digit", month: "2-digit", year: "numeric" }).format(parsed);
  }, [holding.valuationReferenceDate, locale]);

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
        <HoldingIdentity assetName={identity.assetName} issuerName={identity.issuerName} logoUrl={identity.logoUrl} size="lg" />
      </Box>

      <Box sx={{ ...layout.maturityColSx, minWidth: 0, textAlign: "right" }}>
        <Typography variant="body2" noWrap>
          {formatQuantity(holding.quantity, locale)}
        </Typography>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: "flex-end" }} />
      </Box>

      <Box sx={{ ...layout.maturityColSx, minWidth: 0, textAlign: "right" }}>
        <Typography variant="body2" noWrap>
          {formatValueOrPlaceholder(holding.averageCost, holding.asset?.currencyCode, locale)}
        </Typography>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: "flex-end" }} />
      </Box>

      <Box sx={{ ...layout.maturityColSx, minWidth: 0, textAlign: "right" }}>
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
            {formatValueOrPlaceholder(latestPrice, holding.asset?.currencyCode, locale)}
          </Typography>
          {latestPrice != null && latestPriceDateLabel != null ? (
            <ValueOriginIndicator
              origin={valueOriginKey}
              title={t("investments.table.latestPriceInfo.title", { date: latestPriceDateLabel })}
              ariaLabel={t("investments.table.latestPriceInfo.ariaLabel")}
              color="info"
              offsetPx={18}
            />
          ) : null}
        </Box>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: "flex-end" }} />
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
        onBuy={onBuy}
        onSell={onSell}
        buyLabel={buyLabel}
        sellLabel={sellLabel}
      />
    </Box>
  );
});
