"use client";

import React from "react";
import { Box } from "@/shared/components/ui/layout";
import { ErrorAlert } from "@/shared/components/ui/feedback";

/**
 * Base props for ManagementLayout component
 */
export interface ManagementLayoutBaseProps {
  /**
   * Error object to display in error alert
   */
  error: Error | null | undefined;
  /**
   * Callback invoked when user clicks retry on error alert
   */
  onErrorRetry?: () => void;
  /**
   * Fallback message for error alert if error.message is not available
   */
  errorFallbackMessage?: string;
  /**
   * Child components using ManagementLayout subcomponents
   */
  children: React.ReactNode;
}

/**
 * Props for slot subcomponents (Header, Content, Drawer)
 */
export interface SlotProps {
  children: React.ReactNode;
}

/**
 * ManagementLayout component - Generic layout wrapper for management interfaces
 * 
 * Provides a consistent structure for management components with:
 * - Error alert at the top
 * - Header component via ManagementLayout.Header (for search, actions, etc.)
 * - Content component via ManagementLayout.Content (in a flex container)
 * - Optional drawer for create/edit via ManagementLayout.Drawer
 * 
 * Note: Delete dialogs should be included within the Content component, not in the Layout.
 * 
 * @example
 * ```tsx
 * <ManagementLayout
 *   error={error}
 *   onErrorRetry={refetch}
 *   errorFallbackMessage={t("errors.loadFailed")}
 * >
 *   <ManagementLayout.Header>
 *     <Box sx={{ display: "flex", gap: 2, alignItems: "flex-end" }}>
 *       <Box sx={{ flexGrow: 1 }} maxWidth="sm">
 *         <UserSearch ... />
 *       </Box>
 *       <Box sx={{ mb: 2 }}>
 *         <Button>Create</Button>
 *       </Box>
 *     </Box>
 *   </ManagementLayout.Header>
 *   <ManagementLayout.Content>
 *     <UserList ... />
 *     <DeleteConfirmationDialog ... />
 *   </ManagementLayout.Content>
 *   <ManagementLayout.Drawer>
 *     <UserDrawer ... />
 *   </ManagementLayout.Drawer>
 * </ManagementLayout>
 * ```
 */
const ManagementLayoutComponent: React.FC<ManagementLayoutBaseProps> = ({
  error,
  onErrorRetry,
  errorFallbackMessage,
  children,
}) => {
  return (
    <Box fullHeight>
      <ErrorAlert 
        error={error} 
        onRetry={onErrorRetry || (() => {})}
        fallbackMessage={errorFallbackMessage}
      />
      {children}
    </Box>
  );
};

/**
 * Header slot component - Wraps header content (search, actions, etc.)
 */
const Header: React.FC<SlotProps> = ({ children }) => {
  return <>{children}</>;
};

/**
 * Content slot component - Wraps main content with flex layout
 * Applies flex: 1 and minHeight: 0 to fill remaining space
 */
const Content: React.FC<SlotProps> = ({ children }) => {
  return (
    <Box sx={{ flex: 1, minHeight: 0 }}>
      {children}
    </Box>
  );
};

/**
 * Drawer slot component - Wraps drawer components
 */
const Drawer: React.FC<SlotProps> = ({ children }) => {
  return <>{children}</>;
};

// Attach subcomponents to main component
export const ManagementLayout = Object.assign(ManagementLayoutComponent, {
  Header,
  Content,
  Drawer,
});

export default ManagementLayout;
