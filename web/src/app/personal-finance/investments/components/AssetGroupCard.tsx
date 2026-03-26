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
import i18n from 'i18next';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { AllocationInline, PercentageGain } from '@/shared/components/ui/data-display';

interface AssetGroupCardProps {
  title: string;
  summary: string;
  expanded: boolean;
  onToggle: (expanded: boolean) => void;
  leadingIcon?: React.ReactNode;
  /** Rendered after the summary (e.g. warning icon when group has estimated values). */
  summaryAdornment?: React.ReactNode;
  /** Optional header action rendered on the right side (e.g. "Record fund"). */
  /** Percentage gain to show below total amount. */
  gainPercent?: number | null;
  allocationPercent?: number | null;
  appliedLabel?: string;
  appliedValue?: React.ReactNode;
  headerAction?: React.ReactNode;
  children: React.ReactNode;
}

export function AssetGroupCard({
  title,
  summary,
  expanded,
  onToggle,
  summaryAdornment,
  gainPercent,
  allocationPercent,
  appliedLabel,
  appliedValue,
  headerAction,
  children,
}: AssetGroupCardProps) {
  const theme = useTheme();
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

  const allocationLabel =
    typeof allocationPercent === 'number' && Number.isFinite(allocationPercent)
      ? new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 0 }).format(allocationPercent)
      : null;

  return (
    <Accordion
      expanded={expanded}
      onChange={(_, isExpandedNext) => onToggle(isExpandedNext)}
      elevation={0}
      sx={{
        overflow: 'visible',
        bgcolor: 'transparent',
        borderRadius: 0,
        transition: 'none',
        mb: 2,
        '&:before': { display: 'none' },
        '&.Mui-expanded': {
          margin: 0,
          marginBottom: 5,
          borderRadius: 0,
        },
      }}
    >
      <AccordionSummary
        expandIcon={<ExpandMoreIcon />}
        sx={{
          flexDirection: 'row-reverse',
          px: 2,
          pt: 1.75,
          pb: 0.75,
          minHeight: 64,
          border: 'none',
          borderBottom: `1px solid ${dividerColor}`,
          bgcolor: 'transparent',
          borderRadius: 0,
          overflow: 'visible',
          transition: 'none',
          '&.Mui-expanded': {
            minHeight: 64,
            bgcolor: 'transparent',
            borderRadius: 0,
          },
          '&:hover': {
            bgcolor: alpha(theme.palette.primary.main, 0.05),
          },
          '&.Mui-expanded:hover': {
            bgcolor: alpha(theme.palette.primary.main, 0.05),
          },
          '& .MuiAccordionSummary-content': {
            margin: 0,
            alignItems: 'center',
          },
          '& .MuiAccordionSummary-content.Mui-expanded': {
            margin: 0,
          },
          '& .MuiAccordionSummary-expandIconWrapper': {
            mr: 2,
            ml: 0,
            color: 'text.secondary',
            '&.Mui-expanded': {
              color: 'text.secondary',
            },
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0, width: '100%' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0, flex: '1 1 auto' }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ lineHeight: 1.1, fontSize: '1.2rem' }}>
                {title}
              </Typography>
              {parsedSummary.countLabel ? (
                allocationLabel ? (
                  <Box sx={{ mt: 0.25, minWidth: 0 }}>
                    <AllocationInline
                      value={allocationPercent}
                      label={`${allocationLabel} · ${parsedSummary.countLabel}`}
                      barWidth={100}
                      sx={{ minWidth: 0 }}
                      textSx={{ lineHeight: 1.2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                    />
                  </Box>
                ) : (
                  <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2, mt: 0.25 }}>
                    {parsedSummary.countLabel}
                  </Typography>
                )
              ) : null}
            </Box>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flex: '0 0 auto', px: 5 }}>
            {headerAction ? <Box sx={{ flex: '0 0 auto' }}>{headerAction}</Box> : null}
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 6, minWidth: 0 }}>
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', minWidth: 0 }}>
                <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2, textTransform: 'uppercase', fontSize: '0.65rem' }}>
                  {appliedLabel ?? 'Valor aplicado'}
                </Typography>
                <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ lineHeight: 1.2, mt: 0.25 }}>
                  {appliedValue ?? '—'}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', minWidth: 0 }}>
                <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2, textTransform: 'uppercase', fontSize: '0.65rem' }}>
                  POSIÇÃO ATUAL
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, minWidth: 0, mt: 0.25 }}>
                  {summaryAdornment}
                  <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ lineHeight: 1.2 }}>
                    {parsedSummary.totalValue ?? '—'}
                  </Typography>
                </Box>
                {typeof gainPercent === 'number' && Number.isFinite(gainPercent) ? (
                  <PercentageGain value={gainPercent} locale={i18n.language} sx={{ mt: 0.25 }} />
                ) : null}
              </Box>
            </Box>
          </Box>
        </Box>
      </AccordionSummary>
      <AccordionDetails
        sx={{
          pt: 0,
          px: 0,
          pb: 0,
          bgcolor: 'transparent',
          overflow: 'visible',
        }}
      >
        {children}
      </AccordionDetails>
    </Accordion>
  );
}
