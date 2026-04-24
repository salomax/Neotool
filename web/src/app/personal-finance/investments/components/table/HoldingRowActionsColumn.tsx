"use client";

import React from "react";
import { Box, IconButton, Tooltip, alpha, useTheme } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import AddIcon from "@mui/icons-material/Add";
import RemoveIcon from "@mui/icons-material/Remove";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import type { HoldingRow } from "./investmentTableHelpers";
import { useInvestmentTableActions } from "./InvestmentTableActionsContext";

export interface HoldingRowActionsColumnProps {
  actionColWidth: number;
  hovered: boolean;
  holding: HoldingRow;
  buyLabel?: string;
  sellLabel?: string;
}

export const HoldingRowActionsColumn = React.memo(function HoldingRowActionsColumn({
  actionColWidth,
  hovered,
  holding,
  buyLabel,
  sellLabel,
}: HoldingRowActionsColumnProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { onOpenActions, onBuy, onSell } = useInvestmentTableActions();
  const theme = useTheme();

  const quickActionTileSx = React.useMemo(
    () => ({
      width: 32,
      height: 32,
      minWidth: 32,
      p: 0,
      borderRadius: "10px",
      color: alpha(theme.palette.success.dark, theme.palette.mode === "dark" ? 0.9 : 0.75),
      bgcolor: "transparent",
      "&:hover": {
        bgcolor: alpha(theme.palette.success.main, theme.palette.mode === "dark" ? 0.26 : 0.16),
        color: theme.palette.success.main,
      },
      "&:focus-visible": {
        outline: `2px solid ${alpha(theme.palette.success.main, 0.45)}`,
        outlineOffset: 1,
      },
    }),
    [theme],
  );

  const quickActionRedeemTileSx = React.useMemo(
    () => ({
      ...quickActionTileSx,
      color: theme.palette.error.main,
      bgcolor: "transparent",
      "&:hover": {
        bgcolor: alpha(theme.palette.error.main, theme.palette.mode === "dark" ? 0.26 : 0.16),
        color: theme.palette.error.main,
      },
      "&:focus-visible": {
        outline: `2px solid ${alpha(theme.palette.error.main, 0.45)}`,
        outlineOffset: 1,
      },
    }),
    [quickActionTileSx, theme],
  );

  return (
    <Box
      sx={{
        width: actionColWidth,
        ml: "auto",
        flexShrink: 0,
        display: "flex",
        justifyContent: "flex-end",
        alignItems: "center",
        gap: 0.25,
      }}
    >
      <Box
        sx={{
          display: { xs: "none", md: "flex" },
          alignItems: "center",
          gap: 0.5,
          visibility: hovered ? "visible" : "hidden",
        }}
      >
        {buyLabel ? (
          <Tooltip title={buyLabel}>
            <IconButton
              size="small"
              aria-label={buyLabel}
              onClick={(event) => {
                event.stopPropagation();
                onBuy(holding);
              }}
              sx={quickActionTileSx}
            >
              <AddIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Tooltip>
        ) : null}
        {sellLabel ? (
          <Tooltip title={sellLabel}>
            <IconButton
              size="small"
              aria-label={sellLabel}
              onClick={(event) => {
                event.stopPropagation();
                onSell(holding);
              }}
              sx={quickActionRedeemTileSx}
            >
              <RemoveIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Tooltip>
        ) : null}
      </Box>
      <IconButton
        size="small"
        aria-label={t("investments.table.rowActions.ariaLabel")}
        onClick={(event) => {
          event.stopPropagation();
          onOpenActions(event.currentTarget, holding);
        }}
      >
        <MoreVertIcon fontSize="small" />
      </IconButton>
    </Box>
  );
});
