"use client";

import * as React from "react";
import { usePathname } from "next/navigation";
import { useMediaQuery, useTheme, SxProps, Theme } from "@mui/material/styles";
import { Breadcrumbs as MuiBreadcrumbs, Typography, Box } from "@/shared/ui/mui-imports";
import HomeIcon from "@mui/icons-material/Home";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { Link } from "./Link";
import { getTestIdProps } from "@/shared/utils/testid";

export interface BreadcrumbItem {
  label: string;
  href?: string;
  icon?: React.ReactNode;
  disabled?: boolean;
}

export interface RouteConfig {
  path: string;
  label: string | ((params: Record<string, string>) => string);
  icon?: React.ReactNode;
}

export interface BreadcrumbProps {
  // Manual control
  items?: BreadcrumbItem[];
  
  // Auto-generation
  autoGenerate?: boolean;
  routeConfig?: RouteConfig[];
  homeLabel?: string;
  homeHref?: string;
  homeIcon?: React.ReactNode;
  
  // Behavior
  maxItems?: number;
  showHome?: boolean;
  currentPageClickable?: boolean;
  responsive?: boolean;
  
  // Customization
  separator?: React.ReactNode;
  variant?: "default" | "compact" | "minimal";
  size?: "small" | "medium" | "large";
  
  // Rendering
  renderItem?: (item: BreadcrumbItem, index: number, isLast: boolean) => React.ReactNode;
  renderSeparator?: () => React.ReactNode;
  
  // Styling
  className?: string;
  sx?: SxProps<Theme>;
  name?: string;
  "data-testid"?: string;
}

// Create NextLinkBehavior for MUI Breadcrumbs
const NextLinkBehavior = React.forwardRef<HTMLAnchorElement, React.ComponentProps<typeof Link>>(
  (props, ref) => {
    return <Link ref={ref} {...props} />;
  }
);
NextLinkBehavior.displayName = "NextLinkBehavior";

/**
 * Generates breadcrumb items from a pathname
 */
function generateBreadcrumbsFromPathname(
  pathname: string | null,
  routeConfig?: RouteConfig[],
  homeLabel: string = "Home",
  homeHref: string = "/",
  homeIcon?: React.ReactNode
): BreadcrumbItem[] {
  if (!pathname || pathname === "/") {
    return homeHref ? [{ label: homeLabel, href: homeHref, icon: homeIcon }] : [];
  }

  const segments = pathname.split("/").filter(Boolean);
  const items: BreadcrumbItem[] = [];

  // Add home item
  if (homeHref) {
    items.push({ label: homeLabel, href: homeHref, icon: homeIcon });
  }

  // Build path progressively
  let currentPath = "";
  segments.forEach((segment, index) => {
    currentPath += `/${segment}`;
    const isLast = index === segments.length - 1;

    // Try to find matching route config
    let label = segment;
    let icon: React.ReactNode | undefined;

    if (routeConfig) {
      // Try exact match first
      let matchedConfig = routeConfig.find((config) => config.path === currentPath);
      
      // Try pattern match (e.g., /users/[id])
      if (!matchedConfig) {
        matchedConfig = routeConfig.find((config) => {
          const configSegments = config.path.split("/").filter(Boolean);
          const pathSegments = currentPath.split("/").filter(Boolean);
          
          if (configSegments.length !== pathSegments.length) return false;
          
          return configSegments.every((configSeg, i) => {
            // Match dynamic segments like [id], [...slug]
            if (configSeg.startsWith("[") && configSeg.endsWith("]")) {
              return true;
            }
            return configSeg === pathSegments[i];
          });
        });
      }

      if (matchedConfig) {
        if (typeof matchedConfig.label === "function") {
          // Extract params from pathname
          const params: Record<string, string> = {};
          const configSegments = matchedConfig.path.split("/").filter(Boolean);
          const pathSegments = currentPath.split("/").filter(Boolean);
          
          configSegments.forEach((configSeg, i) => {
            if (configSeg.startsWith("[") && configSeg.endsWith("]")) {
              const paramName = configSeg.slice(1, -1).replace("...", "");
              params[paramName] = pathSegments[i] || "";
            }
          });
          
          label = matchedConfig.label(params);
        } else {
          label = matchedConfig.label;
        }
        icon = matchedConfig.icon;
      } else {
        // Capitalize and format segment as fallback
        label = segment
          .split("-")
          .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
          .join(" ");
      }
    } else {
      // Default: capitalize and format segment
      label = segment
        .split("-")
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");
    }

    items.push({
      label,
      href: isLast ? undefined : currentPath,
      icon,
    });
  });

  return items;
}

/**
 * Truncates breadcrumb items, keeping first and last items
 */
function truncateItems(
  items: BreadcrumbItem[],
  maxItems: number
): { items: BreadcrumbItem[]; truncated: boolean } {
  if (items.length <= maxItems) {
    return { items, truncated: false };
  }

  // Always keep first (home) and last (current page)
  const first = items[0];
  const last = items[items.length - 1];
  const middle = items.slice(1, -1);

  if (maxItems < 3) {
    // If maxItems is too small, just return first and last
    return {
      items: [first, last].filter(Boolean) as BreadcrumbItem[],
      truncated: true,
    };
  }

  // Calculate how many middle items we can show
  const availableSlots = maxItems - 2; // First and last
  const itemsToShow = Math.max(1, availableSlots);

  // Take items from the end of middle section
  const visibleMiddle = middle.slice(-itemsToShow);

  return {
    items: [first, ...visibleMiddle, last].filter(Boolean) as BreadcrumbItem[],
    truncated: true,
  };
}

export const Breadcrumb = React.forwardRef<HTMLElement, BreadcrumbProps>(
  (
    {
      items: manualItems,
      autoGenerate = true,
      routeConfig,
      homeLabel = "Home",
      homeHref = "/",
      homeIcon = <HomeIcon fontSize="small" />,
      maxItems = 5,
      showHome = true,
      currentPageClickable = false,
      responsive = true,
      separator,
      variant = "default",
      size = "medium",
      renderItem,
      renderSeparator,
      className,
      sx,
      name,
      "data-testid": dataTestId,
    },
    ref
  ) => {
    const pathname = usePathname();
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
    const testIdProps = getTestIdProps("Breadcrumb", name, dataTestId);

    // Determine which items to use
    const items = React.useMemo(() => {
      if (manualItems) {
        return manualItems;
      }

      if (autoGenerate) {
        const generated = generateBreadcrumbsFromPathname(
          pathname,
          routeConfig,
          homeLabel,
          showHome ? homeHref : undefined,
          showHome ? homeIcon : undefined
        );

        // Apply maxItems truncation
        if (maxItems > 0 && generated.length > maxItems) {
          return truncateItems(generated, maxItems).items;
        }

        return generated;
      }

      return [];
    }, [manualItems, autoGenerate, pathname, routeConfig, homeLabel, homeHref, homeIcon, showHome, maxItems]);

    // Responsive: collapse on mobile
    const displayItems = React.useMemo(() => {
      if (responsive && isMobile && items.length > 2) {
        // On mobile, show only last 2 items (or just last if only 1 item)
        const lastTwo = items.slice(-2);
        return lastTwo;
      }
      return items;
    }, [items, responsive, isMobile]);

    // Default separator
    const defaultSeparator = separator !== undefined 
      ? separator 
      : variant === "minimal" 
        ? "/" 
        : <ChevronRightIcon fontSize="small" />;

    // Size-based typography variant
    const typographyVariant = size === "small" ? "body2" : size === "large" ? "body1" : "body2";

    // Render breadcrumb item
    const renderBreadcrumbItem = (item: BreadcrumbItem, index: number) => {
      const isLast = index === displayItems.length - 1;
      const isClickable = item.href && (!isLast || currentPageClickable) && !item.disabled;

      // Use custom renderItem if provided
      if (renderItem) {
        return renderItem(item, index, isLast);
      }

      // Default rendering
      const content = (
        <>
          {item.icon && (
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                fontSize: size === "small" ? "0.875rem" : size === "large" ? "1rem" : "0.875rem",
                mr: 0.5,
              }}
            >
              {item.icon}
            </Box>
          )}
          <Typography
            variant={typographyVariant}
            component="span"
            sx={{
              fontSize:
                size === "small" ? "0.75rem" : size === "large" ? "0.9375rem" : "0.875rem",
            }}
          >
            {item.label}
          </Typography>
        </>
      );

      if (isClickable) {
        return (
          <Link
            href={item.href!}
            sx={{
              display: "flex",
              alignItems: "center",
              textDecoration: "none",
              color: "text.secondary",
              "&:hover": {
                textDecoration: "underline",
                color: "text.primary",
              },
            }}
            aria-label={item.label}
          >
            {content}
          </Link>
        );
      }

      return (
        <Box
          component="span"
          sx={{
            display: "flex",
            alignItems: "center",
            color: isLast ? "text.primary" : "text.secondary",
            fontWeight: isLast ? 600 : 400,
          }}
          aria-current={isLast ? "page" : undefined}
        >
          {content}
        </Box>
      );
    };

    // Render separator
    const renderBreadcrumbSeparator = () => {
      if (renderSeparator) {
        return renderSeparator();
      }
      return defaultSeparator;
    };

    // Variant-based styling
    const variantStyles = React.useMemo(() => {
      switch (variant) {
        case "compact":
          return {
            padding: "4px 0",
            "& .MuiBreadcrumbs-ol": {
              gap: "4px",
            },
          };
        case "minimal":
          return {
            padding: "2px 0",
            "& .MuiBreadcrumbs-ol": {
              gap: "2px",
            },
          };
        default:
          return {
            padding: "8px 0",
            "& .MuiBreadcrumbs-ol": {
              gap: "8px",
            },
          };
      }
    }, [variant]);

    if (displayItems.length === 0) {
      return null;
    }

    return (
      <MuiBreadcrumbs
        ref={ref}
        separator={renderBreadcrumbSeparator()}
        aria-label="breadcrumb navigation"
        className={className}
        sx={{
          ...variantStyles,
          ...sx,
        }}
        {...testIdProps}
      >
        {displayItems.map((item, index) => (
          <React.Fragment key={`${item.label}-${index}`}>
            {renderBreadcrumbItem(item, index)}
          </React.Fragment>
        ))}
      </MuiBreadcrumbs>
    );
  }
);

Breadcrumb.displayName = "Breadcrumb";

export default Breadcrumb;
