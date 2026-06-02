"use client";

import {
  Box,
  Typography,
  Container,
  Stack,
  styled,
  Link,
  Chip,
} from "@/shared/ui/mui-imports";
import { Theme } from "@mui/material/styles";
import { Logo } from "@/shared/ui/brand";
import { useTranslation } from "@/shared/i18n";
import { landingTranslations } from "./i18n";
import { LandingHero } from "./landing/LandingHero";
import { LANDING_PAGE_CONTENT_SX } from "./landing/landingPageLayout";
import NextLink from "next/link";
import LinkedInIcon from "@mui/icons-material/LinkedIn";
import EastIcon from "@mui/icons-material/East";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import ShowChartIcon from "@mui/icons-material/ShowChart";
import CompareArrowsIcon from "@mui/icons-material/CompareArrows";

const FeatureCard = styled(Box, {
  shouldForwardProp: (prop: string) => prop !== "bgColor" && prop !== "textColor",
})<{ bgColor?: string; textColor?: string }>(
  ({ theme, bgColor, textColor }: { theme: Theme; bgColor?: string; textColor?: string }) => ({
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

  const brandNavy = "#0F223F";
  const brandSail = "#B7DEF6";
  const brandGreen = "#064e3b";

  return (
    <Box sx={{ bgcolor: "background.default", minHeight: "100vh", display: "flex", flexDirection: "column" }}>
      <LandingHero />

      {/* Features Section */}
      <Box sx={{ py: 12, bgcolor: "background.paper" }}>
        <Container maxWidth={false} sx={LANDING_PAGE_CONTENT_SX}>
          <Box sx={{ mb: 6 }}>
            <Typography variant="h3" component="h2" sx={{ fontWeight: 700, mb: 1 }}>
              {t("features.title")}
            </Typography>
            <Typography variant="h6" sx={{ color: "text.secondary", fontWeight: 400 }}>
              {t("features.subtitle")}
            </Typography>
          </Box>

          <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr 1fr 1fr" }, gap: 4 }}>
            <NextLink href="/financial-data/bacen-institutions" style={{ textDecoration: "none", display: "block" }}>
              <FeatureCard bgColor={brandNavy} textColor="#fff" className="group">
                <Box sx={{ position: "relative", zIndex: 1 }}>
                  <Chip
                    label={t("features.institutions.tag")}
                    size="small"
                    sx={{ bgcolor: "rgba(255,255,255,0.1)", color: "#fff", mb: 3, backdropFilter: "blur(4px)" }}
                  />
                  <Typography variant="h4" sx={{ fontWeight: 700, mb: 2 }}>
                    {t("features.institutions.title")}
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{ color: "rgba(183, 222, 246, 0.8)", fontSize: "1.125rem", lineHeight: 1.6, mb: 4 }}
                  >
                    {t("features.institutions.description")}
                  </Typography>
                  <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                    <Chip
                      label={t("features.institutions.tags.balance_results")}
                      variant="outlined"
                      sx={{ borderColor: "rgba(255,255,255,0.2)", color: "#fff" }}
                    />
                    <Chip
                      label={t("features.institutions.tags.basileia")}
                      variant="outlined"
                      sx={{ borderColor: "rgba(255,255,255,0.2)", color: "#fff" }}
                    />
                    <Chip
                      label={t("features.institutions.tags.net_profit")}
                      variant="outlined"
                      sx={{ borderColor: "rgba(255,255,255,0.2)", color: "#fff" }}
                    />
                    <Chip
                      label={t("features.institutions.tags.ratings")}
                      variant="outlined"
                      sx={{ borderColor: "rgba(255,255,255,0.2)", color: "#fff" }}
                    />
                  </Stack>
                </Box>

                <Box sx={{ mt: 6, display: "flex", alignItems: "center", color: brandSail, fontWeight: 600 }}>
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

            <NextLink href="/financial-data/national-indicators" style={{ textDecoration: "none", display: "block" }}>
              <FeatureCard bgColor={brandSail} textColor={brandNavy} className="group">
                <Box sx={{ position: "relative", zIndex: 1 }}>
                  <Chip
                    label={t("features.indicators.tag")}
                    size="small"
                    sx={{ bgcolor: "rgba(15, 34, 63, 0.1)", color: brandNavy, mb: 3 }}
                  />
                  <Typography variant="h4" sx={{ fontWeight: 700, mb: 2 }}>
                    {t("features.indicators.title")}
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{ color: "rgba(15, 34, 63, 0.7)", fontSize: "1.125rem", lineHeight: 1.6, mb: 4 }}
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

                <Box sx={{ mt: 6, display: "flex", alignItems: "center", fontWeight: 700 }}>
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

            <NextLink
              href="/investments/fixed-income-comparison"
              style={{ textDecoration: "none", display: "block", height: "100%" }}
            >
              <FeatureCard bgColor={brandGreen} textColor="#fff" className="group">
                <Box sx={{ position: "relative", zIndex: 1 }}>
                  <Chip
                    label={t("features.fixed_income_comparison.tag")}
                    size="small"
                    sx={{ bgcolor: "rgba(255,255,255,0.1)", color: "#fff", mb: 3, backdropFilter: "blur(4px)" }}
                  />
                  <Typography variant="h4" sx={{ fontWeight: 700, mb: 2 }}>
                    {t("features.fixed_income_comparison.title")}
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{ color: "rgba(255, 255, 255, 0.8)", fontSize: "1.125rem", lineHeight: 1.6, mb: 4 }}
                  >
                    {t("features.fixed_income_comparison.description")}
                  </Typography>
                  <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                    <Chip
                      label={t("features.fixed_income_comparison.tags.cdb")}
                      variant="outlined"
                      sx={{ borderColor: "rgba(255,255,255,0.2)", color: "#fff" }}
                    />
                    <Chip
                      label={t("features.fixed_income_comparison.tags.lci")}
                      variant="outlined"
                      sx={{ borderColor: "rgba(255,255,255,0.2)", color: "#fff" }}
                    />
                    <Chip
                      label={t("features.fixed_income_comparison.tags.lca")}
                      variant="outlined"
                      sx={{ borderColor: "rgba(255,255,255,0.2)", color: "#fff" }}
                    />
                  </Stack>
                </Box>

                <Box sx={{ mt: 6, display: "flex", alignItems: "center", color: "#6ee7b7", fontWeight: 600 }}>
                  {t("features.fixed_income_comparison.cta")} <EastIcon sx={{ ml: 1 }} />
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
                  <CompareArrowsIcon sx={{ fontSize: 300 }} />
                </Box>
              </FeatureCard>
            </NextLink>
          </Box>
        </Container>
      </Box>

      <Box component="footer" sx={{ py: 4, borderTop: 1, borderColor: "divider" }}>
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
              href="mailto:contato@invistus.com.br"
              color="text.secondary"
              underline="hover"
              sx={{ fontSize: "0.875rem" }}
            >
              contato@invistus.com.br
            </Link>
            <Link
              href="https://www.linkedin.com/company/invistus"
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
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: { xs: "center", md: "right" } }}>
              {t("footer.copyright")}
            </Typography>
          </Box>
        </Container>
      </Box>
    </Box>
  );
}
