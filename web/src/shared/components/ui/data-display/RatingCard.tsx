"use client";

import * as React from "react";
import { Box, Typography, IconButton, Stack, Link } from "@mui/material";
import { useTheme } from "@mui/material/styles";
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
  const theme = useTheme();
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
        p: 3,
        height: "100%",
        minHeight: 260,
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        position: "relative",
      }}
    >
      {/* Header: Issuer Name */}
      <Box sx={{ textAlign: 'center', mb: 2 }}>
        <Typography 
          variant="overline" 
          sx={{ 
            fontWeight: 700, 
            letterSpacing: '0.1em',
            color: 'text.primary',
            fontSize: '0.75rem'
          }}
        >
          {issuerName?.toUpperCase()}
        </Typography>
      </Box>

      {/* Body: Rating Content with Arrows */}
      <Box sx={{ display: 'flex', alignItems: 'center', flex: 1 }}>
        
        {/* Left Arrow */}
        <IconButton 
          onClick={handlePrev} 
          disabled={sortedRatings.length <= 1}
          sx={{ visibility: sortedRatings.length <= 1 ? 'hidden' : 'visible' }}
        >
          <ChevronLeftIcon />
        </IconButton>

        {/* Rating Info */}
        <Box sx={{ flex: 1, textAlign: 'center' }}>
          <Typography 
            variant="caption" 
            sx={{ 
              color: 'text.secondary', 
              fontWeight: 600, 
              textTransform: 'uppercase',
              mb: 1,
              display: 'block'
            }}
          >
            {ratingName}
          </Typography>
          
          <Typography 
            variant="h2" 
            sx={{ 
              fontWeight: 700, 
              color: 'primary.main',
              fontSize: '2.5rem',
              lineHeight: 1.2
            }}
          >
            {currentRating.label}
          </Typography>
        </Box>

        {/* Right Arrow */}
        <IconButton 
          onClick={handleNext} 
          disabled={sortedRatings.length <= 1}
          sx={{ visibility: sortedRatings.length <= 1 ? 'hidden' : 'visible' }}
        >
          <ChevronRightIcon />
        </IconButton>
      </Box>

      {/* Footer: Date and Dots */}
      <Box sx={{ textAlign: 'center', mt: 2 }}>
        <Typography 
          variant="caption" 
          sx={{ 
            color: 'text.secondary', 
            display: 'block', 
            mb: 0.5,
            fontSize: '0.7rem',
            fontWeight: 600,
            textTransform: 'uppercase'
          }}
        >
          {t('ratings.lastUpdate')}
        </Typography>
        <Typography variant="body2" fontWeight="500">
          {ratingDate}
        </Typography>

        {/* Dots Indicator */}
        {sortedRatings.length > 1 && (
          <Stack direction="row" spacing={1} justifyContent="center" sx={{ mt: 2 }}>
            {sortedRatings.map((_, index) => (
              <Box
                key={index}
                sx={{
                  width: index === currentIndex ? 24 : 24, // Active is longer line? Image shows line
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
