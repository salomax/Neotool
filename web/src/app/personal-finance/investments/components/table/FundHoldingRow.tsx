"use client";

import React from "react";
import { Box, Typography } from "@mui/material";
import { PercentageGain, ValueOriginIndicator } from "@/shared/components/ui/data-display";
import { useTranslation } from "@/shared/i18n";
import { getHoldingValueOrigin } from "@/shared/utils/holdingValueOrigin";
import { HoldingIdentity, resolveHoldingIdentity } from "../HoldingIdentity";
import { personalFinanceTranslations } from "../../../i18n";
import {
  computePrincipal,
  formatValueOrPlaceholder,
} from "./investmentTableRowHelpers";
import type { InvestmentGroupLayout } from "./investmentTableLayout";
import type { HoldingRow } from "./investmentTableHelpers";
import { HoldingRowActionsColumn } from "./HoldingRowActionsColumn";
import { useInvestmentTableActions } from "./InvestmentTableActionsContext";

const SECONDARY_ROW_SX = { mt: 0.25, minHeight: 16, display: "flex", alignItems: "center" } as const;

export interface FundHoldingRowProps {
  holding: HoldingRow;
  layout: InvestmentGroupLayout;
  locale: string;
  dividerColor: string;
  hoverColor: string;
  buyLabel?: string;
  sellLabel?: string;
}

export const FundHoldingRow = React.memo(function FundHoldingRow({
  holding,
  layout,
  locale,
  dividerColor,
  hoverColor,
  buyLabel,
  sellLabel,
}: FundHoldingRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { onOpenDetail } = useInvestmentTableActions();
  const [hovered, setHovered] = React.useState(false);
  const identity = resolveHoldingIdentity(holding);

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
        buyLabel={buyLabel}
        sellLabel={sellLabel}
      />
    </Box>
  );
});
