"use client";
import * as React from "react";
import { useRouter } from "next/navigation";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import IconButton from "@mui/material/IconButton";
import Avatar from "@mui/material/Avatar";
import Tooltip from "@mui/material/Tooltip";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Divider from "@mui/material/Divider";
import Typography from "@mui/material/Typography";
import DarkModeRoundedIcon from "@mui/icons-material/DarkModeRounded";
import LightModeRoundedIcon from "@mui/icons-material/LightModeRounded";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import LogoutIcon from "@mui/icons-material/Logout";
import PersonIcon from "@mui/icons-material/Person";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { ChatDrawer } from "@/shared/components/ui/feedback/Chat";
import { useAuth } from "@/shared/providers";
import { Button } from "@/shared/components/ui/primitives/Button";
import { usePageTitleValue } from "@/shared/hooks/ui/usePageTitle";

export function AppHeader() {
  const { mode, toggle } = useThemeMode();
  const isDark = mode === "dark";
  const [chatDrawerOpen, setChatDrawerOpen] = React.useState(false);
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const { isAuthenticated, user, signOut, isLoading } = useAuth();
  const router = useRouter();
  const pageTitle = usePageTitleValue();

  const handleChatIconClick = () => {
    setChatDrawerOpen(true);
  };

  const handleChatDrawerClose = () => {
    setChatDrawerOpen(false);
  };

  const handleProfileMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleProfileMenuClose = () => {
    setAnchorEl(null);
  };

  const handleSignIn = () => {
    router.push("/signin");
  };

  const handleSignOut = () => {
    handleProfileMenuClose();
    signOut();
  };

  const getInitials = (user: { displayName?: string | null; email: string } | null) => {
    if (!user) return "?";
    if (user.displayName) {
      const names = user.displayName.trim().split(/\s+/);
      if (names.length >= 2) {
        const first = names[0]?.[0];
        const last = names[names.length - 1]?.[0];
        if (first && last) {
          return `${first}${last}`.toUpperCase();
        }
      }
      const firstChar = user.displayName.trim()[0];
      if (firstChar) {
        return firstChar.toUpperCase();
      }
    }
    const emailChar = user.email?.[0];
    return emailChar ? emailChar.toUpperCase() : "?";
  };

  const open = Boolean(anchorEl);

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
      <Container maxWidth={false} sx={{ py: 1.5 }}>
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 2,
          }}
        >
          {/* Page Title - Left aligned */}
          {pageTitle && (
            <Typography
              variant="h4"
              component="h1"
              sx={{
                fontWeight: 600,
                flex: "0 1 auto",
                minWidth: 0, // Allow text truncation
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {pageTitle}
            </Typography>
          )}

          {/* Actions - Right aligned */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "flex-end",
              gap: 1,
              flex: pageTitle ? "0 0 auto" : "1 1 auto",
            }}
          >
            <Tooltip title={isDark ? "Switch to light mode" : "Switch to dark mode"}>
              <IconButton aria-label="toggle theme" onClick={toggle}>
                {isDark ? <LightModeRoundedIcon /> : <DarkModeRoundedIcon />}
              </IconButton>
            </Tooltip>
            <Tooltip title="AI Assistant">
              <IconButton aria-label="Open AI assistant" onClick={handleChatIconClick}>
                <AutoAwesomeIcon />
              </IconButton>
            </Tooltip>
            {isAuthenticated ? (
              <Tooltip title="Account">
                <IconButton 
                  aria-label="Account menu"
                  onClick={handleProfileMenuOpen}
                  aria-controls={open ? "profile-menu" : undefined}
                  aria-haspopup="true"
                  aria-expanded={open ? "true" : undefined}
                >
                  <Avatar
                    sx={{ width: 32, height: 32 }}
                    src={user?.avatarUrl || undefined}
                  >
                    {user ? getInitials(user) : <PersonIcon />}
                  </Avatar>
                </IconButton>
              </Tooltip>
            ) : (
              <Button
                variant="contained"
                size="small"
                onClick={handleSignIn}
                aria-label="Sign in"
                data-testid="header-signin-button"
                name="header-signin"
              >
                Sign In
              </Button>
            )}
          </Box>
        </Box>
      </Container>
      <ChatDrawer open={chatDrawerOpen} onClose={handleChatDrawerClose} />
      <Menu
        id="profile-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleProfileMenuClose}
        onClick={handleProfileMenuClose}
        transformOrigin={{ horizontal: "right", vertical: "top" }}
        anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        PaperProps={{
          elevation: 3,
          sx: {
            mt: 1.5,
            minWidth: 200,
            "& .MuiMenuItem-root": {
              px: 2,
              py: 1,
            },
          },
        }}
      >
        {user && [
          <Box key="user-info" sx={{ px: 2, py: 1.5 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              {user.displayName || "User"}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {user.email}
            </Typography>
          </Box>,
          <Divider key="divider" />,
          <MenuItem key="sign-out" onClick={handleSignOut}>
            <LogoutIcon sx={{ mr: 1.5, fontSize: 20 }} />
            Sign Out
          </MenuItem>
        ]}
      </Menu>
    </Box>
  );
}
