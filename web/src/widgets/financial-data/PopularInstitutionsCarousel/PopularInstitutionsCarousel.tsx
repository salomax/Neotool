"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { 
  Box, 
  Typography, 
  Stack, 
  Avatar, 
  useTheme, 
  useMediaQuery,
  Skeleton,
  IconButton
} from "@mui/material";
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import Card from "@/shared/components/ui/primitives/Card";
import { PopularInstitution } from "@/lib/graphql/types/__generated__/graphql";
import { createSemanticPath } from "@/shared/utils/url";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";

export interface PopularInstitutionsCarouselProps {
  items: PopularInstitution[];
  loading?: boolean;
}

export function PopularInstitutionsCarousel({ items, loading }: PopularInstitutionsCarouselProps) {
  const router = useRouter();
  const theme = useTheme();
  const { t } = useTranslation(financialDataTranslations);
  const scrollContainerRef = React.useRef<HTMLDivElement>(null);
  
  const handleItemClick = (item: PopularInstitution) => {
    const institution = item.institution;
    router.push(`/financial-data/bacen-institutions/${createSemanticPath(institution.name, institution.id)}`);
  };

  // Create a looped version of items for infinite scrolling
  const [loopedItems, setLoopedItems] = React.useState<PopularInstitution[]>([]);
  
  React.useEffect(() => {
    if (items.length > 0) {
      // Triple the items to ensure smooth infinite scrolling in both directions
      setLoopedItems([...items, ...items, ...items]);
    }
  }, [items]);

  // Initialize scroll position to the middle set
  React.useEffect(() => {
    if (scrollContainerRef.current && items.length > 0) {
      const container = scrollContainerRef.current;
      // Wait for layout to settle
      setTimeout(() => {
        const cardWidth = container.querySelector('.MuiCard-root')?.clientWidth || 0;
        const gap = 16;
        const totalSetWidth = (cardWidth + gap) * items.length;
        
        // Scroll to the start of the middle set
        container.scrollLeft = totalSetWidth;
      }, 100);
    }
  }, [items.length]);

  const handleScroll = (direction: 'left' | 'right') => {
    if (scrollContainerRef.current) {
      const container = scrollContainerRef.current;
      const cardWidth = container.querySelector('.MuiCard-root')?.clientWidth || 0;
      const gap = 16;
      const scrollAmount = cardWidth + gap;
      const totalSetWidth = scrollAmount * items.length;
      
      const currentScroll = container.scrollLeft;
      
      // Calculate target scroll position
      let targetScroll;
      if (direction === 'left') {
        targetScroll = currentScroll - scrollAmount;
      } else {
        targetScroll = currentScroll + scrollAmount;
      }
      
      // Smooth scroll to target
      container.scrollTo({
        left: targetScroll,
        behavior: 'smooth'
      });

      // Handle infinite loop logic
      // We check after the animation completes (approx 300ms)
      setTimeout(() => {
        const newScroll = container.scrollLeft;
        
        // If we've scrolled too far left (into the first set), jump forward to middle set
        if (newScroll < totalSetWidth - scrollAmount) {
          container.scrollTo({
            left: newScroll + totalSetWidth,
            behavior: 'auto' // Instant jump
          });
        }
        // If we've scrolled too far right (into the third set), jump back to middle set
        else if (newScroll >= totalSetWidth * 2) {
          container.scrollTo({
            left: newScroll - totalSetWidth,
            behavior: 'auto' // Instant jump
          });
        }
      }, 350);
    }
  };

  if (loading) {
    return (
      <Box sx={{ overflowX: "auto", display: "flex", gap: 2, pb: 1, px: 0.5 }}>
        {[1, 2, 3, 4, 5].map((i) => (
          <Skeleton key={i} variant="rectangular" width={220} height={160} sx={{ borderRadius: 2, flexShrink: 0 }} />
        ))}
      </Box>
    );
  }

  if (!items.length) {
    return null;
  }

  // Use loopedItems instead of items for rendering
  const itemsToRender = loopedItems.length > 0 ? loopedItems : items;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1, px: 2 }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: "block" }}>
          {t("discovery.mostPopular")}
        </Typography>
        <Stack direction="row" spacing={1}>
          <IconButton 
            size="small" 
            onClick={() => handleScroll('left')}
            sx={{ border: 1, borderColor: 'divider' }}
          >
            <ChevronLeftIcon fontSize="small" />
          </IconButton>
          <IconButton 
            size="small" 
            onClick={() => handleScroll('right')}
            sx={{ border: 1, borderColor: 'divider' }}
          >
            <ChevronRightIcon fontSize="small" />
          </IconButton>
        </Stack>
      </Box>
      
      <Box 
        ref={scrollContainerRef}
        sx={{ 
          display: "flex", 
          gap: 2, 
          overflowX: "auto", 
          overflowY: "hidden",
          pb: 2,
          // Removed negative margins to prevent cutting
          scrollSnapType: "x mandatory",
          scrollbarWidth: "none",
          "&::-webkit-scrollbar": {
            display: "none",
          },
        }}
      >
        {itemsToRender.map((item, index) => (
          <Card 
            key={`${item.rank}-${index}`} // Use index to ensure unique keys for duplicated items
            className="MuiCard-root"
            variant="outlined"
            hoverable
            onClick={() => handleItemClick(item)}
            sx={{ 
              minWidth: 0,
              width: { 
                xs: "100%", 
                sm: "calc((100% - 16px) / 2)", 
                md: "calc((100% - 32px) / 3)", 
                lg: "calc((100% - 48px) / 4)" 
              },
              flexShrink: 0,
              scrollSnapAlign: "start",
              p: 2.5,
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              position: "relative",
              overflow: "hidden",
              cursor: "pointer",
              boxShadow: "none",
              border: "1px solid",
              borderColor: "divider",
              "&:hover": {
                boxShadow: "0px 1px 4px rgba(0, 0, 0, 0.1)",
              }
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar 
                src={item.institution.logoAsset?.publicUrl || undefined}
                alt={item.institution.tradeName || item.institution.name}
                sx={{ 
                  width: 40, 
                  height: 40,
                  borderRadius: 1,
                  bgcolor: "background.default",
                  border: "1px solid",
                  borderColor: "transparent"
                }}
              >
                {(item.institution.tradeName || item.institution.name).charAt(0)}
              </Avatar>

              <Typography 
                variant="subtitle1" 
                sx={{ 
                  textTransform: 'uppercase',
                  fontWeight: 500, 
                  lineHeight: 1.2,
                  display: '-webkit-box', 
                  WebkitLineClamp: 2, 
                  WebkitBoxOrient: 'vertical', 
                  overflow: 'hidden' 
                }}
              >
                {item.institution.tradeName || item.institution.name}
              </Typography>
            </Box>
          </Card>
        ))}
      </Box>
    </Box>
  );
}
