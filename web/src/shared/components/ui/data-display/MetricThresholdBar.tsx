import * as React from "react";
import { Box, useTheme } from "@mui/material";

export interface MetricThresholdBarProps {
  value: number; // 0-100
  thresholds: {
    bad: number;
    regular: number;
    good: number;
  };
  inverse?: boolean;
}

export const MetricThresholdBar = React.memo(function MetricThresholdBar({
  value,
  thresholds,
  inverse = false,
}: MetricThresholdBarProps) {
  const theme = useTheme();

  const segmentWidths = React.useMemo(() => {
    if (inverse) {
      const goodWidth = thresholds.good;
      const regularWidth = thresholds.regular - thresholds.good;
      const badWidth = 100 - thresholds.regular;
      return {
        bad: Math.max(0, Math.min(100, badWidth)),
        regular: Math.max(0, Math.min(100, regularWidth)),
        good: Math.max(0, Math.min(100, goodWidth)),
      };
    }

    const badWidth = thresholds.bad;
    const regularWidth = thresholds.regular - thresholds.bad;
    const goodWidth = 100 - thresholds.regular;
    return {
      bad: Math.max(0, Math.min(100, badWidth)),
      regular: Math.max(0, Math.min(100, regularWidth)),
      good: Math.max(0, Math.min(100, goodWidth)),
    };
  }, [thresholds, inverse]);

  const progressValue = Math.min(Math.max(value, 0), 100);

  const status = React.useMemo<"good" | "regular" | "bad" | "neutral">(() => {
    if (inverse) {
      if (value < thresholds.good) return "good";
      if (value <= thresholds.regular) return "regular";
      if (value > thresholds.regular) return "bad";
      return "neutral";
    }

    if (value < thresholds.bad) return "bad";
    if (value <= thresholds.regular) return "regular";
    return "good";
  }, [value, thresholds, inverse]);

  const thresholdColors = React.useMemo(() => {
    const customTheme = theme as any;
    return {
      bad: customTheme.custom?.palette?.thresholdBad || "#F99797",
      regular: customTheme.custom?.palette?.thresholdRegular || "#FAD957",
      good: customTheme.custom?.palette?.thresholdGood || "#61D48A",
    };
  }, [theme]);

  if (status === "neutral") return null;

  return (
    <Box sx={{ mt: 1 }}>
      <Box sx={{ position: "relative" }}>
        <Box
          sx={{
            height: 8,
            borderRadius: 4,
            overflow: "hidden",
            position: "relative",
            display: "flex",
          }}
        >
          {inverse ? (
            <>
              <Box
                sx={{
                  height: "100%",
                  width: `${segmentWidths.good}%`,
                  bgcolor: thresholdColors.good,
                }}
              />
              <Box
                sx={{
                  height: "100%",
                  width: `${segmentWidths.regular}%`,
                  bgcolor: thresholdColors.regular,
                }}
              />
              <Box
                sx={{
                  height: "100%",
                  width: `${segmentWidths.bad}%`,
                  bgcolor: thresholdColors.bad,
                }}
              />
            </>
          ) : (
            <>
              <Box
                sx={{
                  height: "100%",
                  width: `${segmentWidths.bad}%`,
                  bgcolor: thresholdColors.bad,
                }}
              />
              <Box
                sx={{
                  height: "100%",
                  width: `${segmentWidths.regular}%`,
                  bgcolor: thresholdColors.regular,
                }}
              />
              <Box
                sx={{
                  height: "100%",
                  width: `${segmentWidths.good}%`,
                  bgcolor: thresholdColors.good,
                }}
              />
            </>
          )}
        </Box>

        <Box
          sx={{
            position: "absolute",
            left: `${progressValue}%`,
            top: -8,
            width: 0,
            height: 0,
            borderLeft: "10px solid transparent",
            borderRight: "10px solid transparent",
            borderBottom: `8px solid ${
              thresholdColors[status]
            }`,
            transform: "translateX(-50%)",
            zIndex: 1,
          }}
        />
      </Box>
    </Box>
  );
});
