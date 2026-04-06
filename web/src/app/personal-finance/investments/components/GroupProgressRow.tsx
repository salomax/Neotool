"use client";

import React from "react";
import { alpha, Box, LinearProgress, Typography, useTheme } from "@mui/material";
import type { AssetGroupKey } from "./table/investmentTableHelpers";

export type SummaryGroupKey = AssetGroupKey | "cash";

interface GroupProgressRowProps {
  groupKey: SummaryGroupKey;
  label: string;
  count: number;
  allocationPercent: number;
  isCash: boolean;
  active?: boolean;
  onClick?: () => void;
  locale?: string;
}

export function GroupProgressRow({
  groupKey,
  label,
  count,
  allocationPercent,
  isCash,
  active = false,
  onClick,
  locale,
}: GroupProgressRowProps) {
  const theme = useTheme();
  const interactive = Boolean(onClick);
  const progressValue = Math.max(0, Math.min(100, allocationPercent * 100));
  const barColor = theme.palette.primary.main;
  const trackBg = alpha(theme.palette.text.primary, 0.08);
  const allocationLabel = new Intl.NumberFormat(locale, { style: "percent", maximumFractionDigits: 1 }).format(allocationPercent);

  return (
    <Box
      onClick={onClick}
      onKeyDown={
        interactive
          ? (event: React.KeyboardEvent) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onClick?.();
              }
            }
          : undefined
      }
      role={interactive ? "button" : undefined}
      tabIndex={interactive ? 0 : undefined}
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1.5,
        px: 0,
        py: 0.5,
        borderRadius: 1.5,
        cursor: interactive ? "pointer" : "default",
        bgcolor: active ? alpha(theme.palette.primary.main, 0.08) : "transparent",
        transition: theme.transitions.create(["background-color", "transform"], {
          duration: theme.transitions.duration.shorter,
        }),
        ...(interactive
          ? {
              "&:hover": {
                bgcolor: alpha(theme.palette.primary.main, 0.06),
              },
              "&:focus-visible": {
                outline: `2px solid ${theme.palette.primary.main}`,
                outlineOffset: 2,
              },
            }
          : {}),
      }}
    >
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
            <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
              {label}
            </Typography>
            {!isCash && count > 0 ? (
              <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
                {count}
              </Typography>
            ) : null}
          </Box>

          <Box sx={{ display: "flex", alignItems: "center", gap: 1.25, flexShrink: 0 }}>
            <Typography variant="body2" sx={{ fontWeight: 600, whiteSpace: "nowrap", fontVariantNumeric: "tabular-nums" }}>
              {allocationLabel}
            </Typography>
          </Box>
        </Box>

        <LinearProgress
          variant="determinate"
          value={progressValue}
          sx={{
            mt: 0.75,
            height: 6,
            borderRadius: 2,
            bgcolor: trackBg,
            "& .MuiLinearProgress-bar": {
              bgcolor: barColor,
              borderRadius: 2,
            },
          }}
        />
      </Box>
    </Box>
  );
}
