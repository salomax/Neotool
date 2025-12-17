"use client";

import React from "react";
import { Box } from "@mui/material";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface PermissionSearchProps {
  value: string;
  onChange: (value: string) => void;
  onSearch?: (value: string) => void; // Debounced search callback
  placeholder?: string;
}

/**
 * PermissionSearch component for filtering permissions by name
 * Supports both immediate input updates (onChange) and debounced search (onSearch)
 */
export const PermissionSearch: React.FC<PermissionSearchProps> = ({
  value,
  onChange,
  onSearch,
  placeholder,
}) => {
  return (
    <Box sx={{ mb: 2 }}>
      <SearchField
        value={value}
        onChange={onChange}
        onSearch={onSearch}
        placeholder={placeholder || "Search permissions by name..."}
        fullWidth
        debounceMs={300}
        name="permission-search"
        data-testid="permission-search"
      />
    </Box>
  );
};

