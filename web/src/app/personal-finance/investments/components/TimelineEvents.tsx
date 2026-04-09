'use client';

import * as React from 'react';
import { Box, Paper, Stack, Typography } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { EmptyState } from '@/shared/components/ui/feedback';
import { Label } from '@/shared/components/ui/primitives/Label';

export interface TimelineEventsItem {
  id: string;
  title: string;
  tradeDateLabel: string;
  settlementDateLabel?: string | null;
  amount: number;
  netAmount?: number | null;
  incomeTax?: number | null;
  iof?: number | null;
  direction: string;
}

export interface TimelineEventsProps {
  title: string;
  emptyTitle: string;
  emptyDescription: string;
  icon?: React.ReactNode;
  locale: string;
  currencyCode: string;
  items: readonly TimelineEventsItem[];
  formatCurrency: (value: number) => string;
  netAmountText: (formattedValue: string) => string;
  incomeTaxText: (formattedValue: string) => string;
  iofText: (formattedValue: string) => string;
  /** When set, shown before tax/net lines when `settlementDateLabel` is present on an item. */
  settlementDateText?: (settlementDateLabel: string) => string;
  sx?: SxProps<Theme>;
}

export const TimelineEvents: React.FC<TimelineEventsProps> = ({
  title,
  emptyTitle,
  emptyDescription,
  icon,
  locale,
  currencyCode,
  items,
  formatCurrency,
  netAmountText,
  incomeTaxText,
  iofText,
  settlementDateText,
  sx,
}) => {
  if (items.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <Box sx={sx}>
      <Box sx={{ display: 'flex', alignItems: { xs: 'flex-start', md: 'center' }, justifyContent: 'space-between', gap: 2, mb: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {icon}
          <Label sx={{ fontSize: '0.8rem', fontWeight: 500, mb: 0 }}>{title}</Label>
        </Box>
      </Box>

      <Box sx={{ position: 'relative' }}>
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            left: { xs: 8, md: '50%' },
            width: 2,
            bgcolor: 'divider',
            transform: 'translateX(-1px)',
            pointerEvents: 'none',
          }}
        />

        <Stack spacing={1.5}>
          {items.map((item) => {
          const isOutflow = item.direction === 'OUTFLOW';
          const signedAmount = isOutflow ? -item.amount : item.amount;
          const moneyParts = [
            item.netAmount != null ? netAmountText(formatCurrency(item.netAmount)) : null,
            item.incomeTax != null ? incomeTaxText(formatCurrency(item.incomeTax)) : null,
            item.iof != null ? iofText(formatCurrency(item.iof)) : null,
          ]
            .filter(Boolean)
            .join(' · ');

          const settlementPart =
            item.settlementDateLabel && settlementDateText
              ? settlementDateText(item.settlementDateLabel)
              : null;

          const detailText = [settlementPart, moneyParts || null].filter(Boolean).join(' · ');

          const leftCard = (
            <Paper
              variant="elevation"
              elevation={0}
              sx={{
                p: 2,
                borderRadius: 2,
                minWidth: 0,
                width: '100%',
                maxWidth: 420,
                justifySelf: 'end',
              }}
            >
              <Typography
                variant="caption"
                color="text.secondary"
                noWrap
                sx={{ minWidth: 0, width: '100%', textAlign: 'right', display: 'block' }}
              >
                {item.tradeDateLabel}
              </Typography>
              <Label sx={{ mb: 0.5, textAlign: 'right' }}>{item.title}</Label>
              {detailText ? (
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: 'block', textAlign: 'right' }}>
                  {detailText}
                </Typography>
              ) : null}
              <CurrencyAmountParts
                value={signedAmount}
                currency={currencyCode}
                locale={locale}
                variant="h6"
                fontWeight={500}
                noWrap
                sx={{ mt: 1, display: 'block', textAlign: 'right', lineHeight: 1.1 }}
              />
            </Paper>
          );

          const rightCard = (
            <Paper
              variant="elevation"
              elevation={0}
              sx={{
                p: 2,
                borderRadius: 2,
                minWidth: 0,
                width: '100%',
                maxWidth: 420,
                justifySelf: 'start',
              }}
            >
              <Typography
                variant="caption"
                color="text.secondary"
                noWrap
                sx={{ minWidth: 0, width: '100%', textAlign: 'left', display: 'block' }}
              >
                {item.tradeDateLabel}
              </Typography>
              <Label sx={{ mb: 0.5, textAlign: 'left' }}>{item.title}</Label>
              {detailText ? (
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.75, display: 'block', textAlign: 'left' }}>
                  {detailText}
                </Typography>
              ) : null}
              <CurrencyAmountParts
                value={signedAmount}
                currency={currencyCode}
                locale={locale}
                variant="h6"
                fontWeight={500}
                noWrap
                sx={{ mt: 1, display: 'block', textAlign: 'left', lineHeight: 1.1 }}
              />
            </Paper>
          );

          return (
            <Box
              key={item.id}
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '16px minmax(0, 1fr)', md: 'minmax(0, 1fr) 32px minmax(0, 1fr)' },
                columnGap: 1.5,
                alignItems: 'stretch',
              }}
            >
              <Box sx={{ display: { xs: 'none', md: 'block' }, minWidth: 0 }}>
                {!isOutflow ? leftCard : null}
              </Box>

              <Box sx={{ position: 'relative', minHeight: '100%' }}>
                <Box
                  sx={{
                    position: 'absolute',
                    left: '50%',
                    top: '50%',
                    transform: 'translate(-50%, -50%)',
                    width: 8,
                    height: 8,
                    borderRadius: 999,
                    bgcolor: 'primary.main',
                  }}
                />
              </Box>

              <Box sx={{ display: { xs: 'none', md: 'block' }, minWidth: 0 }}>
                {isOutflow ? rightCard : null}
              </Box>

              <Box sx={{ display: { xs: 'block', md: 'none' }, minWidth: 0 }}>
                {rightCard}
              </Box>
            </Box>
          );
          })}
        </Stack>
      </Box>
    </Box>
  );
};

export default TimelineEvents;
