"use client";

import React from "react";
import { Box, ContainerProps, useTheme } from "@mui/material";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface RoleSearchProps {
  value: string;
  onChange: (value: string) => void;
  onSearch?: (value: string) => void; // Optional debounced search callback
  placeholder?: string;
  maxWidth?: ContainerProps["maxWidth"];
  autoFocus?: boolean;
}

/**
 * RoleSearch component for filtering roles by name
 */
export const RoleSearch: React.FC<RoleSearchProps> = ({
  value,
  onChange,
  onSearch,
  placeholder,
  maxWidth,
  autoFocus,
}) => {
  const theme = useTheme();
  
  const maxWidthStyles = maxWidth
    ? {
        maxWidth:
          typeof maxWidth === "string"
            ? theme.breakpoints.values[maxWidth as keyof typeof theme.breakpoints.values]
            : maxWidth,
      }
    : {};

  // Use onSearch for debounced search, fallback to onChange if not provided
  const handleSearch = onSearch || onChange;

  return (
    <Box sx={{ mb: 2, ...maxWidthStyles }}>
      <SearchField
        value={value}
        onChange={onChange}
        onSearch={handleSearch}
        placeholder={placeholder || "Search roles by name..."}
        fullWidth
        debounceMs={300}
        name="role-search"
        data-testid="role-search"
        autoFocus={autoFocus}
      />
    </Box>
  );
};
