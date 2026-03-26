"use client";

import * as React from "react";
import { Avatar as MAvatar, type AvatarProps as MAvatarProps } from "@mui/material";

export type LogoSize = "xs" | "sm" | "md" | "lg" | number;

export interface LogoProps extends Omit<MAvatarProps, "variant"> {
  size?: LogoSize;
  borderRadius?: string | number;
}

const sizeMap: Record<Exclude<LogoSize, number>, number> = {
  xs: 24,
  sm: 26,
  md: 30,
  lg: 32,
};

function resolveSize(size: LogoSize | undefined) {
  if (typeof size === "number") return size;
  return sizeMap[size ?? "md"];
}

export const Logo: React.FC<LogoProps> = ({ size, borderRadius = ".3rem", sx, ...rest }) => {
  const px = resolveSize(size);

  return (
    <MAvatar
      variant="rounded"
      sx={{
        width: px,
        height: px,
        borderRadius,
        "& img": { objectFit: "contain" },
        ...sx,
      }}
      {...rest}
    />
  );
};

export default Logo;
