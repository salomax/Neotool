import React from "react";
import { useRouter, usePathname } from "next/navigation";
import useMediaQuery from "@mui/material/useMediaQuery";
import { 
  Box, 
  Paper, 
  Typography, 
  useTheme, 
  HomeIcon,
  MenuIcon
} from "@/shared/ui/mui-imports";
import InsertChartIcon from '@mui/icons-material/InsertChart';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import RepeatIcon from '@mui/icons-material/Repeat';
import { alpha } from "@mui/material/styles";
import { useFeatureFlagEnabled } from "@/shared/hooks/useFeatureFlag";
import { useTranslation } from "react-i18next";

export interface BottomNavItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  activeIcon?: React.ReactNode;
  path?: string;
  action?: () => void;
  badge?: number | string;
}

export interface BottomNavigationBarProps {
  onMoreClick?: () => void;
}

function getActivePath(pathname: string | null, items: BottomNavItem[]): string | null {
  if (!pathname) return null;

  let bestMatch: string | null = null;
  let bestMatchLength = -1;

  for (const item of items) {
    const path = item.path;
    if (!path) continue;

    const matches =
      path === "/"
        ? pathname === "/"
        : pathname === path || pathname.startsWith(`${path}/`);

    if (!matches || path.length <= bestMatchLength) continue;

    bestMatch = path;
    bestMatchLength = path.length;
  }

  return bestMatch;
}

export const BottomNavigationBar: React.FC<BottomNavigationBarProps> = ({
  onMoreClick
}) => {
  const theme = useTheme();
  const router = useRouter();
  const pathname = usePathname();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const isCompactViewport = useMediaQuery("(max-width:390px)");
  const { t } = useTranslation("common");
  const personalFinanceEnabled = useFeatureFlagEnabled("personal-finance-dashboard-enabled");

  if (!isMobile) {
    return null;
  }

  const items: BottomNavItem[] = personalFinanceEnabled
    ? [
        {
          id: "dashboard",
          label: t("routes.dashboard"),
          icon: <InsertChartIcon />,
          path: "/personal-finance",
        },
        {
          id: "transactions",
          label: t("routes.transactions"),
          icon: <SwapHorizIcon />,
          path: "/personal-finance/finance-transactions",
        },
        {
          id: "portfolio",
          label: t("routes.portfolio"),
          icon: <SavingsOutlinedIcon />,
          path: "/personal-finance/investments",
        },
        {
          id: "recurring",
          label: t("routes.recurringPayments"),
          icon: <RepeatIcon />,
          path: "/personal-finance/recurring-payments",
        },
        {
          id: "menu",
          label: t("routes.more"),
          icon: <MenuIcon />,
          action: onMoreClick,
        },
      ]
    : [
        {
          id: "home",
          label: t("routes.home"),
          icon: <HomeIcon />,
          path: "/",
        },
        {
          id: "menu",
          label: t("routes.more"),
          icon: <MenuIcon />,
          action: onMoreClick,
        },
      ];

  const handleNavigation = (item: BottomNavItem) => {
    if (item.action) {
      item.action();
    } else if (item.path) {
      router.push(item.path);
    }
  };
  const activePath = getActivePath(pathname, items);

  return (
    <Paper
      elevation={8}
      sx={{
        position: "fixed",
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: theme.zIndex.bottomNav || 1101,
        height: `calc(${theme.custom?.layout?.mobile?.bottomNavHeight || 64}px + env(safe-area-inset-bottom))`,
        paddingBottom: "env(safe-area-inset-bottom)",
        backgroundColor: alpha(theme.palette.background.paper, 0.8),
        backdropFilter: "blur(6px)",
        borderTop: `1px solid ${theme.palette.divider}`,
        borderRadius: 0,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        px: isCompactViewport ? 0 : 0.5,
      }}
      role="navigation"
    >
      {items.map((item) => {
        const active = !!item.path && item.path === activePath;
        const color = active ? theme.palette.primary.main : theme.custom?.palette?.sidebarIcon || theme.palette.text.secondary;

        return (
          <Box
            key={item.id}
            onClick={() => handleNavigation(item)}
            role="button"
            aria-label={item.label}
            aria-current={active ? "page" : undefined}
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              flex: "1 1 0",
              minWidth: 0,
              height: theme.custom?.layout?.mobile?.touchTarget || 56,
              px: 0,
              cursor: "pointer",
              color: color,
              transition: "color 0.2s ease-in-out",
              "&:active": {
                transform: "scale(0.95)",
              },
            }}
          >
            {/* Icon */}
            <Box
              sx={{
                "& svg": {
                  fontSize: isCompactViewport ? 22 : 24,
                  color: color,
                },
                marginBottom: isCompactViewport ? "1px" : "3px",
              }}
            >
              {item.icon}
            </Box>

            {/* Label */}
            <Typography
              variant="caption"
              sx={{
                width: "100%",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                textAlign: "center",
                fontSize: isCompactViewport ? "9px" : "11px",
                fontWeight: 500,
                color: color,
                lineHeight: isCompactViewport ? 0.95 : 1,
              }}
            >
              {item.label}
            </Typography>
          </Box>
        );
      })}
    </Paper>
  );
};

export default BottomNavigationBar;
