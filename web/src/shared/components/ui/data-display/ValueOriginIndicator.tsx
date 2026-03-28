'use client';

import * as React from 'react';
import { alpha, Box, ButtonBase, Tooltip, Typography, useTheme } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import type { ValueOriginLabel } from '@/shared/utils/holdingValueOrigin';

export interface ValueOriginIndicatorProps {
  origin: ValueOriginLabel;
  title: React.ReactNode;
  description?: React.ReactNode;
  ariaLabel?: string;
  offsetPx?: number;
  sizePx?: number;
  sx?: SxProps<Theme>;
  bulletSx?: SxProps<Theme>;
}

export function ValueOriginIndicator({
  origin: _origin,
  title,
  description,
  ariaLabel,
  offsetPx = 18,
  sizePx = 6,
  sx,
  bulletSx,
}: ValueOriginIndicatorProps) {
  const theme = useTheme();

  const { baseColor, hoverColor } = React.useMemo(() => {
    return { baseColor: theme.palette.warning.main, hoverColor: theme.palette.warning.dark };
  }, [theme]);

  const tooltipContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, maxWidth: 320 }}>
      <Typography variant="subtitle2" fontWeight={600} sx={{ lineHeight: 1.25 }}>
        {title}
      </Typography>
      {description ? (
        <Typography variant="caption" sx={{ lineHeight: 1.25, color: alpha(theme.palette.common.white, 0.9) }}>
          {description}
        </Typography>
      ) : null}
    </Box>
  );

  return (
    <Box sx={{ position: 'absolute', right: -offsetPx, top: '50%', transform: 'translateY(-50%)', ...(sx as any) }}>
      <Tooltip title={tooltipContent} arrow enterDelay={250}>
        <ButtonBase
          aria-label={ariaLabel}
          sx={{
            display: 'grid',
            placeItems: 'center',
            width: sizePx + 12,
            height: sizePx + 12,
            borderRadius: 999,
            '&:hover .ValueOriginIndicator-bullet': {
              bgcolor: hoverColor,
              boxShadow: `0 0 0 4px ${alpha(baseColor, 0.18)}`,
            },
            '&:focus-visible .ValueOriginIndicator-bullet': {
              boxShadow: `0 0 0 4px ${alpha(baseColor, 0.22)}`,
            },
          }}
        >
          <Box
            className="ValueOriginIndicator-bullet"
            sx={{
              width: sizePx,
              height: sizePx,
              borderRadius: 999,
              bgcolor: baseColor,
              transition: theme.transitions.create(['background-color', 'box-shadow'], {
                duration: theme.transitions.duration.shortest,
              }),
              boxShadow: `0 0 0 0px ${alpha(baseColor, 0.18)}`,
              ...(bulletSx as any),
            }}
          />
        </ButtonBase>
      </Tooltip>
    </Box>
  );
}

export default ValueOriginIndicator;
