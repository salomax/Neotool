"use client";
import * as React from "react";
import Box from "@mui/material/Box";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useTheme } from "@mui/material/styles";
import { SidebarRail, BottomNavigationBar, MobileNavigationDrawer } from "@/shared/ui/navigation";
import { AppHeader } from "@/shared/ui/shell/AppHeader";
import { PageTitleProvider } from "@/shared/hooks/ui/usePageTitle";
import { BreadcrumbLabelProvider } from "@/shared/hooks/ui/useBreadcrumbLabel";
import { useElementHeight } from "@/shared/hooks/ui/useElementHeight";
import { RequireAuthGuard } from "@/shared/hooks/auth/useRequireAuth";

export function AppShell({ children }: { children: React.ReactNode }) {
  const headerHeight = useElementHeight("header", 80);
  const theme = useTheme();
  const prefersReducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false);

  const handleMoreClick = () => {
    setMobileMenuOpen(true);
  };

  const handleMobileMenuClose = () => {
    setMobileMenuOpen(false);
  };

  return (
    <PageTitleProvider>
      <BreadcrumbLabelProvider>
      <Box
        sx={{
          display: "flex",
          height: "100vh",
          bgcolor: "background.default",
          overflow: "hidden", // Prevent overflow
        }}
      >
        <Box sx={{ display: { xs: "none", md: "block" } }}>
          <SidebarRail />
        </Box>
        <Box 
          sx={{ 
            flex: 1, 
            display: "flex", 
            flexDirection: "column",
            marginLeft: { xs: 0, md: "84px" }, // Account for fixed sidebar width only on desktop
            overflow: "hidden", // Prevent overflow
          }}
        >
          <AppHeader />
          <Box 
            sx={{ 
              flex: 1,
              overflow: "auto", // Allow scrolling only in main content area
              marginTop: { xs: 0, md: `${headerHeight}px` }, // Account for fixed header height only on desktop
              transition: prefersReducedMotion
                ? "none"
                : theme.transitions.create("margin-top", {
                    duration: theme.transitions.duration.shorter,
                    easing: theme.transitions.easing.easeInOut,
                  }),
              willChange: prefersReducedMotion ? undefined : "margin-top",
              paddingBottom: { xs: "80px", md: 0 }, // Add padding for bottom nav on mobile
            }}
          >
            <RequireAuthGuard>{children}</RequireAuthGuard>
          </Box>
        </Box>
        <BottomNavigationBar onMoreClick={handleMoreClick} />
        <MobileNavigationDrawer 
          open={mobileMenuOpen} 
          onClose={handleMobileMenuClose} 
        />
      </Box>
      </BreadcrumbLabelProvider>
    </PageTitleProvider>
  );
}

export default AppShell;
