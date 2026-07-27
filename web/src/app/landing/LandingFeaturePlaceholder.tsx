"use client";

import * as React from "react";
import {
  alpha,
  Box,
  Chip,
  Stack,
  Typography,
  useTheme,
} from "@/shared/ui/mui-imports";
import type { SvgIconComponent } from "@mui/icons-material";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import CompareArrowsIcon from "@mui/icons-material/CompareArrows";
import DashboardCustomizeIcon from "@mui/icons-material/DashboardCustomize";
import RepeatIcon from "@mui/icons-material/Repeat";
import SavingsIcon from "@mui/icons-material/Savings";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import ShowChartIcon from "@mui/icons-material/ShowChart";
import type { LandingFeatureAccent, LandingFeatureVisualIcon } from "./featureCatalog";

const ICON_MAP: Record<LandingFeatureVisualIcon, SvgIconComponent> = {
  dashboard: DashboardCustomizeIcon,
  automation: AutoAwesomeIcon,
  recurring: RepeatIcon,
  wishlist: SavingsIcon,
  institutions: AccountBalanceIcon,
  indicators: ShowChartIcon,
  fixedIncome: CompareArrowsIcon,
};

const ACCENT_MAP: Record<LandingFeatureAccent, "primary" | "secondary" | "success" | "info"> = {
  primary: "primary",
  secondary: "secondary",
  success: "success",
  info: "info",
};

interface LandingFeaturePlaceholderProps {
  accent: LandingFeatureAccent;
  icon: LandingFeatureVisualIcon;
  title: string;
  highlights: string[];
}

export function LandingFeaturePlaceholder({
  accent,
  icon,
  title,
  highlights,
}: LandingFeaturePlaceholderProps) {
  const theme = useTheme();
  const Icon = ICON_MAP[icon];
  const accentColor = theme.palette[ACCENT_MAP[accent]].main;

  return (
    <Box
      sx={{
        position: "relative",
        minHeight: { xs: 280, md: 360 },
        borderRadius: 6,
        overflow: "hidden",
        border: 1,
        borderColor: "divider",
        backgroundImage: `linear-gradient(135deg, ${alpha(accentColor, 0.16)} 0%, ${alpha(
          theme.palette.background.paper,
          0.88,
        )} 55%, ${alpha(accentColor, 0.08)} 100%)`,
        boxShadow: theme.custom.shadow(2, accentColor),
      }}
    >
      <Box
        sx={{
          position: "absolute",
          inset: 0,
          backgroundImage: `radial-gradient(circle at 18% 20%, ${alpha(accentColor, 0.26)} 0%, transparent 38%), radial-gradient(circle at 85% 82%, ${alpha(
            accentColor,
            0.18,
          )} 0%, transparent 36%)`,
        }}
      />

      <Box
        sx={{
          position: "relative",
          zIndex: 1,
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          p: { xs: 3, md: 4 },
        }}
      >
        <Stack spacing={2}>
          <Box
            sx={{
              width: 64,
              height: 64,
              borderRadius: 4,
              display: "grid",
              placeItems: "center",
              bgcolor: alpha(accentColor, 0.14),
              color: accentColor,
              border: 1,
              borderColor: alpha(accentColor, 0.28),
            }}
          >
            <Icon sx={{ fontSize: 34 }} />
          </Box>

          <Typography variant="h5" sx={{ maxWidth: 360, fontWeight: 700 }}>
            {title}
          </Typography>
        </Stack>

        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", maxWidth: 420 }}>
          {highlights.map((highlight) => (
            <Chip
              key={highlight}
              label={highlight}
              size="small"
              icon={<ReceiptLongIcon />}
              sx={{
                bgcolor: alpha(theme.palette.background.paper, 0.74),
                color: "text.primary",
                borderColor: alpha(accentColor, 0.18),
                "& .MuiChip-icon": {
                  color: accentColor,
                },
              }}
              variant="outlined"
            />
          ))}
        </Stack>
      </Box>
    </Box>
  );
}
