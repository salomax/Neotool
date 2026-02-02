"use client";

import * as React from "react";
import { Box, Typography, IconButton, Stack, Tooltip } from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import Card from "@/shared/components/ui/primitives/Card";
import { useTranslation, i18n } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { InstitutionRating } from "@/lib/graphql/types/__generated__/graphql";
import { formatDate } from "@/shared/utils/date";

export interface RatingCardProps {
  ratings: InstitutionRating[];
  loading?: boolean;
}

export function RatingCard({ ratings, loading }: RatingCardProps) {
  const { t } = useTranslation(financialDataTranslations);
  const [currentIndex, setCurrentIndex] = React.useState(0);

  // Filter and sort ratings if needed. 
  // Requirement: default will be issuer.code=FITCH and rating.name=NATIONAL_LONG_TERM_RATING
  // We should probably sort so that the default one comes first, or just let the carousel handle it.
  // For now, we'll just use the provided array, but maybe sort to ensure consistency.
  // Actually, let's find the "default" one and make it the initial index.
  
  const sortedRatings = React.useMemo(() => {
    if (!ratings || ratings.length === 0) return [];
    
    const defaultRatingIndex = ratings.findIndex(
      r => r.issuer?.code === 'FITCH' && r.name === 'NATIONAL_LONG_TERM_RATING'
    );

    if (defaultRatingIndex > 0) {
      const newRatings = [...ratings];
      const defaultRating = newRatings[defaultRatingIndex];
      if (defaultRating) {
        newRatings.splice(defaultRatingIndex, 1);
        newRatings.unshift(defaultRating);
        return newRatings;
      }
    }

    return ratings;
  }, [ratings]);

  const currentRating = sortedRatings[currentIndex];

  const handleNext = () => {
    setCurrentIndex((prev) => (prev + 1) % sortedRatings.length);
  };

  const handlePrev = () => {
    setCurrentIndex((prev) => (prev - 1 + sortedRatings.length) % sortedRatings.length);
  };

  if (loading) {
    // Basic Skeleton could be returned here
    return (
      <Card sx={{ height: 260, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Typography color="text.secondary">Loading...</Typography>
      </Card>
    );
  }

  if (!sortedRatings.length || !currentRating) {
    return null; 
  }

  const locale = i18n?.language || 'pt-BR';
  const ratingDate = currentRating.ratingDate 
    ? formatDate(currentRating.ratingDate as string, { locale }) 
    : '-';

  // Translation keys
  // issuer: ratings.issuer.{code} -> e.g. ratings.issuer.FITCH
  // name: ratings.type.{name} -> e.g. ratings.type.NATIONAL_LONG_TERM_RATING
  
  const issuerName = currentRating.issuer?.code 
    ? t(`ratings.issuer.${currentRating.issuer.code}`, { defaultValue: currentRating.issuer.name })
    : currentRating.issuer?.name;

  const ratingName = currentRating.name
    ? t(`ratings.type.${currentRating.name}`, { defaultValue: currentRating.name.replace(/_/g, ' ') })
    : '';

  return (
    <Card
      variant="outlined"
      sx={{
        p: 2,
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        position: "relative",
      }}
    >
      {/* Header: Issuer Name and Navigation */}
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2} sx={{ mb: 1 }}>
        <Typography 
          variant="overline" 
          sx={{ 
            fontWeight: 700, 
            letterSpacing: '0.1em',
            color: 'text.primary',
            fontSize: '0.75rem',
            lineHeight: 1.5, // Better alignment with buttons
            mt: 0.5 // Slight adjustment for visual alignment
          }}
        >
          {issuerName?.toUpperCase()}
        </Typography>

        {/* Navigation Arrows */}
        {sortedRatings.length > 1 && (
          <Stack direction="row" spacing={0.5}>
            <IconButton 
              onClick={handlePrev} 
              size="small"
              aria-label="Previous rating"
            >
              <ChevronLeftIcon fontSize="small" />
            </IconButton>
            <IconButton 
              onClick={handleNext} 
              size="small"
              aria-label="Next rating"
            >
              <ChevronRightIcon fontSize="small" />
            </IconButton>
          </Stack>
        )}
      </Stack>

      {/* Body: Rating Content */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
        <Tooltip title={ratingName} placement="top" arrow>
          <Typography 
            variant="caption" 
            sx={{ 
              color: 'text.secondary', 
              fontWeight: 600, 
              textTransform: 'uppercase',
              mb: 0.5,
              display: '-webkit-box',
              overflow: 'hidden',
              WebkitBoxOrient: 'vertical',
              WebkitLineClamp: 1,
              textAlign: 'left'
            }}
          >
            {ratingName}
          </Typography>
        </Tooltip>
        
        <Typography 
          variant="h2" 
          sx={{ 
            fontWeight: 700, 
            fontSize: '1.8rem',
            lineHeight: 1.2,
            textAlign: 'left'
          }}
        >
          {currentRating.label}
        </Typography>
      </Box>

      {/* Footer: Date and Dots */}
      <Box sx={{ mt: 1 }}>
        <Stack direction="row" spacing={0.5} alignItems="center">
            <Typography 
            variant="caption" 
            >
            {t('ratings.lastUpdate')}
            </Typography>
            <Typography variant="caption" fontWeight="500" sx={{ textAlign: 'left' }}>
            {ratingDate}
            </Typography>
        </Stack>

        {/* Dots Indicator */}
        {sortedRatings.length > 1 && (
          <Stack direction="row" spacing={1} justifyContent="flex-start" sx={{ mt: 1.5 }}>
            {sortedRatings.map((_, index) => (
              <Box
                key={index}
                sx={{
                  width: index === currentIndex ? 24 : 24,
                  height: 4,
                  borderRadius: 2,
                  bgcolor: index === currentIndex ? 'primary.main' : 'action.disabled',
                  transition: 'all 0.3s ease',
                  opacity: index === currentIndex ? 1 : 0.3
                }}
              />
            ))}
          </Stack>
        )}
      </Box>
    </Card>
  );
}
