import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { useCardMatchingState } from '../hooks/useCardMatchingState';
import type { CardAccountOption, ReviewCard } from '../billImportTypes';

vi.mock('@apollo/client/react', () => ({
  useMutation: () => [vi.fn(), { loading: false }],
}));

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
}));

describe.sequential('useCardMatchingState', () => {
  const theme = createTheme();
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <ThemeProvider theme={theme}>{children}</ThemeProvider>
  );

  const flatSourceCard: ReviewCard = {
    reviewCardId: 'review-card-1',
    computedTotal: 120,
    declaredTotal: 120,
    totalMatchesDeclared: true,
    matchAmbiguous: false,
    matchCandidates: [],
    transactions: [
      { description: 'Netflix', amount: 50, creditDebitType: 'DEBIT', date: '2026-06-01' },
      { description: 'Uber', amount: 70, creditDebitType: 'DEBIT', date: '2026-06-02' },
    ],
  };

  const buildOptions = (options: CardAccountOption[]) => ({
    reviewCards: [flatSourceCard],
    flatSourceCard,
    isFlatAssignmentMode: true,
    cardAccountOptions: options,
    cardAccountOptionsById: new Map(options.map((option) => [option.id, option])),
    issuerOptions: [],
    step: 'card-matching' as const,
    onNavigateToTxReview: vi.fn(),
  });

  it('associates all transactions and enables review when a card is selected', () => {
    const options: CardAccountOption[] = [
      {
        id: 'card-1',
        name: 'Nubank',
        issuerId: 'issuer-1',
        issuerName: 'Nubank',
        issuerKey: 'id:issuer-1',
        maskedNumber: '•••• 1234',
        isMultiCard: false,
      },
    ];

    const { result } = renderHook(() => useCardMatchingState(buildOptions(options)), { wrapper });

    act(() => {
      result.current.selectFlatFinancialAccount('card-1');
    });

    expect(result.current.flatSelectedFinancialAccountId).toBe('card-1');
    expect(result.current.assignmentLastFour).toBe('1234');
    expect(Object.values(result.current.flatTxAssignments)).toHaveLength(2);
    expect(new Set(Object.values(result.current.flatTxAssignments)).size).toBe(1);
    expect(result.current.canReviewTransactions).toBe(true);
    expect(result.current.allCardsResolved).toBe(true);
  });

  it('pre-selects the provided card during flat-mode initialization when it is eligible', () => {
    const options: CardAccountOption[] = [
      {
        id: 'card-1',
        name: 'Nubank',
        issuerId: 'issuer-1',
        issuerName: 'Nubank',
        issuerKey: 'id:issuer-1',
        maskedNumber: '•••• 1234',
        isMultiCard: false,
      },
    ];

    const { result } = renderHook(() => useCardMatchingState(buildOptions(options)), { wrapper });

    act(() => {
      result.current.initFromJob(
        { reviewPayload: { reviewMode: 'TX_CARD_ASSIGNMENT', cards: [flatSourceCard] } },
        'card-1',
      );
    });

    expect(result.current.flatSelectedFinancialAccountId).toBe('card-1');
    expect(result.current.draftCards).toHaveLength(1);
    expect(result.current.canReviewTransactions).toBe(true);
  });

  it('ignores the provided pre-selection when the account is not eligible', () => {
    const options: CardAccountOption[] = [
      {
        id: 'card-1',
        name: 'Nubank',
        issuerId: 'issuer-1',
        issuerName: 'Nubank',
        issuerKey: 'id:issuer-1',
        maskedNumber: '•••• 1234',
        isMultiCard: false,
      },
    ];

    const { result } = renderHook(() => useCardMatchingState(buildOptions(options)), { wrapper });

    act(() => {
      result.current.initFromJob(
        { reviewPayload: { reviewMode: 'TX_CARD_ASSIGNMENT', cards: [flatSourceCard] } },
        'bank-1',
      );
    });

    expect(result.current.flatSelectedFinancialAccountId).toBeNull();
    expect(result.current.draftCards).toHaveLength(0);
  });

  it('uses a friendly label for multi-card selections without an explicit suffix', () => {
    const options: CardAccountOption[] = [
      {
        id: 'card-1',
        name: 'Family card',
        issuerId: 'issuer-1',
        issuerName: 'Bank',
        issuerKey: 'id:issuer-1',
        maskedNumber: null,
        isMultiCard: true,
      },
    ];

    const { result } = renderHook(() => useCardMatchingState(buildOptions(options)), { wrapper });

    act(() => {
      result.current.selectFlatFinancialAccount('card-1');
    });

    expect(result.current.draftBucketSummaries[0]?.label).toBe('Multiple cards');
  });

  it('uses card-not-set label for single cards without a stored suffix', () => {
    const options: CardAccountOption[] = [
      {
        id: 'card-1',
        name: 'Corporate card',
        issuerId: 'issuer-1',
        issuerName: 'Bank',
        issuerKey: 'id:issuer-1',
        maskedNumber: null,
        isMultiCard: false,
      },
    ];

    const { result } = renderHook(() => useCardMatchingState(buildOptions(options)), { wrapper });

    act(() => {
      result.current.selectFlatFinancialAccount('card-1');
    });

    expect(result.current.draftBucketSummaries[0]?.label).toBe('Card not set');
  });

  it('re-runs the default assignment when the selected card changes after a manual override', () => {
    const options: CardAccountOption[] = [
      {
        id: 'card-1',
        name: 'Card one',
        issuerId: 'issuer-1',
        issuerName: 'Bank',
        issuerKey: 'id:issuer-1',
        maskedNumber: '•••• 1234',
        isMultiCard: false,
      },
      {
        id: 'card-2',
        name: 'Card two',
        issuerId: 'issuer-1',
        issuerName: 'Bank',
        issuerKey: 'id:issuer-1',
        maskedNumber: '•••• 9876',
        isMultiCard: false,
      },
    ];

    const { result } = renderHook(() => useCardMatchingState(buildOptions(options)), { wrapper });

    act(() => {
      result.current.selectFlatFinancialAccount('card-1');
    });

    act(() => {
      result.current.setFlatTxSelections({ 0: true, 1: false });
    });

    act(() => {
      result.current.assignSelectedTransactionsToExistingAccount('card-1', '4321');
    });

    expect(result.current.draftCards).toHaveLength(2);

    act(() => {
      result.current.selectFlatFinancialAccount('card-2');
    });

    expect(result.current.assignmentLastFour).toBe('9876');
    expect(result.current.draftCards).toHaveLength(1);
    expect(new Set(Object.values(result.current.flatTxAssignments)).size).toBe(1);
  });
});
