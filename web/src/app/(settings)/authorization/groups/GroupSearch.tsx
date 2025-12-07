"use client";

import React from "react";
import { Box, ContainerProps, useTheme } from "@mui/material";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface GroupSearchProps {
  value: string;
  onChange: (value: string) => void;
  onSearch?: (value: string) => void; // Optional debounced search callback
  placeholder?: string;
  maxWidth?: ContainerProps["maxWidth"];
}

/**
 * GroupSearch component for filtering groups by name
 */
export const GroupSearch: React.FC<GroupSearchProps> = ({
  value,
  onChange,
  onSearch,
  placeholder,
  maxWidth,
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
        placeholder={placeholder || "Search groups by name..."}
        fullWidth
        debounceMs={300}
        name="group-search"
        data-testid="group-search"
      />
    </Box>
  );
};

