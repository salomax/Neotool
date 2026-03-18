'use client';

import React from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

interface AssetGroupCardProps {
  title: string;
  summary: string;
  expanded: boolean;
  onToggle: (expanded: boolean) => void;
  leadingIcon?: React.ReactNode;
  /** Rendered after the summary (e.g. warning icon when group has estimated values). */
  summaryAdornment?: React.ReactNode;
  /** Optional header action rendered on the right side (e.g. "Record fund"). */
  headerAction?: React.ReactNode;
  children: React.ReactNode;
}

export function AssetGroupCard({
  title,
  summary,
  expanded,
  onToggle,
  leadingIcon,
  summaryAdornment,
  headerAction,
  children,
}: AssetGroupCardProps) {
  const theme = useTheme();
  const cardRadius = theme.custom?.radius?.button ?? theme.shape.borderRadius;

  return (
    <Accordion
      expanded={expanded}
      onChange={(_, isExpandedNext) => onToggle(isExpandedNext)}
      elevation={0}
      sx={{
        mb: 2,
        border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
        borderRadius: `${cardRadius}px`,
        overflow: 'hidden',
        bgcolor: 'background.paper',
        transition: 'none',
        '&:before': { display: 'none' },
        '&.Mui-expanded': {
          margin: 0,
          mb: 2,
          borderRadius: `${cardRadius}px`,
        },
      }}
    >
      <AccordionSummary
        expandIcon={<ExpandMoreIcon />}
        sx={{
          px: 2,
          py: 1.5,
          minHeight: 56,
          bgcolor: alpha(theme.palette.primary.main, 0.04),
          transition: 'background-color 0.18s ease',
          '&.Mui-expanded': {
            minHeight: 56,
          },
          '&:hover': {
            bgcolor: alpha(theme.palette.primary.main, 0.06),
          },
          '& .MuiAccordionSummary-content': {
            margin: 0,
            alignItems: 'center',
          },
          '& .MuiAccordionSummary-content.Mui-expanded': {
            margin: 0,
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0, width: '100%' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0, flex: '1 1 auto' }}>
            {leadingIcon && (
              <Box
                sx={{
                  width: 36,
                  height: 36,
                  borderRadius: `${cardRadius}px`,
                  bgcolor: alpha(theme.palette.primary.main, 0.1),
                  color: 'primary.main',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flex: '0 0 auto',
                }}
              >
                {leadingIcon}
              </Box>
            )}
            <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <Typography variant="subtitle1" fontWeight={700} noWrap>
                {title}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <Typography variant="body2" color="text.secondary" noWrap>
                  {summary}
                </Typography>
                {summaryAdornment}
              </Box>
            </Box>
          </Box>

          {headerAction ? <Box sx={{ flex: '0 0 auto' }}>{headerAction}</Box> : null}
        </Box>
      </AccordionSummary>
      <AccordionDetails sx={{ pt: 0, px: 0 }}>{children}</AccordionDetails>
    </Accordion>
  );
}
