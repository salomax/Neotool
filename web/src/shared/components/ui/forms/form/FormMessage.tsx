"use client";
import React from "react";
import { Alert } from "@mui/material";
export const FormMessage: React.FC<{
  type?: "success" | "info" | "warning" | "error";
  children: React.ReactNode;
}> = ({ type = "info", children }) => {
  return (
    <Alert severity={type} sx={{ mt: 2 }}>
      {children}
    </Alert>
  );
};
