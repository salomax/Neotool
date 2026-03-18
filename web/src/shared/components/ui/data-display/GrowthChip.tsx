import * as React from "react";
import { Chip } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import ArrowDropUpIcon from "@mui/icons-material/ArrowDropUp";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import { formatPercentageWithSign } from "@/shared/utils/percentage";
import type { SxProps, Theme } from "@mui/material/styles";

export interface GrowthChipProps {
  growthPercentage?: number | null;
  locale?: string;
  size?: "small" | "medium";
  sx?: SxProps<Theme>;
}

export const GrowthChip: React.FC<GrowthChipProps> = ({ growthPercentage, locale, size = "medium", sx }) => {
  const theme = useTheme();
  const hasGrowth = growthPercentage != null;

  const growthColor = React.useMemo(() => {
    if (!hasGrowth) {
      return theme.palette.primary.main;
    }
    if ((growthPercentage ?? 0) > 0) {
      return theme.palette.success.main;
    }
    if ((growthPercentage ?? 0) < 0) {
      return theme.palette.error.main;
    }
    return theme.palette.text.secondary;
  }, [growthPercentage, hasGrowth, theme.palette]);

  const growthBgColor = React.useMemo(() => {
    if (!hasGrowth) {
      return theme.palette.primary.light;
    }
    if ((growthPercentage ?? 0) > 0) {
      return (theme as any).custom?.palette?.successLightBg;
    }
    if ((growthPercentage ?? 0) < 0) {
      return (theme as any).custom?.palette?.errorLightBg;
    }
    return theme.palette.action.hover;
  }, [growthPercentage, hasGrowth, theme]);

  const growthFormatted = React.useMemo(() => {
    if (!hasGrowth) {
      return "";
    }
    return formatPercentageWithSign(growthPercentage ?? 0, {
      decimals: 1,
      showZeroSign: false,
      locale,
      alwaysShowPlusSign: false
    });
  }, [growthPercentage, hasGrowth, locale]);

  const GrowthIcon = React.useMemo(() => {
    if (!hasGrowth) {
      return null;
    }
    if ((growthPercentage ?? 0) > 0) {
      return ArrowDropUpIcon;
    }
    if ((growthPercentage ?? 0) < 0) {
      return ArrowDropDownIcon;
    }
    return null;
  }, [growthPercentage, hasGrowth]);

  if (!hasGrowth || !GrowthIcon) return null;

  const isSmall = size === "small";

  return (
    <Chip
      size={size}
      icon={
        <GrowthIcon
          sx={{
            fontSize: isSmall ? 16 : 18,
          }}
        />
      }
      label={growthFormatted}
      sx={{
        bgcolor: growthBgColor,
        color: growthColor,
        fontWeight: 700,
        fontSize: isSmall ? 12 : 13,
        height: "auto",
        "& .MuiChip-label": {
          px: isSmall ? 0.375 : 0.5,
          py: isSmall ? 0.125 : 0.25,
          fontSize: isSmall ? 12 : 13,
          fontWeight: 700,
        },
        "& .MuiChip-icon": {
          color: growthColor,
          fontSize: isSmall ? 16 : 18,
        },
        ...(sx as any),
      }}
    />
  );
};
