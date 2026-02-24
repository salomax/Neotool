"use client";

import React from "react";
import { Box, useTheme } from "@mui/material";
import CompareArrowsIcon from "@mui/icons-material/CompareArrows";
import PercentIcon from "@mui/icons-material/Percent";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import SavingsIcon from "@mui/icons-material/Savings";
import { glassStyle } from "./styles";
import { AnimatedIconProps, ICON_SIZES } from "./types";

export function FixedIncomeComparisonIcon({ size = "md" }: AnimatedIconProps) {
  const theme = useTheme();
  const { scale, height } = ICON_SIZES[size];

  return (
    <Box
      sx={{
        width: "100%",
        height: height,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
        overflow: "visible",
      }}
    >
      <Box
        sx={{
          position: "relative",
          width: 160,
          height: 160,
          transform: `scale(${scale})`,
          transformOrigin: "center center",
          transition: "transform 0.3s ease-in-out",
        }}
      >
        {/* Back Card */}
        <Box
          sx={{
            position: "absolute",
            inset: 0,
            bgcolor:
              theme.palette.mode === "dark" ? "grey.800" : "grey.100",
            borderRadius: 4,
            boxShadow: "inset 0 2px 4px 0 rgba(0, 0, 0, 0.06)",
            transform: "rotate(-5deg)",
            transition: "transform 0.4s ease-out",
            ".module-card-root:hover &": {
              transform: "rotate(-8deg) scale(1.05)",
            },
          }}
        />

        {/* Main Card */}
        <Box
          sx={{
            position: "absolute",
            top: 16,
            bottom: 16,
            left: 16,
            right: 16,
            bgcolor: "background.paper",
            borderRadius: 3,
            boxShadow: theme.shadows[6],
            border: `1px solid ${theme.palette.divider}`,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            transition: "all 0.4s ease-out",
            zIndex: 10,
            ".module-card-root:hover &": {
              transform: "scale(1.1) translateY(-5px)",
              boxShadow: theme.shadows[10],
            },
          }}
        >
          <CompareArrowsIcon
            sx={{ fontSize: 48, color: "primary.main", mb: 0.5 }}
          />
        </Box>

        {/* Floating Element 1 (Top Right) */}
        <Box
          sx={{
            position: "absolute",
            top: -16,
            right: -16,
            p: 1.5,
            borderRadius: "20%",
            ...glassStyle(theme),
            transform: "rotate(12deg)",
            zIndex: 20,
            transition: "all 0.4s ease-out",
            ".module-card-root:hover &": {
              transform: "rotate(15deg) translate(10px, -10px) scale(1.1)",
            },
          }}
        >
          <PercentIcon sx={{ color: "success.main", fontSize: 28 }} />
        </Box>

        {/* Floating Element 2 (Bottom Left) */}
        <Box
          sx={{
            position: "absolute",
            bottom: -8,
            left: -24,
            p: 1.5,
            borderRadius: "20%",
            ...glassStyle(theme),
            transform: "rotate(-8deg)",
            zIndex: 20,
            transition: "all 0.4s ease-out",
            ".module-card-root:hover &": {
              transform: "rotate(-12deg) translate(-10px, 5px) scale(1.1)",
            },
          }}
        >
          <TrendingUpIcon sx={{ color: "primary.main", fontSize: 20 }} />
        </Box>

        {/* Floating Element 3 (Right Middle) */}
        <Box
          sx={{
            position: "absolute",
            top: "50%",
            right: -32,
            width: 48,
            height: 48,
            bgcolor: "background.paper",
            borderRadius: "50%",
            boxShadow: theme.shadows[4],
            border: `1px solid ${theme.palette.divider}`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 15,
            transition: "all 0.4s ease-out",
            ".module-card-root:hover &": {
              transform: "translate(10px, 0) scale(1.1) rotate(10deg)",
            },
          }}
        >
          <SavingsIcon sx={{ color: "primary.main", fontSize: 20 }} />
        </Box>
      </Box>
    </Box>
  );
}
