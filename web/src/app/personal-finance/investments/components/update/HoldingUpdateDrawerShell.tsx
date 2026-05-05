'use client';

import * as React from 'react';
import LaunchIcon from '@mui/icons-material/Launch';
import { Box, Divider, IconButton } from '@mui/material';
import Link from 'next/link';
import type { ErrorLike as ApolloError } from '@apollo/client';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { ErrorAlert } from '@/shared/components/ui/feedback';
import { FormStack } from '@/shared/components/ui/forms/components';
import { HoldingDetailHeader } from '../HoldingDetailHeader';
import type { HoldingDetailHeaderModel } from '../holdingDetailHeaderPresenter';
import { DirectionToggle } from './DirectionToggle';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';

interface HoldingUpdateDrawerShellProps {
  open: boolean;
  onClose: () => void;
  headerModel: HoldingDetailHeaderModel;
  holdingDetailPath: string;
  openHoldingDetailAriaLabel: string;
  title: string;
  formId: string;
  direction: InvestmentTransactionDirection;
  directionLocked?: boolean;
  inflowLabel: string;
  outflowLabel: string;
  onDirectionChange: (direction: InvestmentTransactionDirection) => void;
  onSubmit: (event: React.FormEvent) => void;
  mutationError: ApolloError | undefined;
  isSubmitting: boolean;
  cancelText: string;
  submitText: string;
  submittingText: string;
  children: React.ReactNode;
}

export function HoldingUpdateDrawerShell({
  open,
  onClose,
  headerModel,
  holdingDetailPath,
  openHoldingDetailAriaLabel,
  title,
  formId,
  direction,
  directionLocked = false,
  inflowLabel,
  outflowLabel,
  onDirectionChange,
  onSubmit,
  mutationError,
  isSubmitting,
  cancelText,
  submitText,
  submittingText,
  children,
}: HoldingUpdateDrawerShellProps) {
  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header
        title={title}
        headerActions={(
          <IconButton
            component={Link}
            href={holdingDetailPath}
            target="_blank"
            rel="noopener noreferrer"
            size="small"
            aria-label={openHoldingDetailAriaLabel}
          >
            <LaunchIcon fontSize="small" />
          </IconButton>
        )}
      />
      <Drawer.Body>
        <form id={formId} onSubmit={onSubmit}>
          <FormStack>
            <HoldingDetailHeader model={headerModel} hideMetrics />

            <Box sx={directionLocked ? { pointerEvents: 'none', opacity: 0.5 } : undefined}>
              <DirectionToggle
                value={direction}
                inflowLabel={inflowLabel}
                outflowLabel={outflowLabel}
                onChange={onDirectionChange}
                disabled={directionLocked}
              />
            </Box>

            <ErrorAlert error={mutationError} />

            {children}

            <Divider />

            <Drawer.ActionButtons
              cancelText={cancelText}
              submitText={submitText}
              submittingText={submittingText}
              onCancel={onClose}
              submitForm={formId}
              submitDisabled={isSubmitting}
              cancelDisabled={isSubmitting}
              submitting={isSubmitting}
            />
          </FormStack>
        </form>
      </Drawer.Body>
    </Drawer>
  );
}
