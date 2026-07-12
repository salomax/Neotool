import type { ReactElement } from 'react';
import { describe, expect, it, vi, afterEach } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import {
  CreditCardNetwork,
  FinancialAccountSubtype,
  FinancialAccountType,
  type FinancialAccount,
} from '@/lib/graphql/types/__generated__/graphql';
import { CardBucketMatchingStep } from '../steps/CardBucketMatchingStep';
import type { CardAccountOption, CardResolution, ReviewCard } from '../billImportTypes';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'financeTransactions.billImport.cardMatchingStep.descriptionSingle':
          'Associate each card found in the statement with an existing account.',
        'financeTransactions.billImport.cardMatchingStep.descriptionMultipleTitle':
          `We identified ${params?.count} different cards in the statement.`,
        'financeTransactions.billImport.cardMatchingStep.descriptionMultipleBody':
          'Associate each identified card with a registered card or create new cards.',
        'financeTransactions.billImport.cardMatchingStep.status.associated': 'Associated',
        'financeTransactions.billImport.cardMatchingStep.status.pending': 'Pending',
        'financeTransactions.billImport.cardMatchingStep.cardAccountLabel': 'Card account',
        'financeTransactions.billImport.cardMatchingStep.cardAccountPlaceholder': 'Select...',
        'financeTransactions.billImport.cardMatchingStep.createNewCard': 'Create new card',
        'financeTransactions.billImport.cardMatchingStep.txSummary': '{{count}} transaction(s)',
        'financeTransactions.billImport.cardMatchingStep.unnamedCard': 'Card {{index}}',
        'financeTransactions.multipleCards': 'Multiple cards',
        'financeTransactions.selector.noAccountsFound': 'No accounts found',
      };
      return translations[key] ?? key;
    },
  }),
}));

vi.mock('@/shared/components/ui/icons/CardNetworkIcon', () => ({
  CardNetworkIcon: ({ network }: { network?: string | null }) => (
    <span data-testid="card-network-icon">{network ?? 'none'}</span>
  ),
}));

function renderStep(ui: ReactElement) {
  return render(<ThemeProvider theme={createTheme()}>{ui}</ThemeProvider>);
}

afterEach(() => {
  cleanup();
});

function makeAccount(overrides: Partial<FinancialAccount>): FinancialAccount {
  return {
    id: 'card-1',
    name: 'Nubank Visa',
    type: FinancialAccountType.Credit,
    subtype: FinancialAccountSubtype.CreditCard,
    themeColor: null,
    issuer: {
      id: 'issuer-1',
      name: 'Nubank',
      logoAsset: null,
    },
    issuerName: null,
    cardData: {
      maskedNumber: '•••• 1234',
      isMultiCard: false,
      dueDay: 10,
      closingDay: 3,
      cardNetwork: CreditCardNetwork.Visa,
      cardLevel: null,
      creditLimit: null,
    },
    currencyCode: 'BRL',
    includeInNetWorth: true,
    includeInDashboard: true,
    currentBalance: null,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  } as FinancialAccount;
}

function makeCardOption(overrides: Partial<CardAccountOption> = {}): CardAccountOption {
  return {
    id: 'card-1',
    name: 'Nubank Visa',
    issuerId: 'issuer-1',
    issuerName: 'Nubank',
    issuerKey: 'id:issuer-1',
    maskedNumber: '•••• 1234',
    isMultiCard: false,
    dueDay: 10,
    ...overrides,
  };
}

function makeReviewCard(overrides: Partial<ReviewCard> = {}): ReviewCard {
  return {
    reviewCardId: 'review-card-1',
    lastFourDigits: '1234',
    computedTotal: 100,
    declaredTotal: 100,
    totalMatchesDeclared: true,
    matchAmbiguous: false,
    matchCandidates: [],
    transactions: [{ description: 'Store', amount: 100, creditDebitType: 'DEBIT', date: '2026-06-01' }],
    ...overrides,
  };
}

function renderCardBucketMatchingStep(overrides: {
  reviewCards?: ReviewCard[];
  cardResolutions?: CardResolution[];
  getAvailableOptionsForCard?: (card: ReviewCard) => CardAccountOption[];
  cardAccountOptionsById?: Map<string, CardAccountOption>;
  financialAccountsById?: Map<string, FinancialAccount>;
  selectedIssuerKey?: string | null;
  onResolveCard?: (ci: number, accountId: string | null) => void;
  onCreateCardForIndex?: (ci: number) => void;
} = {}) {
  const cardOptions = [makeCardOption(), makeCardOption({ id: 'card-2', name: 'Itaú Mastercard' })];
  const cardAccountOptionsById =
    overrides.cardAccountOptionsById ?? new Map(cardOptions.map((option) => [option.id, option]));
  const financialAccountsById =
    overrides.financialAccountsById ??
    new Map(
      [makeAccount({ id: 'card-1' }), makeAccount({ id: 'card-2', name: 'Itaú Mastercard' })].map(
        (account) => [account.id, account],
      ),
    );

  return renderStep(
    <CardBucketMatchingStep
      reviewCards={overrides.reviewCards ?? [makeReviewCard()]}
      cardResolutions={
        overrides.cardResolutions ?? [{ reviewCardId: 'review-card-1', financialAccountId: null }]
      }
      cardAccountOptionsById={cardAccountOptionsById}
      financialAccountsById={financialAccountsById}
      selectedIssuerKey={overrides.selectedIssuerKey ?? null}
      getAvailableOptionsForCard={overrides.getAvailableOptionsForCard ?? (() => cardOptions)}
      onResolveCard={overrides.onResolveCard ?? vi.fn()}
      onCreateCardForIndex={overrides.onCreateCardForIndex ?? vi.fn()}
    />,
  );
}

describe.sequential('CardBucketMatchingStep', () => {
  it('renders single-card helper copy when only one card is identified', () => {
    renderCardBucketMatchingStep();

    expect(
      screen.getByText('Associate each card found in the statement with an existing account.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/We identified \d+ different cards/)).not.toBeInTheDocument();
  });

  it('renders multi-card helper copy with the detected count', () => {
    renderCardBucketMatchingStep({
      reviewCards: [
        makeReviewCard({ reviewCardId: 'review-card-1', lastFourDigits: '1234' }),
        makeReviewCard({ reviewCardId: 'review-card-2', lastFourDigits: '5678' }),
      ],
      cardResolutions: [
        { reviewCardId: 'review-card-1', financialAccountId: null },
        { reviewCardId: 'review-card-2', financialAccountId: null },
      ],
    });

    expect(screen.getByText('We identified 2 different cards in the statement.')).toBeInTheDocument();
    expect(
      screen.getByText('Associate each identified card with a registered card or create new cards.'),
    ).toBeInTheDocument();
  });

  it('renders the shared AccountSelect instead of a plain select', () => {
    renderCardBucketMatchingStep();

    expect(screen.getByTestId('card-bucket-account-select-0')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Open$/i })).toBeInTheDocument();
  });

  it('renders create-new-card as a link-style action', () => {
    renderCardBucketMatchingStep();

    const createLink = screen.getByTestId('card-bucket-create-card-link-0');
    expect(createLink.tagName).toBe('BUTTON');
    expect(createLink).toHaveTextContent('Create new card');
  });

  it('calls onCreateCardForIndex when the link action is clicked', () => {
    const onCreateCardForIndex = vi.fn();
    renderCardBucketMatchingStep({ onCreateCardForIndex });

    fireEvent.click(screen.getByTestId('card-bucket-create-card-link-0'));

    expect(onCreateCardForIndex).toHaveBeenCalledWith(0);
  });

  it('uses issuer-filtered options from getAvailableOptionsForCard', () => {
    const filteredOptions = [makeCardOption({ id: 'card-1', name: 'Nubank Visa' })];
    renderCardBucketMatchingStep({
      getAvailableOptionsForCard: () => filteredOptions,
    });

    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(screen.getByTestId('account-select-option-card-1')).toBeInTheDocument();
    expect(screen.queryByTestId('account-select-option-card-2')).not.toBeInTheDocument();
  });

  it('updates card resolution through AccountSelect and preserves issuer guard', () => {
    const onResolveCard = vi.fn();
    renderCardBucketMatchingStep({
      selectedIssuerKey: 'id:issuer-1',
      onResolveCard,
    });

    const combobox = screen.getByRole('combobox');
    fireEvent.mouseDown(combobox);
    fireEvent.click(within(screen.getByTestId('account-select-option-card-1')).getByText('Nubank Visa'));

    expect(onResolveCard).toHaveBeenCalledWith(0, 'card-1');
  });

  it('blocks issuer-incompatible selections when an issuer key is already selected', () => {
    const onResolveCard = vi.fn();
    renderCardBucketMatchingStep({
      selectedIssuerKey: 'id:issuer-1',
      getAvailableOptionsForCard: () => [
        makeCardOption({ id: 'card-1', issuerKey: 'id:issuer-1' }),
        makeCardOption({ id: 'card-2', issuerKey: 'id:issuer-2', name: 'Other bank' }),
      ],
      financialAccountsById: new Map([
        ['card-1', makeAccount({ id: 'card-1' })],
        [
          'card-2',
          makeAccount({
            id: 'card-2',
            name: 'Other bank',
            issuer: { id: 'issuer-2', name: 'Other', logoAsset: null } as FinancialAccount['issuer'],
          }),
        ],
      ]),
      cardAccountOptionsById: new Map([
        ['card-1', makeCardOption({ id: 'card-1', issuerKey: 'id:issuer-1' })],
        ['card-2', makeCardOption({ id: 'card-2', issuerKey: 'id:issuer-2', name: 'Other bank' })],
      ]),
      onResolveCard,
    });

    const combobox = screen.getByRole('combobox');
    fireEvent.mouseDown(combobox);
    fireEvent.click(within(screen.getByTestId('account-select-option-card-2')).getByText('Other bank'));

    expect(onResolveCard).not.toHaveBeenCalled();
  });
});
