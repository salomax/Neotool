"use client";

import { Box, Typography, Container, styled } from "@/shared/ui/mui-imports";
import { Theme } from "@mui/material/styles";
import { Wordmark } from "@/shared/ui/brand";
import { useTranslation } from "@/shared/i18n";
import { landingTranslations } from "../i18n";
import { LANDING_PAGE_CONTENT_SX } from "./landingPageLayout";

const BRAND_NAVY = "#0F223F";

const VIDEO_WEBM = "/videos/invistus_home.webm";
const VIDEO_MP4 = "/videos/invistus_home.mp4";

/** Desktop: where the video column starts (from the left). */
const VIDEO_LEFT_OFFSET = "40%";

const VIDEO_MASK = {
  mobile: "linear-gradient(to bottom, transparent 0%, #000 14%)",
  desktop: "linear-gradient(to right, transparent 0%, #000 22%)",
} as const;

const HeroSection = styled(Box)(({ theme }: { theme: Theme }) => ({
  backgroundColor: theme.palette.mode === "dark" ? "#0f172a" : "#f5f6f8",
  backgroundImage:
    theme.palette.mode === "dark"
      ? "radial-gradient(circle at 20% 50%, #1e293b 0%, transparent 55%), radial-gradient(circle at 10% 90%, #064e3b 0%, transparent 40%)"
      : "radial-gradient(circle at 20% 50%, #eceef2 0%, transparent 60%), radial-gradient(circle at 10% 90%, #f0f1f3 0%, transparent 45%)",
  position: "relative",
  overflow: "hidden",
}));

function LandingHeroVideo() {
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
      <Box
        component="video"
        autoPlay
        muted
        loop
        playsInline
        aria-label="Invistus home"
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
    </Box>
  );
}

function LandingHeroCopy() {
  const { t } = useTranslation(landingTranslations);

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
        variant="blue"
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
            color: BRAND_NAVY,
          }}
        >
          <Box component="span" sx={{ ml: "-6px", whiteSpace: "nowrap" }}>
            {t("hero.titlePrefix")}
          </Box>
          <br />
          <Box component="span" sx={{ color: "primary.main", whiteSpace: "nowrap" }}>
            {t("hero.titleHighlight")}
          </Box>
        </Typography>
        <Typography
          variant="h5"
          sx={{
            color: "text.secondary",
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
    <HeroSection>
      <Box sx={{ position: "relative", minHeight: { xs: "auto", lg: 560 } }}>
        <LandingHeroVideo />
        <LandingHeroCopy />
      </Box>
    </HeroSection>
  );
}
