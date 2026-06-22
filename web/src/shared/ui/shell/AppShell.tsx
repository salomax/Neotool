"use client";
import * as React from "react";
import { usePathname } from "next/navigation";
import Box from "@mui/material/Box";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useTheme } from "@mui/material/styles";
import { SidebarRail, BottomNavigationBar, MobileAppBanner, MobileNavigationDrawer } from "@/shared/ui/navigation";
import { AppHeader } from "@/shared/ui/shell/AppHeader";
import { PageTitleProvider } from "@/shared/hooks/ui/usePageTitle";
import { BreadcrumbLabelProvider } from "@/shared/hooks/ui/useBreadcrumbLabel";
import { useElementHeight } from "@/shared/hooks/ui/useElementHeight";
import { RequireAuthGuard } from "@/shared/hooks/auth/useRequireAuth";

const AUTH_ROUTE_PREFIXES = [
  "/signin",
  "/signup",
  "/forgot-password",
  "/reset-password",
  "/verify-email",
  "/verify-email-link",
] as const;

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const headerHeight = useElementHeight("header", 80);
  const theme = useTheme();
  const prefersReducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false);
  const hideMobileChrome = AUTH_ROUTE_PREFIXES.some((prefix) =>
    pathname === prefix || pathname?.startsWith(`${prefix}/`),
  );

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
        data-testid="app-layout"
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
            minWidth: 0, // Allow nested authenticated pages to shrink instead of pushing horizontal overflow.
            marginLeft: { xs: 0, md: "84px" }, // Account for fixed sidebar width only on desktop
            overflow: "hidden", // Prevent overflow
          }}
        >
          <AppHeader />
          {!hideMobileChrome ? <MobileAppBanner /> : null}
          <Box 
            sx={{ 
              flex: 1,
              minWidth: 0, // Keep responsive tables and cards inside the mobile viewport.
              overflow: "auto", // Allow scrolling only in main content area
              overflowX: "hidden", // Clamp authenticated route layouts to the viewport on narrow screens.
              marginTop: { xs: 0, md: `${headerHeight}px` }, // Account for fixed header height only on desktop
              transition: prefersReducedMotion
                ? "none"
                : theme.transitions.create("margin-top", {
                    duration: theme.transitions.duration.shorter,
                    easing: theme.transitions.easing.easeInOut,
                  }),
              willChange: prefersReducedMotion ? undefined : "margin-top",
              paddingBottom: { xs: hideMobileChrome ? 0 : "80px", md: 0 }, // Reserve bottom-nav space only where the mobile chrome is visible.
            }}
          >
            <RequireAuthGuard>{children}</RequireAuthGuard>
          </Box>
        </Box>
        {!hideMobileChrome ? <BottomNavigationBar onMoreClick={handleMoreClick} /> : null}
        {!hideMobileChrome ? (
          <MobileNavigationDrawer 
            open={mobileMenuOpen} 
            onClose={handleMobileMenuClose} 
          />
        ) : null}
      </Box>
      </BreadcrumbLabelProvider>
    </PageTitleProvider>
  );
}

export default AppShell;
