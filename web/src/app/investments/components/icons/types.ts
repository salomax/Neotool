
export type IconSize = "sm" | "md" | "lg";

export interface AnimatedIconProps {
  size?: IconSize;
  className?: string;
}

export const ICON_SIZES: Record<IconSize, { scale: number; height: number }> = {
  sm: { scale: 0.7, height: 100 },
  md: { scale: 1, height: 140 },
  lg: { scale: 1.3, height: 180 },
};
