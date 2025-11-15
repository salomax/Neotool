"use client";
import * as React from "react";
import Image from "next/image";
import { Box, BoxProps } from "@mui/material";

export interface LogoProps extends Omit<BoxProps, 'children'> {
  variant?: 'white' | 'blue';
  size?: 'small' | 'medium' | 'large' | 'xlarge';
  width?: number;
  height?: number;
  showText?: boolean;
  textVariant?: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';
}

const sizeMap = {
  small: { width: 40, height: 38 },
  medium: { width: 80, height: 76 },
  large: { width: 120, height: 113 },
  xlarge: { width: 150, height: 142 },
};

export function Logo({ 
  variant = 'blue', 
  size = 'medium',
  width,
  height,
  showText = false,
  textVariant = 'h4',
  sx,
  ...props 
}: LogoProps) {
  const dimensions = sizeMap[size];
  const logoWidth = width || dimensions.width;
  const logoHeight = height || dimensions.height;

  const logoSrc = variant === 'white' 
    ? '/images/logos/neotool-logo-white.svg'
    : '/images/logos/neotool-logo-blue.svg';

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        ...sx,
      }}
      {...props}
    >
      <Image
        src={logoSrc}
        alt="Neotool"
        width={logoWidth}
        height={logoHeight}
        priority
      />
      {showText && (
        <Box
          component="span"
          sx={{
            fontSize: textVariant,
            fontWeight: 600,
            color: variant === 'white' ? 'white' : 'text.primary',
          }}
        >
          Neotool
        </Box>
      )}
    </Box>
  );
}
