"use client";
import * as React from "react";
import { useRouter } from "next/navigation";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useTheme } from "@mui/material/styles";
import DarkModeRoundedIcon from "@mui/icons-material/DarkModeRounded";
import LightModeRoundedIcon from "@mui/icons-material/LightModeRounded";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { ChatDrawer } from "@/shared/components/ui/feedback/Chat";
import { useAuth } from "@/shared/providers";
import { Button } from "@/shared/components/ui/primitives/Button";
import { usePageTitleValue } from "@/shared/hooks/ui/usePageTitle";
import { Breadcrumb } from "@/shared/components/ui/navigation/Breadcrumb";
import { useTranslation } from "react-i18next";
import { useFeatureFlagEnabled } from "@/shared/hooks/useFeatureFlag";
import { Logo } from "@/shared/ui/brand/Logo";
import { HeaderPendingActionsBadge } from "@/shared/ui/shell/HeaderPendingActionsBadge";
import { UserProfileMenu } from "@/shared/ui/shell/UserProfileMenu";
import type { RouteConfig } from "@/shared/components/ui/navigation/Breadcrumb";

export function AppHeader() {
  const { t } = useTranslation();
  const { mode, toggle } = useThemeMode();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const prefersReducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const isDark = mode === "dark";
  const [chatDrawerOpen, setChatDrawerOpen] = React.useState(false);
  const { isAuthenticated, user, signOut } = useAuth();
  const router = useRouter();
  const pageTitle = usePageTitleValue();
  const isAssistantEnabled = useFeatureFlagEnabled("assistant-enable");
  const contentRef = React.useRef<HTMLDivElement | null>(null);
  const [contentHeight, setContentHeight] = React.useState<number | null>(null);

  const handleChatIconClick = () => {
    setChatDrawerOpen(true);
  };

  const handleChatDrawerClose = () => {
    setChatDrawerOpen(false);
  };

  const handleSignIn = () => {
    router.push("/signin");
  };

  const breadcrumbRouteConfig = React.useMemo<RouteConfig[]>(
    () => [
      { path: "/", label: t("routes.home") },
      { path: "/financial-data", label: t("routes.financialData") },
      { path: "/financial-data/bacen-institutions", label: t("routes.financialInstitutions") },
      { path: "/investments", label: t("routes.investments") },
      { path: "/personal-finance", label: t("routes.personalFinance") },
      { path: "/personal-finance/investments", label: t("routes.investments") },
      { path: "/profile", label: t("routes.profile") },
      { path: "/settings", label: t("routes.settings") },
    ],
    [t]
  );

  React.useLayoutEffect(() => {
    const el = contentRef.current;
    if (!el) return;
    setContentHeight(Math.ceil(el.getBoundingClientRect().height));
  }, []);

  React.useEffect(() => {
    const el = contentRef.current;
    if (!el) return;

    const update = () => {
      setContentHeight(Math.ceil(el.getBoundingClientRect().height));
    };

    const observer = new ResizeObserver(update);
    observer.observe(el);

    return () => {
      observer.disconnect();
    };
  }, []);

  return (
    <Box
      component="header"
      sx={{
        position: "fixed",
        top: 0,
        left: { xs: 0, md: "84px" }, // Account for sidebar width only on desktop
        right: 0,
        zIndex: (t) => t.zIndex.appBar,
        bgcolor: "background.paper",
        borderBottom: "1px solid",
        borderColor: "divider",
        backdropFilter: "blur(6px)",
        display: { xs: "none", md: "block" }, // Hide on mobile as requested
        height: contentHeight ? `${contentHeight}px` : "auto",
        overflow: "hidden",
        transition: prefersReducedMotion
          ? "none"
          : theme.transitions.create("height", {
              duration: theme.transitions.duration.shorter,
              easing: theme.transitions.easing.easeInOut,
            }),
        willChange: prefersReducedMotion ? undefined : "height",
      }}
    >
      <Box ref={contentRef}>
        <Container maxWidth={false} sx={{ py: { xs: 1, md: 1.5 } }}>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: { xs: 1, md: 2 },
            }}
          >
            {/* Left Section: Logo (Mobile) + Page Title */}
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1.5,
                flex: "0 1 auto",
                minWidth: 0,
              }}
            >
              {/* Mobile Logo */}
              <Box sx={{ display: { xs: "block", md: "none" } }}>
                <Logo size="small" />
              </Box>

              {/* Page Title and Breadcrumb */}
              {pageTitle && (
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    gap: 0.5,
                    minWidth: 0, // Allow text truncation
                  }}
                >
                <Typography
                  variant={isMobile ? "h6" : "h4"}
                  component="h1"
                  sx={{
                    fontWeight: 600,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {pageTitle}
                </Typography>
                <Box sx={{ display: { xs: "none", md: "block" } }}>
                  <Breadcrumb
                    homeLabel={t("routes.home")}
                    routeConfig={breadcrumbRouteConfig}
                  />
                </Box>
              </Box>
            )}
          </Box>

          {/* Actions - Right aligned */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "flex-end",
              gap: { xs: 1, md: 1 },
              flex: "0 0 auto", // Prevent actions from shrinking
            }}
          >
            <Tooltip title={isDark ? t("appHeader.theme.light") : t("appHeader.theme.dark")}>
              <IconButton
                aria-label="toggle theme"
                onClick={toggle}
                sx={{ display: { xs: "none", md: "inline-flex" } }}
              >
                {isDark ? <LightModeRoundedIcon /> : <DarkModeRoundedIcon />}
              </IconButton>
            </Tooltip>
            <HeaderPendingActionsBadge />
            {isAssistantEnabled && (
              <Box sx={{ display: { xs: "none", md: "inline-flex" } }}>
                <Tooltip title={t("appHeader.aiAssistant")}>
                  <IconButton
                    aria-label="Open AI assistant"
                    onClick={handleChatIconClick}
                  >
                    <AutoAwesomeIcon />
                  </IconButton>
                </Tooltip>
              </Box>
            )}
            {isAuthenticated ? (
              <UserProfileMenu
                user={user}
                onSignOut={signOut}
                onOpenProfile={() => router.push("/profile")}
                t={t}
              />
            ) : (
              <Button
                variant="contained"
                size="medium"
                onClick={handleSignIn}
                aria-label={t("appHeader.signIn")}
                data-testid="header-signin-button"
                name="header-signin"
                sx={{ height: (theme) => theme.spacing(5.5), px: 2.5 }}
              >
                {t("appHeader.signIn")}
              </Button>
            )}
          </Box>
          </Box>
        </Container>
      </Box>
      {isAssistantEnabled && (
        <ChatDrawer open={chatDrawerOpen} onClose={handleChatDrawerClose} />
      )}
    </Box>
  );
}
