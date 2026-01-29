"use client";

import * as React from "react";
import { Box, Typography } from "@mui/material";

export interface SectionHeaderProps {
  title: React.ReactNode;
  icon?: React.ReactNode;
  actions?: React.ReactNode;
  sx?: React.ComponentProps<typeof Box>["sx"];
}

export const SectionHeader: React.FC<SectionHeaderProps> = ({
  title,
  icon,
  actions,
  sx,
}) => {
  const titleNode =
    typeof title === "string" ? (
      <Typography variant="h6" component="h2" sx={{ fontWeight: 600 }}>
        {title}
      </Typography>
    ) : (
      title
    );

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1,
        mb: 2,
        ...sx,
      }}
    >
      {icon}
      {titleNode}
      <Box sx={{ flexGrow: 1 }} />
      {actions}
    </Box>
  );
};

export default SectionHeader;

