import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { FlatAssignmentStep } from '../steps/FlatAssignmentStep';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (key === 'financeTransactions.billImport.cardMatchingStep.lastFourChip') {
        return `Last 4 ${params?.lastFour}`;
      }
      if (key === 'financeTransactions.billImport.cardMatchingStep.cardNotSet') {
        return 'Card not set';
      }
      if (key === 'financeTransactions.multipleCards') {
        return 'Multiple cards';
      }
      return key;
    },
  }),
  i18n: { language: 'en-US' },
}));

vi.mock('../../account/FinancialAccountSelector', () => ({
  FinancialAccountSelector: () => <div>selector</div>,
}));

describe.sequential('FlatAssignmentStep', () => {
  const theme = createTheme();

  const baseProps: React.ComponentProps<typeof FlatAssignmentStep> = {
    flatSourceCard: {
      reviewCardId: 'review-card-1',
      computedTotal: 120,
      declaredTotal: 120,
      totalMatchesDeclared: true,
      matchAmbiguous: false,
      matchCandidates: [],
      transactions: [{ description: 'Netflix', amount: 50, creditDebitType: 'DEBIT', date: '2026-06-01' }],
    },
    flatTxAssignments: { 0: 'draft-1' },
    flatTxSelections: { 0: false },
    draftCards: [{ draftCardId: 'draft-1', lastFourDigits: '', financialAccountId: 'card-1', allowMissingLastFour: true }],
    assignmentLastFour: '',
    selectedCardAccountId: 'card-1',
    selectedCardIsMultiCard: false,
    onSelectCardAccount: vi.fn(),
    onCreateCardAccount: vi.fn(),
    onAssignSelectedTransactionsToExistingAccount: vi.fn(),
    onSelectAllTx: vi.fn(),
    onAssignmentLastFourChange: vi.fn(),
    onSetFlatTxSelections: vi.fn(),
    onResetDraftCards: vi.fn(),
    onContinue: vi.fn(),
  };

  const renderStep = (props?: Partial<React.ComponentProps<typeof FlatAssignmentStep>>) =>
    render(
      <ThemeProvider theme={theme}>
        <FlatAssignmentStep {...baseProps} {...props} />
      </ThemeProvider>,
    );

  it('shows card-not-set label for single cards without a suffix', () => {
    renderStep();
    expect(screen.getByText('Card not set')).toBeInTheDocument();
  });

  it('shows multiple-cards label for multi-card selections without a suffix', () => {
    renderStep({ selectedCardIsMultiCard: true });
    expect(screen.getByText('Multiple cards')).toBeInTheDocument();
  });

  it('keeps the manual suffix input visible for multi-card selections', () => {
    renderStep({ selectedCardIsMultiCard: true, flatTxSelections: { 0: true } });
    expect(screen.getByLabelText('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel')).toBeInTheDocument();
  });
});
