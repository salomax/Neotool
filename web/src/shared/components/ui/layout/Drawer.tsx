"use client";

import React from "react";
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

export interface DrawerProps extends Omit<MUIDrawerProps, 'open'> {
  open: boolean;
  onClose: () => void;
  title?: string;
  showCloseButton?: boolean;
  showMenuButton?: boolean;
  onMenuClick?: () => void;
  variant?: 'temporary' | 'persistent' | 'permanent';
  anchor?: 'left' | 'right' | 'top' | 'bottom';
  width?: number | string;
  height?: number | string;
  /** If true, forces temporary variant on mobile devices. Default: true */
  forceMobileTemporary?: boolean;
  /** Footer content (e.g., action buttons). Always visible at the bottom of the drawer. */
  footer?: React.ReactNode;
  children: React.ReactNode;
}

export const Drawer: React.FC<DrawerProps> = ({
  open,
  onClose,
  title,
  showCloseButton = true,
  showMenuButton = false,
  onMenuClick,
  variant = 'temporary',
  anchor = 'left',
  width = 600,
  height = '100%',
  forceMobileTemporary = true,
  footer,
  children,
  sx,
  ...props
}) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  // Auto-adjust variant for mobile (unless explicitly disabled)
  const drawerVariant = isMobile && forceMobileTemporary ? 'temporary' : variant;

  // Merge sx prop with drawer styles
  const drawerSx = {
    '& .MuiDrawer-paper': {
      width: anchor === 'left' || anchor === 'right' ? width : '100%',
      height: anchor === 'top' || anchor === 'bottom' ? height : '100%',
      borderRadius: 0,
    },
    ...sx,
  };

  return (
    <MUIDrawer
      open={open}
      onClose={onClose}
      variant={drawerVariant}
      anchor={anchor}
      sx={drawerSx}
      {...props}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header - Always visible when configured */}
        {(title || showCloseButton || showMenuButton) && (
          <>
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
            <Divider />
          </>
        )}

        {/* Content - Scrollable area */}
        <Box sx={{ flex: 1, overflow: 'auto' }}>
          {children}
        </Box>

        {/* Footer - Always visible when provided */}
        {footer && (
          <>
            <Divider />
            <Box
              sx={{
                p: 2,
                flexShrink: 0,
              }}
            >
              {footer}
            </Box>
          </>
        )}
      </Box>
    </MUIDrawer>
  );
};

export default Drawer;
