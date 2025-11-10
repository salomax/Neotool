"use client";
import * as React from "react";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Paper from "@mui/material/Paper";
import InputBase from "@mui/material/InputBase";
import IconButton from "@mui/material/IconButton";
import Badge from "@mui/material/Badge";
import Avatar from "@mui/material/Avatar";
import Tooltip from "@mui/material/Tooltip";
import Button from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Divider from "@mui/material/Divider";
import Typography from "@mui/material/Typography";
import { alpha } from '@mui/material/styles';
import Link from "next/link";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import DarkModeRoundedIcon from "@mui/icons-material/DarkModeRounded";
import NotificationsNoneRoundedIcon from "@mui/icons-material/NotificationsNoneRounded";
import LoginRoundedIcon from "@mui/icons-material/LoginRounded";
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import SettingsIcon from "@mui/icons-material/Settings";
import LogoutIcon from "@mui/icons-material/Logout";
import { useAuth } from "@/shared/providers/AuthProvider";
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { headerTranslations } from "./i18n";
import { ConfirmationDialog } from "@/shared/components/ui/feedback/ConfirmationDialog";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import LightModeRoundedIcon from "@mui/icons-material/LightModeRounded";

export function AppHeader() {
  const { isAuthenticated, user, isLoading, signOut } = useAuth();
  const { t } = useTranslation(headerTranslations);
  const { mode, toggle: toggleTheme } = useThemeMode();
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const [logoutDialogOpen, setLogoutDialogOpen] = React.useState(false);
  const menuOpen = Boolean(anchorEl);
  
  const isDarkMode = mode === "dark";

  // Get user initials for avatar
  const getUserInitials = () => {
    if (!user) return "?";
    if (user.displayName) {
      return user.displayName
        .split(" ")
        .map((n) => n[0])
        .join("")
        .toUpperCase()
        .slice(0, 2);
    }
    return user?.email?.[0]?.toUpperCase() || "?";
  };

  const handleProfileMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleProfileMenuClose = () => {
    setAnchorEl(null);
  };

  const handleLogoutClick = () => {
    handleProfileMenuClose();
    setLogoutDialogOpen(true);
  };

  const handleLogoutConfirm = () => {
    setLogoutDialogOpen(false);
    signOut();
  };

  const handleLogoutCancel = () => {
    setLogoutDialogOpen(false);
  };

  // Close menu on Escape key
  React.useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && menuOpen) {
        handleProfileMenuClose();
      }
    };
    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [menuOpen]);

  return (
    <Box
      component="header"
      sx={{
        position: "fixed",
        top: 0,
        left: "84px", // Account for sidebar width
        right: 0,
        zIndex: (t) => t.zIndex.appBar,
        bgcolor: "background.paper",
        borderBottom: "1px solid",
        borderColor: "divider",
        backdropFilter: "blur(6px)",
      }}
    >
      <Container maxWidth="xl" sx={{ py: 1.5 }}>
        {/* grid: [esquerda vazia] [busca central] [ações à direita] */}
        <Box
          sx={{
            display: "grid",
            alignItems: "center",
            gridTemplateColumns: "1fr minmax(360px, 680px) 1fr",
            gap: 2,
          }}
        >
          <Box aria-hidden />

          {/* Busca central */}
          <Paper
            component="form"
            onSubmit={(e) => e.preventDefault()}
            sx={{
              px: 1,
              py: 0.25,
              display: "flex",
              alignItems: "center",
              boxShadow: "none",
              border: "1px solid",
              borderColor: "divider",
              bgcolor: (t) =>
                alpha(
                  t.palette.common.white,
                  t.palette.mode === "light" ? 1 : 0.06,
                ),
            }}
          >
            <InputBase
              sx={{ ml: 1, flex: 1 }}
              placeholder="Search here"
              inputProps={{ "aria-label": "search" }}
            />
            <IconButton type="submit" aria-label="search" size="small">
              <SearchRoundedIcon />
            </IconButton>
          </Paper>

          {/* Ações à direita */}
          <Box
            sx={{
              justifySelf: "end",
              display: "flex",
              alignItems: "center",
              gap: 1,
            }}
          >
            <Tooltip title={isDarkMode ? t('theme.switchToLight') : t('theme.switchToDark')}>
              <IconButton 
                aria-label="toggle theme" 
                onClick={toggleTheme}
                data-testid="button-theme-toggle"
              >
                {isDarkMode ? <LightModeRoundedIcon /> : <DarkModeRoundedIcon />}
              </IconButton>
            </Tooltip>
            {/* TODO: Implement notifications functionality */}
            {/* <Tooltip title="Notifications">
              <IconButton aria-label="notifications">
                <Badge variant="dot" color="primary">
                  <NotificationsNoneRoundedIcon />
                </Badge>
              </IconButton>
            </Tooltip> */}
            {!isLoading && (
              <>
                {isAuthenticated ? (
                  <>
                    <Tooltip title={t('profileMenu')}>
                      <IconButton 
                        aria-label={t('profileMenu')}
                        aria-controls={menuOpen ? 'profile-menu' : undefined}
                        aria-haspopup="true"
                        aria-expanded={menuOpen ? 'true' : undefined}
                        onClick={handleProfileMenuOpen}
                        data-testid="button-profile"
                        sx={{
                          p: 0.5,
                        }}
                      >
                        <Avatar sx={{ width: 32, height: 32 }}>
                          {getUserInitials()}
                        </Avatar>
                      </IconButton>
                    </Tooltip>
                    <Menu
                      id="profile-menu"
                      anchorEl={anchorEl}
                      open={menuOpen}
                      onClose={handleProfileMenuClose}
                      onClick={handleProfileMenuClose}
                      transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                      anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                      slotProps={{
                        paper: {
                          sx: {
                            mt: 1.5,
                            minWidth: 200,
                            maxWidth: { xs: 'calc(100vw - 32px)', sm: 280 },
                            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
                            '& .MuiMenuItem-root': {
                              px: 2,
                              py: 1.5,
                            },
                          },
                        },
                      }}
                      data-testid="profile-menu"
                    >
                      {/* User Info Section */}
                      <Box sx={{ px: 2, py: 1.5 }}>
                        <Typography variant="subtitle2" noWrap sx={{ fontWeight: 600 }}>
                          {user?.displayName || user?.email}
                        </Typography>
                        {user?.displayName && (
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {user.email}
                          </Typography>
                        )}
                      </Box>
                      <Divider />
                      
                      {/* Menu Items */}
                      <MenuItem 
                        component={Link}
                        href="/profile"
                        data-testid="menu-item-my-account"
                      >
                        <AccountCircleIcon sx={{ mr: 1.5, fontSize: 20 }} />
                        {t('myAccount')}
                      </MenuItem>
                      <MenuItem 
                        component={Link}
                        href="/settings"
                        data-testid="menu-item-settings"
                      >
                        <SettingsIcon sx={{ mr: 1.5, fontSize: 20 }} />
                        {t('settings')}
                      </MenuItem>
                      <Divider />
                      <MenuItem 
                        onClick={handleLogoutClick}
                        data-testid="menu-item-sign-out"
                        sx={{
                          color: 'error.main',
                          '&:hover': {
                            bgcolor: 'error.lighter',
                          },
                        }}
                      >
                        <LogoutIcon sx={{ mr: 1.5, fontSize: 20 }} />
                        {t('signOut')}
                      </MenuItem>
                    </Menu>
                  </>
                ) : (
                  <Box data-testid="button-login">
                    <Button
                      component={Link}
                      href="/signin"
                      variant="contained"
                      size="medium"
                      startIcon={<LoginRoundedIcon />}
                    >
                      {t('signIn')}
                    </Button>
                  </Box>
                )}
              </>
            )}
          </Box>
        </Box>
      </Container>
      
      {/* Logout Confirmation Dialog */}
      <ConfirmationDialog
        open={logoutDialogOpen}
        onClose={handleLogoutCancel}
        onConfirm={handleLogoutConfirm}
        title={t('logout.title')}
        message={t('logout.message')}
        confirmText={t('logout.confirm')}
        cancelText={t('logout.cancel')}
        confirmColor="error"
        data-testid="logout-confirmation-dialog"
      />
    </Box>
  );
}
