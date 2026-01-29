"use client";

import * as React from "react";
import { Box, Paper, Typography, Stack, Skeleton, useTheme } from "@mui/material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import WarningIcon from "@mui/icons-material/Warning";
import ErrorIcon from "@mui/icons-material/Error";

export interface PercentageThresholds {
  bad: (v: number) => boolean;
  regular: (v: number) => boolean;
  good: (v: number) => boolean;
}

export interface PercentageThresholdValues {
  bad: number; // Upper bound for bad (or lower for inverse)
  regular: number; // Upper bound for regular
  good: number; // Upper bound for good
}

export interface PercentageBoxProps {
  label: string;
  value: number | null | undefined; // Percentage value (0-100)
  isLoading?: boolean;
  thresholds: PercentageThresholds;
  thresholdValues: PercentageThresholdValues;
  inverse?: boolean; // For inverse logic (like Imobilização)
  offset?: number; // For dynamic max calculation
  locale?: string;
}

/**
 * PercentageBox component for displaying percentage values with threshold indicators
 * 
 * @example
 * ```tsx
 * <PercentageBox
 *   label="Basel Index"
 *   value={15.5}
 *   thresholds={{
 *     bad: (v) => v < 11,
 *     regular: (v) => v >= 11 && v <= 14,
 *     good: (v) => v > 14
 *   }}
 *   thresholdValues={{ bad: 11, regular: 14, good: 100 }}
 * />
 * ```
 */
export const PercentageBox: React.FC<PercentageBoxProps> = ({
  label,
  value,
  isLoading = false,
  thresholds,
  thresholdValues,
  inverse = false,
  offset = 10,
  locale = "pt-BR"
}) => {
  const theme = useTheme();
  
  // Determine status
  const status = React.useMemo(() => {
    if (value == null) return "neutral";
    if (thresholds.good(value)) return "good";
    if (thresholds.regular(value)) return "regular";
    if (thresholds.bad(value)) return "bad";
    return "neutral";
  }, [value, thresholds]);

  // Colors
  const colors = {
    good: { bg: "#E8F5E9", text: theme.palette.success.main, icon: theme.palette.success.main }, // Green
    regular: { bg: "#FFF8E1", text: theme.palette.warning.main, icon: theme.palette.warning.main }, // Yellow
    bad: { bg: "#FFEBEE", text: theme.palette.error.main, icon: theme.palette.error.main }, // Red
    neutral: { bg: theme.palette.background.paper, text: theme.palette.text.primary, icon: theme.palette.text.disabled }
  };

  const currentColors = colors[status];

  // Dynamic Max Scale
  const maxScale = React.useMemo(() => {
    if (value == null) return 20;
    if (value > 20) {
      return Math.min(value + offset, 100);
    }
    return 20;
  }, [value, offset]);

  // Calculate segment widths based on thresholds
  const segmentWidths = React.useMemo(() => {
    if (value == null || maxScale === 0) {
      return { bad: 0, regular: 0, good: 100 };
    }

    if (inverse) {
      // For inverse (Imobilização): good < 40%, regular 40-50%, bad > 50%
      // Segments from left to right: good (0-40%), regular (40-50%), bad (50-100%)
      const goodWidth = (thresholdValues.good / maxScale) * 100; // 40% / maxScale
      const regularWidth = ((thresholdValues.regular - thresholdValues.good) / maxScale) * 100; // (50-40)% / maxScale
      const badWidth = 100 - goodWidth - regularWidth;
      return {
        good: Math.max(0, Math.min(100, goodWidth)),
        regular: Math.max(0, Math.min(100, regularWidth)),
        bad: Math.max(0, Math.min(100, badWidth))
      };
    } else {
      // For normal (Basileia): bad < 11%, regular 11-14%, good > 14%
      // Segments from left to right: bad (0-11%), regular (11-14%), good (14-100%)
      const badWidth = (thresholdValues.bad / maxScale) * 100; // 11% / maxScale
      const regularWidth = ((thresholdValues.regular - thresholdValues.bad) / maxScale) * 100; // (14-11)% / maxScale
      const goodWidth = 100 - badWidth - regularWidth;
      return {
        bad: Math.max(0, Math.min(100, badWidth)),
        regular: Math.max(0, Math.min(100, regularWidth)),
        good: Math.max(0, Math.min(100, goodWidth))
      };
    }
  }, [thresholdValues, maxScale, inverse, value]);

  // Progress Value (normalized to 0-100 for positioning)
  const progressValue = React.useMemo(() => {
    if (value == null) return 0;
    return (Math.min(value, maxScale) / maxScale) * 100;
  }, [value, maxScale]);

  if (isLoading) {
    return (
      <Paper
        variant="outlined"
        sx={{ p: 2, height: "100%", boxShadow: "0 10px 30px rgba(15, 23, 42, 0.06)" }}
      >
        <Skeleton width="60%" height={20} sx={{ mb: 1 }} />
        <Skeleton width="40%" height={40} sx={{ mb: 1 }} />
        <Skeleton width="100%" height={10} />
      </Paper>
    );
  }

  // Threshold colors
  const thresholdColors = {
    bad: "#F99797", // red
    regular: "#FAD957", // yellow
    good: "#61D48A", // green
  };

  return (
    <Paper 
      variant="outlined" 
      sx={{ 
        p: 2, 
        height: "100%",
        boxShadow: "0 10px 30px rgba(15, 23, 42, 0.06)",
        position: "relative",
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
      }}
    >
      <Box>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Typography variant="body2" sx={{ color: theme.palette.text.secondary, fontWeight: 600 }}>
            {label}
          </Typography>
          {status === 'good' && <CheckCircleIcon sx={{ color: currentColors.icon }} />}
          {status === 'regular' && <WarningIcon sx={{ color: currentColors.icon }} />}
          {status === 'bad' && <ErrorIcon sx={{ color: currentColors.icon }} />}
        </Stack>

        <Typography variant="h4" sx={{ fontWeight: "bold", my: 1 }}>
          {value != null ? value.toLocaleString(locale, { maximumFractionDigits: 1 }) : "-"}%
        </Typography>
      </Box>

      <Box sx={{ mt: 2 }}>
        <Box sx={{ position: 'relative' }}>
          <Box 
            sx={{ 
              height: 8, 
              borderRadius: 4, 
              overflow: 'hidden',
              position: 'relative',
              display: 'flex'
            }}
          >
            {inverse ? (
              <>
                {/* Good segment (left) */}
                <Box 
                  sx={{ 
                    height: '100%', 
                    width: `${segmentWidths.good}%`,
                    bgcolor: thresholdColors.good,
                  }} 
                />
                {/* Regular segment (middle) */}
                <Box 
                  sx={{ 
                    height: '100%', 
                    width: `${segmentWidths.regular}%`,
                    bgcolor: thresholdColors.regular,
                  }} 
                />
                {/* Bad segment (right) */}
                <Box 
                  sx={{ 
                    height: '100%', 
                    width: `${segmentWidths.bad}%`,
                    bgcolor: thresholdColors.bad,
                  }} 
                />
              </>
            ) : (
              <>
                {/* Bad segment (left) */}
                <Box 
                  sx={{ 
                    height: '100%', 
                    width: `${segmentWidths.bad}%`,
                    bgcolor: thresholdColors.bad,
                  }} 
                />
                {/* Regular segment (middle) */}
                <Box 
                  sx={{ 
                    height: '100%', 
                    width: `${segmentWidths.regular}%`,
                    bgcolor: thresholdColors.regular,
                  }} 
                />
                {/* Good segment (right) */}
                <Box 
                  sx={{ 
                    height: '100%', 
                    width: `${segmentWidths.good}%`,
                    bgcolor: thresholdColors.good,
                  }} 
                />
              </>
            )}
          </Box>
          
          {value != null && status !== "neutral" && (
            <Box
              sx={{
                position: "absolute",
                left: `${progressValue}%`,
                top: -6,
                width: 0,
                height: 0,
                borderLeft: "5px solid transparent",
                borderRight: "5px solid transparent",
                borderBottom: `6px solid ${thresholdColors[status as "bad" | "regular" | "good"]}`,
                transform: "translateX(-50%)",
                zIndex: 1,
              }}
            />
          )}
        </Box>
      </Box>
    </Paper>
  );
};
