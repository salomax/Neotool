"use client";
import * as React from "react";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import IconButton from "@mui/material/IconButton";
import Avatar from "@mui/material/Avatar";
import Tooltip from "@mui/material/Tooltip";
import DarkModeRoundedIcon from "@mui/icons-material/DarkModeRounded";
import LightModeRoundedIcon from "@mui/icons-material/LightModeRounded";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { CartIcon } from "@/shared/components/ui/navigation";
import { CartDrawer } from "@/app/(neotool)/cart/components/CartDrawer";
import { ChatDrawer } from "@/shared/components/ui/feedback/Chat";

export function AppHeader() {
  const { mode, toggle } = useThemeMode();
  const isDark = mode === "dark";
  const [cartDrawerOpen, setCartDrawerOpen] = React.useState(false);
  const [chatDrawerOpen, setChatDrawerOpen] = React.useState(false);

  const handleCartIconClick = () => {
    setCartDrawerOpen(true);
  };

  const handleCartDrawerClose = () => {
    setCartDrawerOpen(false);
  };

  const handleChatIconClick = () => {
    setChatDrawerOpen(true);
  };

  const handleChatDrawerClose = () => {
    setChatDrawerOpen(false);
  };

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
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "flex-end",
            gap: 1,
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
            <Tooltip title="Shopping cart">
              <CartIcon onClick={handleCartIconClick} aria-label="Shopping cart" />
            </Tooltip>
            <Tooltip title="Profile">
              <IconButton aria-label="profile">
                <Avatar sx={{ width: 32, height: 32 }}>N</Avatar>
              </IconButton>
            </Tooltip>
        </Box>
      </Container>
      <CartDrawer open={cartDrawerOpen} onClose={handleCartDrawerClose} />
      <ChatDrawer open={chatDrawerOpen} onClose={handleChatDrawerClose} />
    </Box>
  );
}
