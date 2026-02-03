"use client";
import * as React from "react";
import Image from "next/image";
import { Box, BoxProps } from "@mui/material";

export interface WordmarkProps extends Omit<BoxProps, 'children'> {
  variant?: 'white' | 'blue';
  size?: 'small' | 'medium' | 'large' | 'xlarge';
  width?: number;
  height?: number;
}

const sizeMap = {
  small: { width: 173, height: 38 },
  medium: { width: 346, height: 76 },
  large: { width: 515, height: 113 },
  xlarge: { width: 647, height: 142 },
};

export function Wordmark({ 
  variant = 'blue', 
  size = 'medium',
  width,
  height,
  sx,
  ...props 
}: WordmarkProps) {
  const dimensions = sizeMap[size];
  const logoWidth = width || dimensions.width;
  const logoHeight = height || dimensions.height;

  const logoSrc = variant === 'white' 
    ? '/images/logos/neotool-wordmark-white.svg'
    : '/images/logos/neotool-wordmark-blue.svg';

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        ...sx,
      }}
      {...props}
    >
      <Image
        src={logoSrc}
        alt="Invistus"
        width={logoWidth}
        height={logoHeight}
        priority
      />
    </Box>
  );
}
