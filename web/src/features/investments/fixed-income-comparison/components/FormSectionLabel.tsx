"use client";
 
 import type { ReactNode } from "react";
 import { Typography } from "@mui/material";
 import type { SxProps, Theme } from "@mui/material/styles";
 
 export function FormSectionLabel({
   children,
   mb = 1,
   color = "text.secondary",
  fontSize = 11,
   sx,
 }: {
   children: ReactNode;
   mb?: number;
   color?: string;
  fontSize?: number;
   sx?: SxProps<Theme>;
 }) {
   return (
     <Typography
      className="FormSectionLabel-root"
       sx={{
         display: "block",
         textTransform: "uppercase",
        letterSpacing: "0.05em",
         color,
        fontSize,
         mb,
         ...sx,
       }}
     >
       {children}
     </Typography>
   );
 }
