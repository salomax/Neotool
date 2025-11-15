"use client";
import * as React from "react";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import { SidebarRail } from "@/shared/ui/navigation/SidebarRail";
import { AppHeader } from "@/shared/ui/shell/AppHeader";

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <Box
      sx={{
        display: "flex",
        minHeight: "100vh",
        bgcolor: "background.default",
        overflow: "hidden", // Prevent overflow
      }}
    >
      <SidebarRail />
      <Box 
        sx={{ 
          flex: 1, 
          display: "flex", 
          flexDirection: "column",
          marginLeft: "84px", // Account for fixed sidebar width
          overflow: "hidden", // Prevent overflow
        }}
      >
        <AppHeader />
        <Box 
          sx={{ 
            flex: 1,
            overflow: "auto", // Allow scrolling only in main content area
            padding: 3,
            marginTop: "73px", // Account for fixed header height (approximate)
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
}

export default AppShell;
