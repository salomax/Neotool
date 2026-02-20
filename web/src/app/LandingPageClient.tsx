"use client";

import {
  Box,
  Typography,
  Container,
  Stack,
  styled,
  Link,
  useTheme,
  Grid,
  Chip,
  keyframes,
} from "@/shared/ui/mui-imports";
import { Theme } from "@mui/material/styles";
import { Wordmark, Logo } from "@/shared/ui/brand";
import { useTranslation } from "@/shared/i18n";
import { landingTranslations } from "./i18n";
import NextLink from "next/link";
import Image from "next/image";
import LinkedInIcon from "@mui/icons-material/LinkedIn";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import HomeIcon from "@mui/icons-material/Home";
import PaidIcon from "@mui/icons-material/Paid";
import BarChartIcon from "@mui/icons-material/BarChart";
import SavingsIcon from "@mui/icons-material/Savings";
import EastIcon from "@mui/icons-material/East";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import ShowChartIcon from "@mui/icons-material/ShowChart";

// Define animations
const float = keyframes`
  0% { transform: translateY(0px); }
  50% { transform: translateY(-20px); }
  100% { transform: translateY(0px); }
`;

// Styled components
const GradientSection = styled(Box)(({ theme }: { theme: Theme }) => ({
  background:
    theme.palette.mode === "dark"
      ? "radial-gradient(circle at 70% 50%, #1e293b 0%, transparent 50%), radial-gradient(circle at 90% 90%, #064e3b 0%, transparent 40%)"
      : "radial-gradient(circle at 70% 50%, #B7DEF6 0%, transparent 50%), radial-gradient(circle at 90% 90%, #dcfce7 0%, transparent 40%)",
  minHeight: "auto",
  paddingTop: theme.spacing(12),
  paddingBottom: theme.spacing(12),
  [theme.breakpoints.down("md")]: {
    paddingTop: theme.spacing(4),
    paddingBottom: theme.spacing(6),
  },
  display: "flex",
  alignItems: "center",
  position: "relative",
  overflow: "hidden",
}));

const GlassCard = styled(Box)(({ theme }: { theme: Theme }) => ({
  background:
    theme.palette.mode === "dark"
      ? "rgba(15, 23, 42, 0.6)"
      : "rgba(255, 255, 255, 0.4)",
  backdropFilter: "blur(12px)",
  border: `1px solid ${theme.palette.mode === "dark" ? "rgba(255, 255, 255, 0.1)" : "rgba(255, 255, 255, 0.3)"}`,
  borderRadius: (theme.shape.borderRadius as number) * 2,
  padding: theme.spacing(2),
  boxShadow: theme.shadows[4],
  position: "absolute",
  animation: `${float} 6s ease-in-out infinite`,
}));

const FeatureCard = styled(Box, {
  shouldForwardProp: (prop: string) =>
    prop !== "bgColor" && prop !== "textColor",
})<{ bgColor?: string; textColor?: string }>(
  ({
    theme,
    bgColor,
    textColor,
  }: {
    theme: Theme;
    bgColor?: string;
    textColor?: string;
  }) => ({
    backgroundColor: bgColor || theme.palette.background.paper,
    color: textColor || theme.palette.text.primary,
    borderRadius: 32,
    padding: theme.spacing(5),
    height: "100%",
    display: "flex",
    flexDirection: "column",
    justifyContent: "space-between",
    position: "relative",
    overflow: "hidden",
    transition: "all 0.5s ease",
    cursor: "pointer",
    "&:hover": {
      boxShadow: theme.shadows[10],
      transform: "translateY(-4px)",
    },
  }),
);

export default function WelcomePage() {
  const { t } = useTranslation(landingTranslations);

  const theme = useTheme();

  // Colors
  const brandNavy = "#0F223F";
  const brandSail = "#B7DEF6";

  return (
    <Box
      sx={{
        bgcolor: "background.default",
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
      }}
    >
      {/* Hero Section */}
      <GradientSection>
        <Container maxWidth="lg">
          <Grid container spacing={6} alignItems="center">
            {/* Text Content */}
            <Grid size={{ xs: 12, lg: 6 }} sx={{ zIndex: 10 }}>
              <Wordmark
                sx={{ ml: "2px", width: { xs: 173, lg: 346 } }}
                variant={theme.palette.mode === "dark" ? "white" : "blue"}
                size="medium"
              />
              <Box sx={{ mt: 4, mb: 6 }}>
                <Typography
                  variant="h1"
                  component="h1"
                  sx={{
                    fontWeight: 700,
                    fontSize: { xs: "2rem", lg: "4.5rem" },
                    lineHeight: 1.1,
                    mb: 3,
                    color: "text.primary",
                  }}
                >
                  <Box component="span" sx={{ ml: "-6px" }}>
                    {t("hero.titlePrefix")} <br />
                  </Box>
                  <Box component="span" sx={{ color: "primary.main" }}>
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
            </Grid>

            {/* Graphic Content */}
            <Grid
              size={{ xs: 12, lg: 6 }}
              sx={{
                display: "flex",
                justifyContent: "center",
                position: "relative",
              }}
            >
              <Box
                sx={{
                  position: "relative",
                  width: { xs: 320, lg: 450 },
                  height: { xs: 320, lg: 450 },
                }}
              >
                {/* Main Circle Image Placeholder */}
                <Box
                  sx={{
                    width: "100%",
                    height: "100%",
                    borderRadius: "50%",
                    overflow: "hidden",
                    border: "4px solid",
                    borderColor: "rgba(255,255,255,0.5)",
                    bgcolor: "primary.light",
                    position: "relative",
                    zIndex: 1,
                  }}
                >
                  <Image
                    alt="Financial Professional"
                    src="/images/illustration/neotool-landpage-cover.png"
                    fill
                    priority
                    sizes="(max-width: 1200px) 320px, 450px"
                    style={{ objectFit: "cover" }}
                  />
                </Box>

                {/* Floating Cards */}
                <Box
                  sx={{
                    position: "absolute",
                    inset: 0,
                    zIndex: 2,
                    pointerEvents: "none",
                  }}
                >
                  <GlassCard
                    sx={{ top: "10%", left: "5%", animationDelay: "0s" }}
                  >
                    <TrendingUpIcon sx={{ color: "#22c55e", fontSize: 32 }} />
                    <Typography
                      variant="caption"
                      sx={{
                        display: "block",
                        fontWeight: 700,
                        color: "#16a34a",
                        mt: 1,
                      }}
                    >
                      +25%
                    </Typography>
                  </GlassCard>

                  <GlassCard
                    sx={{ bottom: "20%", left: "-5%", animationDelay: "1s" }}
                  >
                    <HomeIcon sx={{ color: "#fb923c", fontSize: 32 }} />
                    <Typography
                      variant="caption"
                      sx={{
                        display: "block",
                        fontWeight: 700,
                        color: "text.secondary",
                        mt: 1,
                      }}
                    >
                      Imóveis
                    </Typography>
                  </GlassCard>

                  <GlassCard
                    sx={{ top: "-5%", right: "20%", animationDelay: "2s" }}
                  >
                    <PaidIcon sx={{ color: "#eab308", fontSize: 32 }} />
                    <Typography
                      variant="caption"
                      sx={{
                        display: "block",
                        fontWeight: 700,
                        color: "text.secondary",
                        mt: 1,
                      }}
                    >
                      Dividendos
                    </Typography>
                  </GlassCard>

                  <GlassCard
                    sx={{ top: "40%", right: "-10%", animationDelay: "3s" }}
                  >
                    <BarChartIcon
                      sx={{ color: "primary.main", fontSize: 32 }}
                    />
                  </GlassCard>

                  <GlassCard
                    sx={{ bottom: "5%", right: "10%", animationDelay: "4s" }}
                  >
                    <SavingsIcon sx={{ color: "#f472b6", fontSize: 32 }} />
                  </GlassCard>
                </Box>
              </Box>
            </Grid>
          </Grid>
        </Container>
      </GradientSection>

      {/* Features Section */}
      <Box sx={{ py: 12, bgcolor: "background.paper" }}>
        <Container maxWidth={false} sx={{ maxWidth: 1280, px: 3, mx: "auto" }}>
          <Box sx={{ mb: 6 }}>
            <Typography
              variant="h3"
              component="h2"
              sx={{ fontWeight: 700, mb: 1 }}
            >
              {t("features.title")}
            </Typography>
            <Typography
              variant="h6"
              sx={{ color: "text.secondary", fontWeight: 400 }}
            >
              {t("features.subtitle")}
            </Typography>
          </Box>

          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" },
              gap: 4,
            }}
          >
            {/* Institutions Card */}
            <NextLink
              href="/financial-data/bacen-institutions"
              style={{ textDecoration: "none", display: "block" }}
            >
              <FeatureCard
                bgColor={brandNavy}
                textColor="#fff"
                className="group"
              >
                <Box sx={{ position: "relative", zIndex: 1 }}>
                  <Chip
                    label={t("features.institutions.tag")}
                    size="small"
                    sx={{
                      bgcolor: "rgba(255,255,255,0.1)",
                      color: "#fff",
                      mb: 3,
                      backdropFilter: "blur(4px)",
                    }}
                  />
                  <Typography variant="h4" sx={{ fontWeight: 700, mb: 2 }}>
                    {t("features.institutions.title")}
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{
                      color: "rgba(183, 222, 246, 0.8)",
                      fontSize: "1.125rem",
                      lineHeight: 1.6,
                      mb: 4,
                    }}
                  >
                    {t("features.institutions.description")}
                  </Typography>
                  <Stack
                    direction="row"
                    spacing={1}
                    useFlexGap
                    sx={{ flexWrap: "wrap" }}
                  >
                    <Chip
                      label={t("features.institutions.tags.balance_results")}
                      variant="outlined"
                      sx={{
                        borderColor: "rgba(255,255,255,0.2)",
                        color: "#fff",
                      }}
                    />
                    <Chip
                      label={t("features.institutions.tags.basileia")}
                      variant="outlined"
                      sx={{
                        borderColor: "rgba(255,255,255,0.2)",
                        color: "#fff",
                      }}
                    />
                    <Chip
                      label={t("features.institutions.tags.net_profit")}
                      variant="outlined"
                      sx={{
                        borderColor: "rgba(255,255,255,0.2)",
                        color: "#fff",
                      }}
                    />
                    <Chip
                      label={t("features.institutions.tags.ratings")}
                      variant="outlined"
                      sx={{
                        borderColor: "rgba(255,255,255,0.2)",
                        color: "#fff",
                      }}
                    />
                  </Stack>
                </Box>

                <Box
                  sx={{
                    mt: 6,
                    display: "flex",
                    alignItems: "center",
                    color: brandSail,
                    fontWeight: 600,
                  }}
                >
                  {t("features.institutions.cta")} <EastIcon sx={{ ml: 1 }} />
                </Box>

                <Box
                  sx={{
                    position: "absolute",
                    right: -60,
                    bottom: -60,
                    opacity: 0.2,
                    transition: "all 0.7s",
                    ".group:hover &": { transform: "scale(1.1)", opacity: 0.3 },
                  }}
                >
                  <AccountBalanceIcon sx={{ fontSize: 300 }} />
                </Box>
              </FeatureCard>
            </NextLink>

            {/* Indicators Card */}
            <NextLink
              href="/financial-data/national-indicators"
              style={{ textDecoration: "none", display: "block" }}
            >
              <FeatureCard
                bgColor={brandSail}
                textColor={brandNavy}
                className="group"
              >
                <Box sx={{ position: "relative", zIndex: 1 }}>
                  <Chip
                    label={t("features.indicators.tag")}
                    size="small"
                    sx={{
                      bgcolor: "rgba(15, 34, 63, 0.1)",
                      color: brandNavy,
                      mb: 3,
                    }}
                  />
                  <Typography variant="h4" sx={{ fontWeight: 700, mb: 2 }}>
                    {t("features.indicators.title")}
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{
                      color: "rgba(15, 34, 63, 0.7)",
                      fontSize: "1.125rem",
                      lineHeight: 1.6,
                      mb: 4,
                    }}
                  >
                    {t("features.indicators.description")}
                  </Typography>
                  <Stack direction="row" spacing={2} flexWrap="wrap" gap={1}>
                    <Chip
                      label={t("features.indicators.tags.ipca")}
                      sx={{
                        bgcolor: "rgba(255,255,255,0.5)",
                        color: brandNavy,
                        fontWeight: 700,
                        border: "1px solid rgba(255,255,255,0.5)",
                      }}
                    />
                    <Chip
                      label={t("features.indicators.tags.selic")}
                      sx={{
                        bgcolor: "rgba(255,255,255,0.5)",
                        color: brandNavy,
                        fontWeight: 700,
                        border: "1px solid rgba(255,255,255,0.5)",
                      }}
                    />
                    <Chip
                      label={t("features.indicators.tags.pib")}
                      sx={{
                        bgcolor: "rgba(255,255,255,0.5)",
                        color: brandNavy,
                        fontWeight: 700,
                        border: "1px solid rgba(255,255,255,0.5)",
                      }}
                    />
                  </Stack>
                </Box>

                <Box
                  sx={{
                    mt: 6,
                    display: "flex",
                    alignItems: "center",
                    fontWeight: 700,
                  }}
                >
                  {t("features.indicators.cta")} <EastIcon sx={{ ml: 1 }} />
                </Box>

                <Box
                  sx={{
                    position: "absolute",
                    right: -60,
                    bottom: -60,
                    opacity: 0.2,
                    transition: "all 0.7s",
                    ".group:hover &": { transform: "rotate(12deg)" },
                  }}
                >
                  <ShowChartIcon sx={{ fontSize: 300 }} />
                </Box>
              </FeatureCard>
            </NextLink>
          </Box>
        </Container>
      </Box>

      {/* Footer */}
      <Box
        component="footer"
        sx={{ py: 4, borderTop: 1, borderColor: "divider" }}
      >
        <Container
          maxWidth="lg"
          sx={{
            display: "flex",
            alignItems: "center",
            flexDirection: { xs: "column", md: "row" },
            gap: { xs: 4, md: 2 },
          }}
        >
          <Box
            sx={{
              flex: { md: 1 },
              display: "flex",
              justifyContent: { xs: "center", md: "flex-start" },
              width: { xs: "100%", md: "auto" },
            }}
          >
            <Logo size="small" />
          </Box>

          <Stack direction="column" spacing={1} alignItems="center">
            <Link
              href="mailto:contato@neotool.com.br"
              color="text.secondary"
              underline="hover"
              sx={{ fontSize: "0.875rem" }}
            >
              contato@neotool.com.br
            </Link>
            <Link
              href="https://www.linkedin.com/company/neotool"
              target="_blank"
              rel="noopener noreferrer"
              color="text.secondary"
              underline="none"
              sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
            >
              <LinkedInIcon fontSize="small" />
              <Typography variant="body2" fontWeight="bold">
                LinkedIn
              </Typography>
            </Link>
          </Stack>

          <Box
            sx={{
              flex: { md: 1 },
              display: "flex",
              justifyContent: { xs: "center", md: "flex-end" },
              width: { xs: "100%", md: "auto" },
            }}
          >
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ textAlign: { xs: "center", md: "right" } }}
            >
              {t("footer.copyright")}
            </Typography>
          </Box>
        </Container>
      </Box>
    </Box>
  );
}
