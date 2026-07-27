"use client";

import EastIcon from "@mui/icons-material/East";
import NextLink from "next/link";
import { alpha, Box, Button, Chip, Link, Stack, Typography, useTheme } from "@/shared/ui/mui-imports";
import type { LandingFeatureSection } from "./featureCatalog";
import { LandingFeaturePlaceholder } from "./LandingFeaturePlaceholder";
import { useScrollReveal } from "./useScrollReveal";

interface FeatureHighlightSectionProps {
  section: LandingFeatureSection;
}

export function FeatureHighlightSection({ section }: FeatureHighlightSectionProps) {
  const theme = useTheme();
  const { targetRef, isVisible } = useScrollReveal<HTMLDivElement>();

  return (
    <Box
      ref={targetRef}
      data-testid={`${section.id}-section`}
      data-revealed={isVisible ? "true" : "false"}
      sx={{
        borderRadius: 8,
        border: 1,
        borderColor: "divider",
        backgroundColor: "background.default",
        boxShadow: theme.custom.shadow(1),
        overflow: "hidden",
        opacity: isVisible ? 1 : 0,
        transform: isVisible ? "translateY(0px)" : "translateY(32px)",
        transition: "opacity 500ms ease, transform 500ms ease",
      }}
    >
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", lg: "1.05fr 0.95fr" },
          gap: { xs: 4, lg: 6 },
          p: { xs: 3, md: 4, lg: 5 },
          alignItems: "center",
        }}
      >
        <Stack spacing={3}>
          <Chip
            label={section.eyebrow}
            size="small"
            sx={{
              width: "fit-content",
              color: "text.primary",
              bgcolor: alpha(theme.palette.primary.main, 0.08),
            }}
          />

          <Box>
            <Typography variant="h3" component="h3" sx={{ mb: 2 }}>
              {section.title}
            </Typography>
            <Typography variant="h6" sx={{ color: "text.secondary", fontWeight: 400, lineHeight: 1.7 }}>
              {section.description}
            </Typography>
          </Box>

          {section.items && section.items.length > 0 ? (
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                gap: 2,
              }}
            >
              {section.items.map((item) => (
                <Box
                  key={item.title}
                  sx={{
                    p: 2.5,
                    borderRadius: 4,
                    border: 1,
                    borderColor: "divider",
                    bgcolor: alpha(theme.palette.background.paper, 0.82),
                  }}
                >
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
                    {item.title}
                  </Typography>
                  <Typography variant="body1" sx={{ color: "text.secondary", lineHeight: 1.6 }}>
                    {item.description}
                  </Typography>
                </Box>
              ))}
            </Box>
          ) : null}

          <Link
            component={NextLink}
            href={section.href}
            underline="none"
            sx={{ width: "fit-content" }}
            data-testid={`${section.id}-cta`}
          >
            <Button
              endIcon={<EastIcon />}
              sx={{
                px: 3,
                bgcolor: "text.primary",
                color: "background.paper",
                "&:hover": {
                  bgcolor: "text.primary",
                },
              }}
            >
              {section.cta}
            </Button>
          </Link>
        </Stack>

        <Box data-testid={`${section.id}-visual`}>
          <LandingFeaturePlaceholder
            accent={section.accent}
            icon={section.icon}
            title={section.title}
            highlights={section.highlights}
          />
        </Box>
      </Box>
    </Box>
  );
}
