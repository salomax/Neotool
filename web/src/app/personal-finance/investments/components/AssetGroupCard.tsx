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
  children: React.ReactNode;
}

export function AssetGroupCard({ title, summary, expanded, onToggle, leadingIcon, children }: AssetGroupCardProps) {
  const theme = useTheme();

  return (
    <Accordion
      expanded={expanded}
      onChange={(_, isExpandedNext) => onToggle(isExpandedNext)}
      elevation={0}
      sx={{
        border: '1px solid',
        borderColor: expanded ? 'primary.main' : alpha(theme.palette.divider, 0.7),
        borderRadius: 2,
        overflow: 'hidden',
        bgcolor: 'background.paper',
        boxShadow: expanded ? theme.shadows[3] : theme.shadows[1],
        transition: 'border-color 0.2s ease-in-out, box-shadow 0.2s ease-in-out',
        '&:before': { display: 'none' },
        '&:hover': {
          borderColor: 'primary.main',
          boxShadow: theme.shadows[3],
        },
      }}
    >
      <AccordionSummary
        expandIcon={<ExpandMoreIcon />}
        sx={{
          px: 2,
          py: 1.5,
          bgcolor: expanded ? 'action.selected' : 'background.paper',
          transition: 'background-color 0.2s ease-in-out',
          '&:hover': {
            bgcolor: expanded ? 'action.selected' : 'action.hover',
          },
          '& .MuiAccordionSummary-content': {
            my: 0,
            alignItems: 'center',
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>
          {leadingIcon && (
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: 2,
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
            <Typography variant="body2" color="text.secondary" noWrap>
              {summary}
            </Typography>
          </Box>
        </Box>
      </AccordionSummary>
      <AccordionDetails sx={{ pt: 0, px: 0 }}>{children}</AccordionDetails>
    </Accordion>
  );
}

