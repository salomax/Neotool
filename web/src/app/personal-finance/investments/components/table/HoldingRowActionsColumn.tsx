"use client";

import React from "react";
import { alpha, Box, IconButton, useTheme } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import RemoveIcon from "@mui/icons-material/Remove";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import type { HoldingRow } from "./investmentTableHelpers";
import { useInvestmentTableActions } from "./InvestmentTableActionsContext";

export interface HoldingRowActionsColumnProps {
  actionColWidth: number;
  holding: HoldingRow;
}

export const HoldingRowActionsColumn = React.memo(function HoldingRowActionsColumn({
  actionColWidth,
  holding,
}: HoldingRowActionsColumnProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const { onOpenActions, onRowBuy, onRowSell } = useInvestmentTableActions();

  const isDark = theme.palette.mode === "dark";
  const actionBorderRadius = `${theme.custom.radius.md}px`;
  const inflowActiveColor = theme.palette.success.main;
  const inflowActiveBg = alpha(theme.palette.success.main, isDark ? 0.26 : 0.16);
  const outflowActiveColor = theme.palette.error.main;
  const outflowActiveBg = alpha(theme.palette.error.main, isDark ? 0.26 : 0.16);

  const actionChipBaseSx = {
    width: 32,
    height: 32,
    padding: 0,
    borderRadius: actionBorderRadius,
    border: 0,
    boxSizing: "border-box" as const,
    bgcolor: "transparent",
    transition: theme.transitions.create("background-color", {
      duration: theme.transitions.duration.shortest,
    }),
  };

  return (
    <Box
      sx={{
        width: actionColWidth,
        ml: "auto",
        flexShrink: 0,
        display: "flex",
        justifyContent: "flex-end",
        alignItems: "center",
        gap: 0.5,
      }}
    >
      <Box
        className="holding-row-quick-actions"
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 0.5,
          opacity: { xs: 1, md: 0 },
          visibility: { xs: "visible", md: "hidden" },
          pointerEvents: { xs: "auto", md: "none" },
          transition: "opacity 120ms ease",
          "@media (hover: none)": {
            opacity: 1,
            visibility: "visible",
            pointerEvents: "auto",
          },
        }}
      >
        <IconButton
          size="small"
          aria-label={t("investments.row.addTransaction", { defaultValue: "Adicionar transação" })}
          onClick={(event) => {
            event.stopPropagation();
            onRowBuy(holding);
          }}
          sx={{
            ...actionChipBaseSx,
            color: inflowActiveColor,
            "&:hover": {
              bgcolor: inflowActiveBg,
            },
          }}
        >
          <AddIcon fontSize="small" />
        </IconButton>

        <IconButton
          size="small"
          aria-label={t("investments.row.sellTransaction", { defaultValue: "Registrar venda" })}
          onClick={(event) => {
            event.stopPropagation();
            onRowSell(holding);
          }}
          sx={{
            ...actionChipBaseSx,
            color: outflowActiveColor,
            "&:hover": {
              bgcolor: outflowActiveBg,
            },
          }}
        >
          <RemoveIcon fontSize="small" />
        </IconButton>
      </Box>

      <IconButton
        size="small"
        aria-label={t("investments.table.rowActions.ariaLabel")}
        onClick={(event) => {
          event.stopPropagation();
          onOpenActions(event.currentTarget, holding);
        }}
        sx={{
          ...actionChipBaseSx,
          color: "text.secondary",
          "&:hover": {
            bgcolor: alpha(theme.palette.text.primary, 0.08),
          },
        }}
      >
        <MoreVertIcon fontSize="small" />
      </IconButton>
    </Box>
  );
});
