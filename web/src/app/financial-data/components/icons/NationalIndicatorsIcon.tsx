"use client";

import React from "react";
import { Box, useTheme } from "@mui/material";
import ShowChartIcon from "@mui/icons-material/ShowChart";
import CurrencyExchangeIcon from "@mui/icons-material/CurrencyExchange";
import TimelineIcon from "@mui/icons-material/Timeline";
import PercentIcon from "@mui/icons-material/Percent";
import { glassStyle } from "./styles";
import { AnimatedIconProps, ICON_SIZES } from "./types";

export function NationalIndicatorsIcon({ size = "md" }: AnimatedIconProps) {
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
        <Box sx={{ 
            position: "relative", 
            width: 160, 
            height: 160,
            transform: `scale(${scale})`,
            transformOrigin: "center center",
            transition: "transform 0.3s ease-in-out"
        }}>
            
            {/* Back Card - Rotated opposite way */}
            <Box sx={{
                position: "absolute",
                inset: 0,
                bgcolor: theme.palette.mode === 'dark' ? 'grey.800' : 'grey.100',
                borderRadius: 4,
                boxShadow: "inset 0 2px 4px 0 rgba(0, 0, 0, 0.06)",
                transform: "rotate(5deg)",
                transition: "transform 0.4s ease-out",
                ".module-card-root:hover &": {
                    transform: "rotate(8deg) scale(1.05)",
                }
            }} />

            {/* Main Card */}
            <Box sx={{
                position: "absolute",
                top: 16, bottom: 16, left: 16, right: 16,
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
                }
            }}>
                <ShowChartIcon sx={{ fontSize: 48, color: "secondary.main", mb: 0.5 }} />
                <Box sx={{ width: '60%', height: 4, bgcolor: 'secondary.light', borderRadius: 2, mb: 0.5 }} />
                <Box sx={{ width: '40%', height: 4, bgcolor: 'secondary.light', borderRadius: 2 }} />
            </Box>

            {/* Floating Element 1 (Top Left) */}
            <Box sx={{
                position: "absolute",
                top: -10, left: -20,
                p: 1.5,
                borderRadius: 4,
                ...glassStyle(theme),
                transform: "rotate(-12deg)",
                zIndex: 20,
                transition: "all 0.4s ease-out",
                ".module-card-root:hover &": {
                    transform: "rotate(-15deg) translate(-10px, -10px) scale(1.1)",
                }
            }}>
                <CurrencyExchangeIcon sx={{ color: "warning.main", fontSize: 28 }} />
            </Box>

            {/* Floating Element 2 (Bottom Right) */}
            <Box sx={{
                position: "absolute",
                bottom: -12, right: -16,
                p: 1.5,
                borderRadius: 4,
                ...glassStyle(theme),
                transform: "rotate(8deg)",
                zIndex: 20,
                transition: "all 0.4s ease-out",
                ".module-card-root:hover &": {
                    transform: "rotate(12deg) translate(10px, 5px) scale(1.1)",
                }
            }}>
                <PercentIcon sx={{ color: "info.main", fontSize: 24 }} />
            </Box>

            {/* Floating Element 3 (Left Middle) */}
            <Box sx={{
                position: "absolute",
                top: "60%", left: -36,
                width: 44, height: 44,
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
                    transform: "translate(-10px, 0) scale(1.1) rotate(-10deg)",
                }
            }}>
                <TimelineIcon sx={{ color: "secondary.main", fontSize: 20 }} />
            </Box>

        </Box>
    </Box>
  );
}
