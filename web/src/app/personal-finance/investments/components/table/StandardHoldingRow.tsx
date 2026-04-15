"use client";

import React from "react";
import { Box, Typography } from "@mui/material";
import { PercentageGain, ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { formatTableDate } from "@/shared/utils/date";
import { getHoldingValueOrigin } from "@/shared/utils/holdingValueOrigin";
import { HoldingIdentity, resolveHoldingIdentity } from "../HoldingIdentity";
import {
  computePrincipal,
  formatValueOrPlaceholder,
  type InvestmentTableTranslate,
} from "./investmentTableRowHelpers";
import type { InvestmentGroupLayout } from "./investmentTableLayout";
import type { HoldingRow } from "./investmentTableHelpers";
import { HoldingRowActionsColumn } from "./HoldingRowActionsColumn";

const SECONDARY_ROW_SX = { mt: 0.25, minHeight: 16, display: "flex", alignItems: "center" } as const;

export interface StandardHoldingRowProps {
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

export const StandardHoldingRow = React.memo(function StandardHoldingRow({
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
}: StandardHoldingRowProps) {
  const [hovered, setHovered] = React.useState(false);
  const identity = resolveHoldingIdentity(holding);
  const assetType = holding.asset?.assetType ?? null;
  const assetTypeLabel = assetType != null ? t(`investments.assetTypes.${assetType}`) : "—";
  const maturityLabel = holding.maturityDate ? formatTableDate(String(holding.maturityDate), locale) : "—";

  const principal = computePrincipal(holding);
  const gross = typeof holding.grossValue === "number" ? holding.grossValue : null;
  const gain = gross != null && principal != null ? gross - principal : null;
  const gainPercent = gross != null && principal != null && principal > 0 ? (gross - principal) / principal : null;
  const valueOriginKey = getHoldingValueOrigin(holding);

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

      <Box sx={{ width: layout.colWidth, minWidth: 0 }}>
        <Typography variant="body2" noWrap>
          {assetTypeLabel}
        </Typography>
        <Box sx={SECONDARY_ROW_SX} />
      </Box>

      <Box sx={{ ...layout.maturityColSx, minWidth: 0 }}>
        <Typography variant="body2" noWrap>
          {maturityLabel}
        </Typography>
        <Box sx={SECONDARY_ROW_SX} />
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
