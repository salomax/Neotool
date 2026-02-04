"use client";

import React from "react";
import { Box, Typography, Paper, useTheme, alpha } from "@mui/material";
import Link from "next/link";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";

export interface ModuleCardProps {
  title: string;
  description: string;
  href?: string;
  icon?: React.ReactNode;
  disabled?: boolean;
  onClick?: () => void;
  actionLabel?: string;
}

export function ModuleCard({
  title,
  description,
  href,
  icon,
  disabled,
  onClick,
  actionLabel,
}: ModuleCardProps) {
  const theme = useTheme();

  const CardContent = (
    <Paper
      elevation={0}
      className="module-card-root"
      sx={{
        p: 4,
        height: "100%",
        minHeight: 320,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        textAlign: "center",
        transition: "all 0.2s ease-in-out",
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: 4,
        bgcolor: "background.paper",
        cursor: disabled ? "default" : "pointer",
        opacity: disabled ? 0.6 : 1,
        "&:hover": !disabled
          ? {
              transform: "translateY(-4px)",
              boxShadow: theme.shadows[4],
              borderColor: "primary.main",
              "& .arrow-icon": {
                transform: "translateX(4px)",
                color: "primary.main",
              },
            }
          : {},
      }}
    >
      <Box
        sx={{
          mb: 3,
          height: 140,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {icon}
      </Box>

      <Typography variant="h5" component="h2" gutterBottom fontWeight="bold" sx={{ mb: 2 }}>
        {title}
      </Typography>

      <Typography variant="body1" color="text.secondary" sx={{ mb: 4, flexGrow: 1, maxWidth: '80%' }}>
        {description}
      </Typography>

      {!disabled && actionLabel && (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            color: "text.primary",
            fontWeight: 700,
            mt: "auto",
          }}
        >
          <Typography variant="button" sx={{ mr: 1, fontWeight: 700, textTransform: 'none', fontSize: '0.9rem' }}>
            {actionLabel}
          </Typography>
          <ArrowForwardIcon className="arrow-icon" sx={{ transition: "transform 0.2s", fontSize: 20 }} />
        </Box>
      )}
    </Paper>
  );

  if (href && !disabled) {
    return (
      <Link href={href} style={{ textDecoration: "none", height: "100%", display: "block" }} onClick={onClick}>
        {CardContent}
      </Link>
    );
  }

  return <Box sx={{ height: "100%" }} onClick={disabled ? undefined : onClick}>{CardContent}</Box>;
}
