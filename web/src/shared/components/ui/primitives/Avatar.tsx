"use client";

import * as React from "react";
import { Avatar as MAvatar, AvatarProps as MAvatarProps } from "@mui/material";
import { getTestIdProps } from '@/shared/utils/testid';

export type AvatarSize = 'small' | 'medium' | 'large';

export interface AvatarProps extends Omit<MAvatarProps, 'size'> {
  name?: string;
  src?: string;
  size?: AvatarSize;
  'data-testid'?: string;
}

function initials(name: string | undefined) {
  if (!name) return "?";
  const parts = name.trim().split(" ").filter(Boolean);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1]?.[0] ?? "" : "";
  return (first + last).toUpperCase();
}

const sizeMap: Record<AvatarSize, { width: number; height: number; fontSize: string }> = {
  small: { width: 24, height: 24, fontSize: '0.75rem' },
  medium: { width: 34, height: 34, fontSize: '1rem' },
  large: { width: 56, height: 56, fontSize: '1.25rem' },
};

export const Avatar: React.FC<AvatarProps> = ({
  name,
  src,
  size = 'medium',
  children,
  'data-testid': dataTestId,
  sx,
  ...rest
}) => {
  // Track image load errors to fall back to initials
  const [imgError, setImgError] = React.useState(false);
  const isMountedRef = React.useRef(true);
  
  // Reset error state when src changes to allow retry
  React.useEffect(() => {
    setImgError(false);
  }, [src]);
  
  // Cleanup on unmount
  React.useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);
  
  // Handle image load errors gracefully
  const handleImageError = React.useCallback(() => {
    if (isMountedRef.current) {
      setImgError(true);
    }
  }, []);
  
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Avatar', name, dataTestId);
  
  // Get size styles
  const sizeStyles = sizeMap[size];
  
  // Only use src if it's provided and hasn't errored
  const imageSrc = src && !imgError ? src : undefined;
  
  return (
    <MAvatar 
      src={imageSrc}
      onError={handleImageError}
      {...testIdProps} 
      sx={{
        ...sizeStyles,
        ...sx,
      }}
      {...rest}
    >
      {children ?? initials(name)}
    </MAvatar>
  );
};

export default Avatar;
