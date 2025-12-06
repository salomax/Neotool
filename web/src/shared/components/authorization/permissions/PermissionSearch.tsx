"use client";

import React from "react";
import { Box } from "@mui/material";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface PermissionSearchProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

/**
 * PermissionSearch component for filtering permissions by name
 */
export const PermissionSearch: React.FC<PermissionSearchProps> = ({
  value,
  onChange,
  placeholder,
}) => {
  return (
    <Box sx={{ mb: 2 }}>
      <SearchField
        value={value}
        onChange={onChange}
        placeholder={placeholder || "Search permissions by name..."}
        fullWidth
        debounceMs={300}
        name="permission-search"
        data-testid="permission-search"
      />
    </Box>
  );
};

