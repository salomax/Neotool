import React from "react";
import { useRouter, usePathname } from "next/navigation";
import useMediaQuery from "@mui/material/useMediaQuery";
import { 
  Box, 
  Paper, 
  Typography, 
  useTheme, 
  Avatar,
  HomeIcon,
  MenuIcon,
  PersonIcon
} from "@/shared/ui/mui-imports";
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import { alpha } from "@mui/material/styles";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/shared/providers";
import { getInitials } from "@/shared/utils/user";

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

export const BottomNavigationBar: React.FC<BottomNavigationBarProps> = ({
  onMoreClick
}) => {
  const theme = useTheme();
  const router = useRouter();
  const pathname = usePathname();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const { t } = useTranslation();
  const { isAuthenticated, user, signIn } = useAuth();

  if (!isMobile) {
    return null;
  }

  const items: BottomNavItem[] = [
    {
      id: "home",
      label: t("routes.home", "Home"),
      icon: <HomeIcon />,
      path: "/",
    },
    {
      id: "data",
      label: t("routes.financialData", "Data"),
      icon: <AccountBalanceIcon />,
      path: "/financial-data",
    },
    {
      id: "profile",
      label: isAuthenticated ? t("routes.profile", "Profile") : t("appHeader.signIn", "Sign In"),
      icon: isAuthenticated ? (
        <Avatar
          sx={{ width: 24, height: 24, fontSize: '0.75rem' }}
          src={user?.avatarUrl || undefined}
        >
          {user ? getInitials(user) : <PersonIcon sx={{ fontSize: 16 }} />}
        </Avatar>
      ) : (
        <PersonIcon />
      ),
      path: isAuthenticated ? "/profile" : "/signin",
    },
    {
      id: "menu",
      label: t("routes.menu", "Menu"),
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

  const isActive = (path?: string) => {
    if (!path) return false;
    if (path === "/" && pathname === "/") return true;
    if (path !== "/" && pathname?.startsWith(path)) return true;
    return false;
  };

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
        justifyContent: "space-evenly",
        alignItems: "center",
      }}
      role="navigation"
    >
      {items.map((item) => {
        const active = isActive(item.path);
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
              width: theme.custom?.layout?.mobile?.touchTarget || 56,
              height: theme.custom?.layout?.mobile?.touchTarget || 56,
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
                  fontSize: 24,
                  color: color,
                },
                marginBottom: "4px",
              }}
            >
              {item.icon}
            </Box>

            {/* Label */}
            <Typography
              variant="caption"
              sx={{
                fontSize: "12px",
                fontWeight: 500,
                color: color,
                lineHeight: 1,
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
