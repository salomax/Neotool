import { memo, useMemo } from 'react';
import { Stack, alpha, useTheme } from '@mui/material';
import { Chip } from '@/shared/components/ui/primitives/Chip';

type PaletteColorKey = 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';

const PALETTE_COLOR_KEYS: readonly PaletteColorKey[] = [
  'primary',
  'secondary',
  'error',
  'warning',
  'info',
  'success',
];

function hashString(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = value.charCodeAt(i) + ((hash << 5) - hash);
  }
  return hash;
}

function pickHashedColor(value: string, colors: readonly string[]): string {
  const index = Math.abs(hashString(value) % colors.length);
  return colors[index] ?? colors[0] ?? '';
}

export interface TagChipsProps {
  tags?: readonly string[] | null;
  prefix?: string;
  size?: 'small' | 'medium';
}

export const TagChips = memo(function TagChips({ tags, prefix = '#', size = 'small' }: TagChipsProps) {
  const theme = useTheme();

  const colors = useMemo(
    () => PALETTE_COLOR_KEYS.map((key) => theme.palette[key].main),
    [theme],
  );

  if (!tags || tags.length === 0) return null;

  return (
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      {tags.map((tag) => {
        const color = pickHashedColor(tag, colors);
        return (
          <Chip
            key={tag}
            size={size}
            variant="filled"
            label={`${prefix}${tag}`}
            sx={{
              fontSize: '0.7rem',
              height: 20,
              bgcolor: alpha(color, 0.08),
              color,
              borderRadius: `${theme.shape.borderRadius}px`,
            }}
          />
        );
      })}
    </Stack>
  );
});

