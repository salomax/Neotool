"use client";

import React from "react";
import { Box } from "@mui/material";
import { SearchField } from "@/shared/components/ui/forms/SearchField";

export interface UserSearchProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

/**
 * UserSearch component for filtering users by name, email, or identifier
 */
export const UserSearch: React.FC<UserSearchProps> = ({
  value,
  onChange,
  placeholder,
}) => {
  return (
    <Box sx={{ mb: 2 }}>
      <SearchField
        value={value}
        onChange={onChange}
        placeholder={placeholder || "Search users by name, email, or identifier..."}
        fullWidth
        debounceMs={300}
        name="user-search"
        data-testid="user-search"
      />
    </Box>
  );
};

