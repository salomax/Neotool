"use client";

import * as React from "react";
import Link from "next/link";
import CloseIcon from "@mui/icons-material/Close";
import {
  Box,
  Button,
  IconButton,
  Typography,
  useTheme,
} from "@mui/material";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useTranslation } from "react-i18next";
import { useFeatureFlagEnabled } from "@/shared/hooks/useFeatureFlag";
import { LogoMark } from "@/shared/ui/brand";

const DISMISSED_STORAGE_KEY = "invistus:mobile-banner-dismissed";

export function MobileAppBanner() {
  const { t } = useTranslation("common");
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const showBanner = useFeatureFlagEnabled("mobile-app-message-show");
  const [dismissed, setDismissed] = React.useState(true);

  React.useEffect(() => {
    if (!isMobile || !showBanner) {
      setDismissed(true);
      return;
    }

    const storedValue = window.localStorage.getItem(DISMISSED_STORAGE_KEY);
    setDismissed(storedValue === "true");
  }, [isMobile, showBanner]);

  const handleDismiss = React.useCallback(() => {
    window.localStorage.setItem(DISMISSED_STORAGE_KEY, "true");
    setDismissed(true);
  }, []);

  if (!isMobile || !showBanner || dismissed) {
    return null;
  }

  return (
    <Box
      data-testid="mobile-app-banner"
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1.5,
        px: 2,
        py: 1.5,
        borderBottom: "1px solid",
        borderColor: "divider",
        bgcolor: "background.paper",
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          width: 44,
          height: 44,
          flexShrink: 0,
        }}
      >
        <LogoMark width={28} height={26} />
      </Box>

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: 700, color: "text.primary" }}>
          {t("mobileAppBanner.title")}
        </Typography>
        <Typography variant="caption" sx={{ color: "text.secondary" }}>
          {t("mobileAppBanner.subtitle")}
        </Typography>
        <Box sx={{ display: "flex", gap: 1, mt: 1.25, flexWrap: "wrap" }}>
          {/*
          <Button
            component={Link}
            href="#"
            variant="outlined"
            size="small"
            sx={{ minHeight: 44, px: 1.5 }}
          >
            {t("mobileAppBanner.appStore")}
          </Button>
          */}
          <Button
            component={Link}
            href="#"
            variant="outlined"
            size="small"
            sx={{ minHeight: 44, px: 1.5 }}
          >
            {t("mobileAppBanner.playStore")}
          </Button>
        </Box>
      </Box>

      <IconButton
        aria-label={t("mobileAppBanner.dismiss")}
        onClick={handleDismiss}
        sx={{ width: 44, height: 44, color: "text.secondary", flexShrink: 0 }}
      >
        <CloseIcon />
      </IconButton>
    </Box>
  );
}

export default MobileAppBanner;
