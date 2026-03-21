"use client";

import React, { createContext, useContext } from "react";
import {
  Drawer as MUIDrawer,
  DrawerProps as MUIDrawerProps,
  Box,
  IconButton,
  Divider,
  Typography,
  TypographyProps,
  alpha,
  useTheme,
  useMediaQuery,
} from "@mui/material";
import { CloseIcon, MenuIcon } from "../../../ui/mui-imports";
import { RAIL_W } from "@/shared/ui/navigation/SidebarRail";
import { Button, type ButtonProps } from "@/shared/components/ui/primitives/Button";

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
  /** Icon displayed before the title */
  icon?: React.ReactNode;
  /** Props forwarded to the title Typography component */
  titleTypographyProps?: TypographyProps;
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

export interface DrawerActionButtonsProps {
  cancelText: React.ReactNode;
  submitText: React.ReactNode;
  onCancel: () => void;
  onSubmit?: () => void;
  submitForm?: string;
  submitDisabled?: boolean;
  cancelDisabled?: boolean;
  submitting?: boolean;
  submittingText?: string;
  "data-testid"?: string;
  cancelTestId?: string;
  submitTestId?: string;
}

export type DrawerCancelButtonProps = Omit<ButtonProps, "variant" | "type" | "loading" | "loadingText">;

export const DrawerCancelButton: React.FC<DrawerCancelButtonProps> = (props) => {
  return <Button variant="outlined" {...props} />;
};

export type DrawerSubmitButtonProps = Omit<ButtonProps, "variant"> & {
  submitting?: boolean;
  submittingText?: string;
};

export const DrawerSubmitButton: React.FC<DrawerSubmitButtonProps> = ({
  submitting = false,
  submittingText,
  loading,
  loadingText,
  disabled,
  sx,
  ...props
}) => {
  const baseSx = (theme: any) => ({
    boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.35)}`,
    "&:hover": {
      boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.45)}`,
    },
    "&:active": {
      boxShadow: `0 6px 16px ${alpha(theme.palette.primary.main, 0.55)}`,
    },
    "&.Mui-disabled": {
      boxShadow: "none",
    },
  });

  const mergedSx =
    typeof sx === "function"
      ? (theme: any) => ({ ...baseSx(theme), ...(sx as any)(theme) })
      : sx
        ? (theme: any) => ({ ...baseSx(theme), ...(sx as any) })
        : baseSx;

  return (
    <Button
      variant="contained"
      loading={submitting || loading || false}
      loadingText={submittingText ?? loadingText}
      disabled={disabled}
      sx={mergedSx}
      {...props}
    />
  );
};

export const DrawerActionButtons: React.FC<DrawerActionButtonsProps> = ({
  cancelText,
  submitText,
  onCancel,
  onSubmit,
  submitForm,
  submitDisabled = false,
  cancelDisabled = false,
  submitting = false,
  submittingText,
  "data-testid": dataTestId,
  cancelTestId,
  submitTestId,
}) => {
  const submitType = submitForm ? "submit" : "button";

  return (
    <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }} data-testid={dataTestId}>
      <DrawerCancelButton onClick={onCancel} disabled={cancelDisabled} data-testid={cancelTestId}>
        {cancelText}
      </DrawerCancelButton>
      <DrawerSubmitButton
        type={submitType}
        form={submitForm}
        onClick={submitType === "button" ? onSubmit : undefined}
        disabled={submitDisabled}
        submitting={submitting}
        submittingText={submittingText}
        data-testid={submitTestId}
      >
        {submitText}
      </DrawerSubmitButton>
    </Box>
  );
};

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
      bgcolor: theme.palette.mode === 'dark' 
        ? (theme as any).custom?.palette?.sidebarBg || '#111729'
        : 'background.paper',
      backgroundImage: theme.palette.mode === 'dark' ? 'none' : undefined,
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
  icon,
  titleTypographyProps,
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
  if (!title && !showCloseButton && !showMenuButton && !icon) {
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
        {icon && (
          <Box sx={{ display: 'flex', color: 'text.secondary' }}>
            {icon}
          </Box>
        )}
        {title && (
          <Typography 
            variant="h5" 
            component="h2" 
            sx={{ flex: 1 }}
            {...titleTypographyProps}
          >
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
const Body = React.forwardRef<HTMLDivElement, DrawerBodyProps>(
  ({ children }, ref) => {
    return (
      <Box ref={ref} sx={{ flex: 1, overflow: 'auto', p: 3 }}>
        {children}
      </Box>
    );
  }
);

Body.displayName = 'DrawerBody';

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
  ActionButtons: DrawerActionButtons,
  CancelButton: DrawerCancelButton,
  SubmitButton: DrawerSubmitButton,
});

export default Drawer;
