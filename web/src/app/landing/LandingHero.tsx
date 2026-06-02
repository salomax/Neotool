"use client";

import { Box, Typography, Container, useTheme, alpha } from "@/shared/ui/mui-imports";
import { Theme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { Wordmark } from "@/shared/ui/brand";
import { useTranslation } from "@/shared/i18n";
import { landingTranslations } from "../i18n";
import { LANDING_PAGE_CONTENT_SX } from "./landingPageLayout";
import { BRAND_NAVY } from "./brandColors";

const VIDEO_WEBM = "/videos/invistus_home.webm";
const VIDEO_MP4 = "/videos/invistus_home.mp4";

/** Desktop: where the video column starts (from the left). */
const VIDEO_LEFT_OFFSET = "40%";

const VIDEO_MASK = {
  mobile: "linear-gradient(to bottom, transparent 0%, #000 14%)",
  desktop: "linear-gradient(to right, transparent 0%, #000 22%)",
} as const;

function heroSectionSx(theme: Theme) {
  const isDark = theme.palette.mode === "dark";

  if (isDark) {
    return {
      backgroundColor: theme.palette.background.paper,
      backgroundImage: `radial-gradient(circle at 20% 50%, ${theme.palette.background.default} 0%, transparent 55%), radial-gradient(circle at 10% 10%, ${alpha(theme.palette.primary.main, 0.35)} 0%, transparent 40%)`,
      position: "relative" as const,
      overflow: "hidden" as const,
    };
  }

  return {
    backgroundColor: theme.palette.background.default,
    backgroundImage: `radial-gradient(circle at 20% 50%, ${alpha(theme.palette.divider, 0.85)} 0%, transparent 60%), radial-gradient(circle at 10% 10%, ${alpha(theme.palette.divider, 0.55)} 0%, transparent 45%)`,
    position: "relative" as const,
    overflow: "hidden" as const,
  };
}

function LandingHeroVideo() {
  const { t } = useTranslation(landingTranslations);
  const prefersReducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");

  return (
    <Box
      sx={{
        position: { xs: "relative", lg: "absolute" },
        top: { lg: 0 },
        right: { lg: 0 },
        bottom: { lg: 0 },
        left: { lg: VIDEO_LEFT_OFFSET },
        minHeight: { xs: 360, sm: 420 },
        overflow: "hidden",
        zIndex: 0,
      }}
    >
      {!prefersReducedMotion && (
        <Box
          component="video"
          autoPlay
          muted
          loop
          playsInline
          aria-label={t("hero.videoLabel")}
          sx={{
            position: "absolute",
            inset: 0,
            width: "100%",
            height: "100%",
            objectFit: "cover",
            display: "block",
            maskImage: {
              xs: VIDEO_MASK.mobile,
              lg: VIDEO_MASK.desktop,
            },
            WebkitMaskImage: {
              xs: VIDEO_MASK.mobile,
              lg: VIDEO_MASK.desktop,
            },
          }}
        >
          <source src={VIDEO_WEBM} type="video/webm" />
          <source src={VIDEO_MP4} type="video/mp4" />
        </Box>
      )}
    </Box>
  );
}

function LandingHeroCopy() {
  const { t } = useTranslation(landingTranslations);
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";

  return (
    <Container
      maxWidth={false}
      sx={{
        ...LANDING_PAGE_CONTENT_SX,
        position: "relative",
        zIndex: 5,
        py: { xs: 4, lg: 10 },
        minHeight: { lg: 560 },
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        bgcolor: "transparent",
      }}
    >
      <Wordmark
        sx={{ ml: "2px", width: { xs: 173, lg: 346 } }}
        variant={isDark ? "white" : "blue"}
        size="medium"
      />

      <Box sx={{ mt: 4, maxWidth: 520, position: "relative" }}>
        <Typography
          variant="h1"
          component="h1"
          sx={{
            fontWeight: 700,
            fontSize: { xs: "2rem", lg: "4.5rem" },
            lineHeight: 1.1,
            mb: 3,
            color: isDark ? "primary.main" : BRAND_NAVY,
          }}
        >
          <Box
            component="span"
            sx={{
              // Optical align with wordmark left edge
              ml: "-6px",
              whiteSpace: "nowrap",
              color: isDark ? "text.primary" : "inherit",
            }}
          >
            {t("hero.titlePrefix")}
          </Box>
          <br />
          <Box component="span" sx={{ whiteSpace: "nowrap" }}>
            {t("hero.titleHighlight")}
          </Box>
        </Typography>
        <Typography
          variant="h5"
          sx={{
            color: isDark ? "text.primary" : "text.secondary",
            maxWidth: 500,
            lineHeight: 1.6,
            fontWeight: 400,
          }}
        >
          {t("hero.subtitle")}
        </Typography>
      </Box>
    </Container>
  );
}

/**
 * Landing hero: wordmark + title overlapping a masked background video.
 */
export function LandingHero() {
  return (
    <Box sx={(theme) => heroSectionSx(theme)}>
      <Box sx={{ position: "relative", minHeight: { xs: "auto", lg: 560 } }}>
        <LandingHeroVideo />
        <LandingHeroCopy />
      </Box>
    </Box>
  );
}
