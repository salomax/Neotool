"use client";
import * as React from "react";
import Image from "next/image";

export interface LogoMarkProps {
  variant?: 'white' | 'blue';
  width?: number;
  height?: number;
  className?: string;
  style?: React.CSSProperties;
}

export function LogoMark({ 
  variant = 'blue', 
  width = 120, 
  height = 113,
  className,
  style,
  ...props 
}: LogoMarkProps) {
  const logoSrc = variant === 'white' 
    ? '/images/logos/neotool-logo-white.svg'
    : '/images/logos/neotool-logo-blue.svg';

  return (
    <Image
      src={logoSrc}
      alt="Neotool"
      width={width}
      height={height}
      className={className}
      style={style}
      priority
      {...props}
    />
  );
}
