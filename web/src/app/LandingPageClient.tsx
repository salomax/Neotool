"use client";

import { Box, Container, Link, Stack, Typography } from "@/shared/ui/mui-imports";
import { Logo } from "@/shared/ui/brand";
import LinkedInIcon from "@mui/icons-material/LinkedIn";
import { useTranslation } from "@/shared/i18n";
import { landingTranslations } from "./i18n";
import { LandingHero } from "./landing/LandingHero";
import { LANDING_PAGE_CONTENT_SX } from "./landing/landingPageLayout";
import { buildLandingFeatureCatalog } from "./landing/featureCatalog";
import { FeatureHighlightSection } from "./landing/FeatureHighlightSection";
import { FeatureSplitSection } from "./landing/FeatureSplitSection";

export default function WelcomePage() {
  const { t } = useTranslation(landingTranslations);
  const catalog = buildLandingFeatureCatalog(t);

  return (
    <Box
      sx={{
        bgcolor: "background.default",
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        overflowX: "hidden",
      }}
    >
      <LandingHero />

      <Box sx={{ py: { xs: 8, md: 12 }, bgcolor: "background.paper" }}>
        <Container maxWidth={false} sx={LANDING_PAGE_CONTENT_SX}>
          <Box sx={{ mb: { xs: 6, md: 8 }, maxWidth: 760 }}>
            <Typography variant="h3" component="h2" sx={{ fontWeight: 700, mb: 2 }}>
              {catalog.header.title}
            </Typography>
            <Typography variant="h6" sx={{ color: "text.secondary", fontWeight: 400, lineHeight: 1.6 }}>
              {catalog.header.subtitle}
            </Typography>
          </Box>

          <Stack spacing={{ xs: 6, md: 8 }}>
            {catalog.sections.map((section) =>
              section.layout === "highlight" ? (
                <FeatureHighlightSection key={section.id} section={section} />
              ) : (
                <FeatureSplitSection key={section.id} section={section} />
              ),
            )}
          </Stack>
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
