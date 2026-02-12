import React from "react";
import { useRouter } from "next/navigation";
import {
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
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { useAuthorization, useAuth } from "@/shared/providers";
import { useTranslation } from "react-i18next";

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
  const { signOut } = useAuth();

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
      <Drawer.Header title={t('routes.menu', 'Menu')} />
      
      <Drawer.Body>
        <Box sx={{ mx: -3, my: -2 }}>
          <List component="nav">
            <ListItemButton onClick={() => handleNavigation("/financial-data")}>
              <ListItemIcon>
                <AccountBalanceIcon />
              </ListItemIcon>
              <ListItemText primary={t('routes.financialData', 'Financial Data')} />
            </ListItemButton>

            {hasAuthorizationAccess && (
              <>
                <Divider sx={{ my: 1 }} />
                <ListItemButton onClick={() => handleNavigation("/settings")}>
                  <ListItemIcon>
                    <SettingsRoundedIcon />
                  </ListItemIcon>
                  <ListItemText primary={t('routes.settings', 'Settings')} />
                </ListItemButton>
              </>
            )}

            <Divider sx={{ my: 1 }} />

            <ListItem>
              <ListItemIcon>
                {isDark ? <DarkModeRoundedIcon /> : <LightModeRoundedIcon />}
              </ListItemIcon>
              <ListItemText 
                primary="Dark Mode" 
                secondary={isDark ? "On" : "Off"}
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
              <ListItemText primary="Sign Out" />
            </ListItemButton>
          </List>
        </Box>
      </Drawer.Body>
    </Drawer>
  );
};
