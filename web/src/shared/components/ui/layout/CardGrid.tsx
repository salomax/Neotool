"use client";

import * as React from "react";
import { Grid } from "./Grid";
import { Button } from "@mui/material";
import { Stack } from "@mui/material";
import { Box } from "@mui/material";
import { EmptyState, ErrorState } from "@/shared/components/ui/feedback/states/EmptyErrorState";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import type { ResponsiveValue } from "./types";
import { useTranslation } from "react-i18next";

export interface CardGridProps<T> {
  /**
   * Array of items to display
   */
  items: T[];
  /**
   * Function to render each item as a card
   */
  renderItem: (item: T, index: number) => React.ReactNode;
  /**
   * Number of columns or responsive column configuration
   * @default { xs: 1, sm: 2, md: 3, lg: 4 }
   */
  columns?: number | { xs?: number; sm?: number; md?: number; lg?: number; xl?: number };
  /**
   * Gap between grid items
   * @default 2
   */
  gap?: number;
  /**
   * Callback when user scrolls near bottom or clicks Load More
   */
  onLoadMore?: () => void;
  /**
   * Whether there are more pages to load
   */
  hasNextPage?: boolean;
  /**
   * Loading state
   */
  loading?: boolean;
  /**
   * Error state
   */
  error?: Error | null;
  /**
   * Custom loading component (skeleton cards)
   */
  loadingComponent?: React.ReactNode;
  /**
   * Custom empty state component
   */
  emptyComponent?: React.ReactNode;
  /**
   * Custom error state component
   */
  errorComponent?: React.ReactNode;
  /**
   * Empty state message
   * @default "No results found"
   */
  emptyMessage?: string;
  /**
   * Error state message
   */
  errorMessage?: string;
  /**
   * Retry handler for error state
   */
  onRetry?: () => void;
  /**
   * Scroll threshold for infinite scroll (0-1, where 0.8 = 80%)
   * @default 0.8
   */
  scrollThreshold?: number;
  /**
   * Number of skeleton cards to show while loading
   * @default 6
   */
  skeletonCount?: number;
}

/**
 * CardGrid component with infinite scroll support.
 * 
 * Features:
 * - Responsive grid layout
 * - Intersection Observer for automatic infinite scroll
 * - Load More button as fallback
 * - Empty state, error state, and loading states
 * 
 * @example
 * ```tsx
 * <CardGrid
 *   items={institutions}
 *   renderItem={(item) => <BacenInstitutionCard institution={item} />}
 *   onLoadMore={loadNextPage}
 *   hasNextPage={hasNextPage}
 *   loading={loading}
 *   error={error}
 * />
 * ```
 */
export function CardGrid<T>({
  items,
  renderItem,
  columns = { xs: 1, sm: 2, md: 3, lg: 4 },
  gap = 2,
  onLoadMore,
  hasNextPage = false,
  loading = false,
  error = null,
  loadingComponent,
  emptyComponent,
  errorComponent,
  emptyMessage,
  errorMessage,
  onRetry,
  scrollThreshold = 0.8,
  skeletonCount = 6,
}: CardGridProps<T>) {
  const { t } = useTranslation("common");
  const sentinelRef = React.useRef<HTMLDivElement>(null);
  const [showLoadMore, setShowLoadMore] = React.useState(false);
  
  // Use provided emptyMessage or fallback to translation
  const displayEmptyMessage = emptyMessage || t("cardGrid.noResultsFound");

  // Convert columns to responsive format if needed
  const responsiveColumns: ResponsiveValue<number> = React.useMemo(() => {
    if (typeof columns === "number") {
      return columns;
    }
    return columns;
  }, [columns]);

  // Intersection Observer for infinite scroll
  React.useEffect(() => {
    if (!onLoadMore || !hasNextPage || loading || error) {
      return;
    }

    const sentinel = sentinelRef.current;
    if (!sentinel) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const [entry] = entries;
        if (entry && entry.isIntersecting && hasNextPage && !loading) {
          onLoadMore();
        }
      },
      {
        rootMargin: `${(1 - scrollThreshold) * 100}% 0px`,
        threshold: 0.1,
      }
    );

    observer.observe(sentinel);

    return () => {
      observer.disconnect();
    };
  }, [onLoadMore, hasNextPage, loading, error, scrollThreshold]);

  // Show Load More button if Intersection Observer might not work (e.g., not enough content)
  React.useEffect(() => {
    // Show Load More button if we have items but sentinel might not be visible
    // This is a fallback for cases where there's not enough scrollable content
    const checkShowLoadMore = () => {
      if (!hasNextPage || loading || error) {
        setShowLoadMore(false);
        return;
      }

      const sentinel = sentinelRef.current;
      if (!sentinel) {
        setShowLoadMore(false);
        return;
      }

      // Check if sentinel is already visible (meaning we need Load More button)
      const rect = sentinel.getBoundingClientRect();
      const isVisible = rect.top < window.innerHeight;
      setShowLoadMore(isVisible && items.length > 0);
    };

    checkShowLoadMore();
    window.addEventListener("resize", checkShowLoadMore);
    window.addEventListener("scroll", checkShowLoadMore);

    return () => {
      window.removeEventListener("resize", checkShowLoadMore);
      window.removeEventListener("scroll", checkShowLoadMore);
    };
  }, [hasNextPage, loading, error, items.length, onLoadMore, scrollThreshold]);

  // Error state
  if (error && !loading) {
    if (errorComponent) {
      return <>{errorComponent}</>;
    }
    return (
      <ErrorState
        title={t("cardGrid.unableToLoadResults")}
        description={errorMessage || error.message || t("cardGrid.searchCouldNotBeCompleted")}
        retryText={t("cardGrid.retry")}
        onRetry={onRetry}
      />
    );
  }

  // Empty state
  if (!loading && items.length === 0 && !error) {
    if (emptyComponent) {
      return <>{emptyComponent}</>;
    }
    return (
      <EmptyState
        title={displayEmptyMessage}
      />
    );
  }

  // Loading skeleton
  const renderSkeleton = () => {
    if (loadingComponent) {
      return loadingComponent;
    }
    return (
      <Grid cols={responsiveColumns} gap={gap}>
        {Array.from({ length: skeletonCount }).map((_, index) => (
          <Skeleton key={`skeleton-${index}`} variant="rectangular" height={300} />
        ))}
      </Grid>
    );
  };

  // If loading and no items, show skeleton
  if (loading && items.length === 0) {
    return renderSkeleton();
  }

  // Render grid with items
  return (
    <Box>
      <Grid cols={responsiveColumns} gap={gap}>
        {items.map((item, index) => {
          // Try to use item.id if available, otherwise fall back to index
          const key = (item as any)?.id ?? index;
          return (
            <React.Fragment key={key}>
              {renderItem(item, index)}
            </React.Fragment>
          );
        })}
      </Grid>

      {/* Loading skeleton for additional items */}
      {loading && items.length > 0 && (
        <Box sx={{ mt: gap }}>
          <Grid cols={responsiveColumns} gap={gap}>
            {Array.from({ length: Math.min(3, skeletonCount) }).map((_, index) => (
              <Skeleton key={`loading-skeleton-${index}`} variant="rectangular" height={300} />
            ))}
          </Grid>
        </Box>
      )}

      {/* Sentinel element for Intersection Observer */}
      {hasNextPage && !loading && (
        <Box
          ref={sentinelRef}
          sx={{
            height: "1px",
            width: "100%",
            mt: gap,
          }}
          aria-hidden="true"
        />
      )}

      {/* Load More button (fallback) */}
      {showLoadMore && hasNextPage && !loading && onLoadMore && (
        <Stack alignItems="center" sx={{ mt: 3 }}>
          <Button variant="outlined" onClick={onLoadMore}>
            {t("cardGrid.loadMore")}
          </Button>
        </Stack>
      )}
    </Box>
  );
}

export default CardGrid;

