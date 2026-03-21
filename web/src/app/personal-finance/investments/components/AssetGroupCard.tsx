'use client';

import React from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useTranslation } from '@/shared/i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { personalFinanceTranslations } from '../../i18n';

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
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const cardRadius = theme.custom?.radius?.button ?? theme.shape.borderRadius;
  const dividerColor = alpha(theme.palette.divider, 0.7);

  const parsedSummary = React.useMemo(() => {
    const normalized = summary.trim();
    const delimiter = normalized.includes('·') ? '·' : normalized.includes('•') ? '•' : null;
    if (!delimiter) return { countLabel: null as string | null, totalValue: normalized || null };

    const parts = normalized
      .split(delimiter)
      .map((p) => p.trim())
      .filter(Boolean);
    if (parts.length < 2) return { countLabel: null as string | null, totalValue: normalized || null };
    return {
      countLabel: parts[0] ?? null,
      totalValue: parts.slice(1).join(` ${delimiter} `) || null,
    };
  }, [summary]);

  return (
    <Accordion
      expanded={expanded}
      onChange={(_, isExpandedNext) => onToggle(isExpandedNext)}
      elevation={0}
      sx={{
        mb: 0,
        overflow: 'hidden',
        bgcolor: 'transparent',
        borderRadius: `${cardRadius}px`,
        transition: 'none',
        '&:before': { display: 'none' },
        '&.Mui-expanded': {
          margin: 0,
          borderRadius: `${cardRadius}px`,
        },
      }}
    >
      <AccordionSummary
        expandIcon={<ExpandMoreIcon />}
        sx={{
          px: 2,
          py: 1.75,
          minHeight: 64,
          border: `1px solid ${dividerColor}`,
          bgcolor: alpha(theme.palette.text.primary, 0.02),
          borderRadius: `${cardRadius}px`,
          overflow: 'hidden',
          transition: 'none',
          '&.Mui-expanded': {
                      borderBottom: 'none',

            minHeight: 64,
            bgcolor: alpha(theme.palette.text.primary, 0.01),
            borderBottomLeftRadius: 0,
            borderBottomRightRadius: 0,
          },
          '&:hover': {
            bgcolor: alpha(theme.palette.text.primary, 0.03),
          },
          '&.Mui-expanded:hover': {
            bgcolor: alpha(theme.palette.text.primary, 0.02),
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
                  width: 40,
                  height: 40,
                  borderRadius: `${cardRadius}px`,
                  bgcolor: alpha(theme.palette.primary.main, 0.08),
                  color: 'primary.main',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flex: '0 0 auto',
                  '& .MuiSvgIcon-root': {
                    fontSize: 22,
                  },
                }}
              >
                {leadingIcon}
              </Box>
            )}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
              <Typography variant="h6" fontWeight={800} noWrap sx={{ lineHeight: 1.1, fontSize: '1.2rem' }}>
                {title}
              </Typography>
              {parsedSummary.countLabel ? (
                <Chip
                  label={parsedSummary.countLabel}
                  size="small"
                  sx={{
                    height: 18,
                    fontSize: '.7rem',
                    fontWeight: 400,
                    bgcolor: alpha(theme.palette.primary.main, 0.08),
                    color: 'text.primary',
                    '& .MuiChip-label': { px: 1 },
                  }}
                />
              ) : null}
            </Box>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flex: '0 0 auto', px: 5 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', minWidth: 0 }}>
              <Label sx={{ mb: 0, lineHeight: 1 }}>{t('investments.table.total')}</Label>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, minWidth: 0 }}>
                {summaryAdornment}
                <Typography variant="subtitle1" fontWeight={700} noWrap sx={{ lineHeight: 2 }}>
                  {parsedSummary.totalValue ?? '—'}
                </Typography>
              </Box>
            </Box>
            {headerAction ? <Box sx={{ flex: '0 0 auto' }}>{headerAction}</Box> : null}
          </Box>
        </Box>
      </AccordionSummary>
      <AccordionDetails
        sx={{
          pt: 0,
          px: 0,
          pb: 0,
          bgcolor: alpha(theme.palette.text.primary, 0.01),
          borderBottomLeftRadius: `${cardRadius}px`,
          borderBottomRightRadius: `${cardRadius}px`,
          overflow: 'hidden',
        }}
      >
        <Box
          sx={{
            border: `1px solid ${dividerColor}`,
            borderRadius: `0 0 ${cardRadius}px ${cardRadius}px`,
            overflow: 'hidden',
            bgcolor: 'background.paper',
            '& .MuiTableHead-root .MuiTableRow-root': {
              bgcolor: alpha(theme.palette.primary.main, 0.04),
            },
            '& .MuiTableHead-root .MuiTableCell-root': {
              bgcolor: 'transparent',
            },
            '& .MuiTableBody-root .MuiTableRow-root': {
              bgcolor: 'background.paper',
            },
            '& .MuiTableCell-root': {
              borderColor: dividerColor,
            },
          }}
        >
          {children}
        </Box>
      </AccordionDetails>
    </Accordion>
  );
}
