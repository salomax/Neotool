"use client";

import React from "react";
import { Box, ContainerProps, useTheme } from "@mui/material";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface UserSearchProps {
  value: string;
  onChange: (value: string) => void;
  onSearch?: (value: string) => void; // Optional debounced search callback
  placeholder?: string;
  maxWidth?: ContainerProps["maxWidth"];
  autoFocus?: boolean;
}

/**
 * UserSearch component for filtering users by name or email
 */
export const UserSearch: React.FC<UserSearchProps> = ({
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
        placeholder={placeholder || "Search users by name or email..."}
        fullWidth
        debounceMs={300}
        name="user-search"
        data-testid="user-search"
        autoFocus={autoFocus}
      />
    </Box>
  );
};

