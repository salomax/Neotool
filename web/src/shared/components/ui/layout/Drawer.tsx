"use client";

import React, { createContext, useContext } from "react";
import {
  Drawer as MUIDrawer,
  DrawerProps as MUIDrawerProps,
  Box,
  IconButton,
  Divider,
  Typography,
  useTheme,
  useMediaQuery,
} from "@mui/material";
import { CloseIcon, MenuIcon } from "../../../ui/mui-imports";
import { RAIL_W } from "@/shared/ui/navigation/SidebarRail";

// Context to pass onClose handler to Header subcomponent
const DrawerContext = createContext<{ onClose: () => void } | null>(null);

// Drawer size presets
export type DrawerSize = 'sm' | 'md' | 'lg' | 'full';

const DRAWER_SIZES: Record<DrawerSize, number | string> = {
  sm: 400,
  md: 600,
  lg: 800,
  full: `calc(100% - ${RAIL_W}px)`,
};

/**
 * Get drawer width based on size prop or fallback to width prop
 */
function getDrawerWidth(
  size: DrawerSize | undefined,
  width: number | string | undefined,
  anchor: 'left' | 'right' | 'top' | 'bottom'
): number | string {
  // If size is provided, use it (only for left/right anchors)
  if (size && (anchor === 'left' || anchor === 'right')) {
    return DRAWER_SIZES[size];
  }
  
  // Otherwise, use width prop or default
  return width ?? DRAWER_SIZES.md;
}

export interface DrawerProps extends Omit<MUIDrawerProps, 'open'> {
  open: boolean;
  onClose: () => void;
  variant?: 'temporary' | 'persistent' | 'permanent';
  anchor?: 'left' | 'right' | 'top' | 'bottom';
  /** Predefined size: 'sm' (400px), 'md' (600px), 'lg' (800px), or 'full' (100% - sidebar width) */
  size?: DrawerSize;
  /** Custom width (number or string). Ignored if size is provided. Default: 600 */
  width?: number | string;
  height?: number | string;
  /** If true, forces temporary variant on mobile devices. Default: true */
  forceMobileTemporary?: boolean;
  children: React.ReactNode;
}

export interface DrawerHeaderProps {
  /** Title text displayed in the header */
  title?: string;
  /** Show close button. Default: true */
  showCloseButton?: boolean;
  /** Show menu button. Default: false */
  showMenuButton?: boolean;
  /** Callback when menu button is clicked */
  onMenuClick?: () => void;
  /** Custom content for the header (overrides title if provided) */
  children?: React.ReactNode;
}

export interface DrawerBodyProps {
  children: React.ReactNode;
}

export interface DrawerFooterProps {
  children: React.ReactNode;
}

/**
 * Drawer component - A slide-out panel for navigation, forms, and supplementary content
 * 
 * Uses a subcomponent pattern for composition:
 * - Drawer.Header - Header with title, close button, and optional menu button
 * - Drawer.Body - Scrollable content area with padding
 * - Drawer.Footer - Footer area with consistent padding
 * 
 * @example
 * ```tsx
 * // Using size prop (recommended)
 * <Drawer open={open} onClose={onClose} anchor="right" size="md">
 *   <Drawer.Header title="Settings" />
 *   <Drawer.Body>
 *     <FormContent />
 *   </Drawer.Body>
 * </Drawer>
 * 
 * // Using custom width (backward compatible)
 * <Drawer open={open} onClose={onClose} anchor="right" width={600}>
 *   <Drawer.Header title="Settings" />
 *   <Drawer.Body>
 *     <FormContent />
 *   </Drawer.Body>
 * </Drawer>
 * 
 * // Full width drawer (excluding sidebar)
 * <Drawer open={open} onClose={onClose} anchor="right" size="full">
 *   <Drawer.Header title="Full Width" />
 *   <Drawer.Body>
 *     <FormContent />
 *   </Drawer.Body>
 * </Drawer>
 * ```
 */
const DrawerComponent: React.FC<DrawerProps> = ({
  open,
  onClose,
  variant = 'temporary',
  anchor = 'left',
  size,
  width,
  height = '100%',
  forceMobileTemporary = true,
  children,
  sx,
  ...props
}) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  // Auto-adjust variant for mobile (unless explicitly disabled)
  const drawerVariant = isMobile && forceMobileTemporary ? 'temporary' : variant;

  // Calculate drawer width
  const drawerWidth = getDrawerWidth(size, width, anchor);

  // Merge sx prop with drawer styles
  const drawerSx = {
    '& .MuiDrawer-paper': {
      width: anchor === 'left' || anchor === 'right' ? drawerWidth : '100%',
      height: anchor === 'top' || anchor === 'bottom' ? height : '100%',
      borderRadius: 0,
    },
    ...sx,
  };

  // Extract subcomponents from children
  let headerContent: React.ReactNode = null;
  let bodyContent: React.ReactNode = null;
  let footerContent: React.ReactNode = null;
  let otherChildren: React.ReactNode[] = [];

  React.Children.forEach(children, (child) => {
    if (React.isValidElement(child)) {
      if (child.type === Header) {
        headerContent = child;
      } else if (child.type === Body) {
        bodyContent = child;
      } else if (child.type === Footer) {
        footerContent = child;
      } else {
        otherChildren.push(child);
      }
    } else {
      otherChildren.push(child);
    }
  });

  // If no subcomponents are used, render children directly in body area
  const hasSubcomponents = headerContent || bodyContent || footerContent;
  const bodyToRender = bodyContent || (hasSubcomponents ? null : <Body>{children}</Body>);

  return (
    <MUIDrawer
      open={open}
      onClose={onClose}
      variant={drawerVariant}
      anchor={anchor}
      sx={drawerSx}
      {...props}
    >
      <DrawerContext.Provider value={{ onClose }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header */}
          {headerContent && (
            <>
              {headerContent}
              <Divider />
            </>
          )}

          {/* Body - Scrollable area */}
          {bodyToRender}

          {/* Footer */}
          {footerContent && (
            <>
              <Divider />
              {footerContent}
            </>
          )}
        </Box>
      </DrawerContext.Provider>
    </MUIDrawer>
  );
};

/**
 * Header subcomponent - Displays title, close button, and optional menu button
 */
const Header: React.FC<DrawerHeaderProps> = ({
  title,
  showCloseButton = true,
  showMenuButton = false,
  onMenuClick,
  children,
}) => {
  const context = useContext(DrawerContext);
  const onClose = context?.onClose || (() => {});

  // If custom children provided, render them instead of default header
  if (children) {
    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          p: 2,
          minHeight: 64,
          flexShrink: 0,
        }}
      >
        {children}
      </Box>
    );
  }

  // Default header with title and buttons
  if (!title && !showCloseButton && !showMenuButton) {
    return null;
  }

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        p: 2,
        minHeight: 64,
        flexShrink: 0,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flex: 1 }}>
        {showMenuButton && (
          <IconButton
            onClick={onMenuClick || (() => {})}
            size="small"
            sx={{ mr: 1 }}
            aria-label={title ? `${title} menu` : "Menu"}
            disabled={!onMenuClick}
          >
            <MenuIcon />
          </IconButton>
        )}
        {title && (
          <Typography variant="h6" component="h2" sx={{ flex: 1 }}>
            {title}
          </Typography>
        )}
      </Box>
      
      {showCloseButton && (
        <IconButton
          onClick={onClose}
          size="small"
          aria-label={title ? `Close ${title}` : "Close drawer"}
        >
          <CloseIcon />
        </IconButton>
      )}
    </Box>
  );
};

/**
 * Body subcomponent - Scrollable content area with padding
 */
const Body: React.FC<DrawerBodyProps> = ({ children }) => {
  return (
    <Box sx={{ flex: 1, overflow: 'auto', p: 3 }}>
      {children}
    </Box>
  );
};

/**
 * Footer subcomponent - Footer area with consistent padding
 */
const Footer: React.FC<DrawerFooterProps> = ({ children }) => {
  return (
    <Box
      sx={{
        p: 2,
        flexShrink: 0,
        bgcolor: 'background.default'
      }}
    >
      {children}
    </Box>
  );
};

// Attach subcomponents to main component
export const Drawer = Object.assign(DrawerComponent, {
  Header,
  Body,
  Footer,
});

export default Drawer;
