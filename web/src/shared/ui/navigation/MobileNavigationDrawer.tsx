import React from "react";
import { useRouter } from "next/navigation";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import {
  Avatar,
  Box,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  Switch,
  SettingsRoundedIcon,
  DarkModeRoundedIcon,
  LightModeRoundedIcon,
  LogoutIcon
} from "@/shared/ui/mui-imports";
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { useAuthorization, useAuth } from "@/shared/providers";
import { useFeatureFlagEnabled } from '@/shared/hooks/useFeatureFlag';
import { useTranslation } from "react-i18next";
import { getInitials } from "@/shared/utils/user";

export interface MobileNavigationDrawerProps {
  open: boolean;
  onClose: () => void;
}

export const MobileNavigationDrawer: React.FC<MobileNavigationDrawerProps> = ({
  open,
  onClose,
}) => {
  const router = useRouter();
  const { t } = useTranslation('common');
  const { mode, toggle } = useThemeMode();
  const isDark = mode === "dark";
  const { hasAny } = useAuthorization();
  const personalFinanceEnabled = useFeatureFlagEnabled('personal-finance-dashboard-enabled');
  const pricingEnabled = useFeatureFlagEnabled('pricing-enabled');
  const { signOut, user } = useAuth();

  // Check permissions (same as SidebarRail)
  const hasAuthorizationAccess = hasAny([
    'security:user:view',
    'security:user:save',
    'security:user:delete',
    'security:role:view',
    'security:role:save',
    'security:role:delete',
    'security:group:view',
    'security:group:save',
    'security:group:delete',
  ]);

  const handleNavigation = (path: string) => {
    router.push(path);
    onClose();
  };

  const handleSignOut = () => {
    onClose();
    signOut();
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="bottom"
      variant="temporary"
      sx={{
        zIndex: (theme) => theme.zIndex.drawer + 2, // Above bottom nav
      }}
    >
      <Drawer.Header title={t('routes.menu')} />
      
      <Drawer.Body>
        <Box sx={{ mx: -3, my: -2 }}>
          <List component="nav">
            <ListItemButton onClick={() => handleNavigation("/profile")}>
              <ListItemAvatar sx={{ minWidth: 44 }}>
                <Avatar
                  sx={{ width: 32, height: 32, fontSize: "0.875rem" }}
                  src={user?.avatarUrl ?? undefined}
                >
                  {user ? getInitials(user) : ""}
                </Avatar>
              </ListItemAvatar>
              <ListItemText primary={t('routes.profile')} />
            </ListItemButton>

            <ListItemButton onClick={() => handleNavigation("/financial-data")}>
              <ListItemIcon>
                <AccountBalanceIcon />
              </ListItemIcon>
              <ListItemText primary={t('routes.financialData')} />
            </ListItemButton>

            <ListItemButton onClick={() => handleNavigation("/investments")}>
              <ListItemIcon>
                <TrendingUpIcon />
              </ListItemIcon>
              <ListItemText primary={t('routes.investments')} />
            </ListItemButton>

            {personalFinanceEnabled && pricingEnabled && (
              <ListItemButton onClick={() => handleNavigation("/personal-finance/prices-consumption")}>
                <ListItemIcon>
                  <ShoppingCartOutlinedIcon />
                </ListItemIcon>
                <ListItemText primary={t('routes.pricesConsumption')} />
              </ListItemButton>
            )}

            {hasAuthorizationAccess && (
              <>
                <Divider sx={{ my: 1 }} />
                <ListItemButton onClick={() => handleNavigation("/settings")}>
                  <ListItemIcon>
                    <SettingsRoundedIcon />
                  </ListItemIcon>
                  <ListItemText primary={t('routes.settings')} />
                </ListItemButton>
              </>
            )}

            <Divider sx={{ my: 1 }} />

            <ListItem>
              <ListItemIcon>
                {isDark ? <DarkModeRoundedIcon /> : <LightModeRoundedIcon />}
              </ListItemIcon>
              <ListItemText 
                primary={t("appHeader.theme.dark")}
                secondary={isDark ? t("actions.enabled") : t("actions.disabled")}
              />
              <Switch
                edge="end"
                onChange={toggle}
                checked={isDark}
                inputProps={{
                  'aria-labelledby': 'switch-list-label-wifi',
                }}
              />
            </ListItem>

            <Divider sx={{ my: 1 }} />

            <ListItemButton onClick={handleSignOut} sx={{ color: 'error.main' }}>
              <ListItemIcon sx={{ color: 'error.main' }}>
                <LogoutIcon />
              </ListItemIcon>
              <ListItemText primary={t("appHeader.signOut")} />
            </ListItemButton>
          </List>
        </Box>
      </Drawer.Body>
    </Drawer>
  );
};
