"use client";

import EastIcon from "@mui/icons-material/East";
import NextLink from "next/link";
import { alpha, Box, Button, Chip, Link, Stack, Typography, useTheme } from "@/shared/ui/mui-imports";
import type { LandingFeatureSection } from "./featureCatalog";
import { LandingFeaturePlaceholder } from "./LandingFeaturePlaceholder";
import { LANDING_PAGE_CONTENT_SX } from "./landingPageLayout";
import { useScrollReveal } from "./useScrollReveal";

interface FeatureSplitSectionProps {
  section: LandingFeatureSection;
}

export function FeatureSplitSection({ section }: FeatureSplitSectionProps) {
  const theme = useTheme();
  const { targetRef, isVisible } = useScrollReveal<HTMLDivElement>();
  const isImageFirst = section.imageSide === "left";

  const media = (
    <Box data-testid={`${section.id}-media`}>
      <LandingFeaturePlaceholder
        accent={section.accent}
        icon={section.icon}
        title={section.title}
        highlights={section.highlights}
      />
    </Box>
  );

  const content = (
    <Stack data-testid={`${section.id}-content`} spacing={3} sx={{ minWidth: 0 }}>
      <Chip
        label={section.eyebrow}
        size="small"
        sx={{
          width: "fit-content",
          color: "text.primary",
          bgcolor: alpha(theme.palette.info.main, 0.08),
        }}
      />

      <Box>
        <Typography variant="h4" component="h3" sx={{ mb: 2, fontWeight: 700 }}>
          {section.title}
        </Typography>
        <Typography variant="body1" sx={{ color: "text.secondary", lineHeight: 1.8, fontSize: "1rem" }}>
          {section.description}
        </Typography>
      </Box>

      {section.items && section.items.length > 0 ? (
        <Stack spacing={1.5}>
          {section.items.map((item) => (
            <Box key={item.title}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>
                {item.title}
              </Typography>
              <Typography variant="body2" sx={{ color: "text.secondary", lineHeight: 1.7 }}>
                {item.description}
              </Typography>
            </Box>
          ))}
        </Stack>
      ) : (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
          {section.highlights.map((highlight) => (
            <Chip
              key={highlight}
              label={highlight}
              variant="outlined"
              sx={{
                borderColor: "divider",
                bgcolor: "background.paper",
              }}
            />
          ))}
        </Stack>
      )}

      <Link
        component={NextLink}
        href={section.href}
        underline="none"
        sx={{ width: "fit-content" }}
        data-testid={`${section.id}-cta`}
      >
        <Button variant="outlined" endIcon={<EastIcon />}>
          {section.cta}
        </Button>
      </Link>
    </Stack>
  );

  return (
    <Box
      ref={targetRef}
      data-testid={`${section.id}-section`}
      data-revealed={isVisible ? "true" : "false"}
      sx={{
        ...LANDING_PAGE_CONTENT_SX,
        px: { xs: 0, md: LANDING_PAGE_CONTENT_SX.px },
        borderRadius: 8,
        opacity: isVisible ? 1 : 0,
        transform: isVisible ? "translateY(0px)" : "translateY(32px)",
        transition: "opacity 500ms ease, transform 500ms ease",
      }}
    >
      <Box
        data-testid={`${section.id}-layout`}
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", lg: "minmax(0, 1fr) minmax(0, 1fr)" },
          gap: { xs: 4, lg: 6 },
          alignItems: "center",
        }}
      >
        {isImageFirst ? media : content}
        {isImageFirst ? content : media}
      </Box>
    </Box>
  );
}
