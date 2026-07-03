import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BillImportDrawer } from '../BillImportDrawer';

const mockUseQuery = vi.fn();
const mockUseMutation = vi.fn(() => [vi.fn(), { loading: false }]);

vi.mock('@apollo/client/react', () => ({
  useMutation: () => mockUseMutation(),
  useQuery: (...args: unknown[]) => mockUseQuery(...args),
}));

vi.mock('../hooks/useFileUpload', () => ({
  useFileUpload: () => ({
    fileInputRef: { current: null },
    file: new File(['csv'], 'bill.csv', { type: 'text/csv' }),
    fileError: null,
    isEncryptedPdf: false,
    pdfPassword: '',
    submitting: false,
    handleFileChange: vi.fn(),
    handleDrop: vi.fn(),
    setPdfPassword: vi.fn(),
    handleSubmitFile: vi.fn(),
    reset: vi.fn(),
    resetError: vi.fn(),
  }),
}));

vi.mock('../hooks/useCardMatchingState', () => ({
  useCardMatchingState: () => ({
    reset: vi.fn(),
    initFromJob: vi.fn(),
    cardResolutions: [],
    allCardsResolved: false,
    billDateOverrides: { closingDate: '', dueDate: '' },
    flatTxAssignments: {},
    flatTxInclusions: {},
    flatTxSelections: {},
    flatSelectedFinancialAccountId: null,
    draftCards: [],
    assignmentLastFour: '',
    selectedIssuerDefaults: { issuerId: null, issuerName: null },
    canReviewTransactions: false,
    creatingTemplateCards: false,
    handleFlatReview: vi.fn(),
    getAvailableOptionsForCard: () => [],
    setCardResolutions: vi.fn(),
    setDraftCards: vi.fn(),
    setFlatTxAssignments: vi.fn(),
    setFlatTxSelections: vi.fn(),
    setFlatTxInclusions: vi.fn(),
    setAssignmentLastFour: vi.fn(),
    selectFlatFinancialAccount: vi.fn(),
    assignSelectedTransactionsToExistingAccount: vi.fn(),
  }),
}));

vi.mock('../hooks/useTxReviewState', () => ({
  useTxReviewState: () => ({
    reset: vi.fn(),
    initFromReviewCards: vi.fn(),
    txSelections: {},
    txCategorySelections: {},
    setTxSelections: vi.fn(),
    setBillDateOverrides: vi.fn(),
    updateCategory: vi.fn(),
  }),
}));

vi.mock('../hooks/useInlineCardCreation', () => ({
  useInlineCardCreation: () => ({
    reset: vi.fn(),
    effectiveCardAccountOptions: [],
    createCardForIndex: null,
    isCreateCardDrawerOpen: false,
    createDraftCardId: null,
    createForFlatSelection: false,
    openCreateCardForIndex: vi.fn(),
    openCreateCardForFlatSelection: vi.fn(),
    addInlineAccount: vi.fn(),
    close: vi.fn(),
  }),
}));

vi.mock('../../account/CreateFinancialAccountDrawer', () => ({
  CreateFinancialAccountDrawer: () => null,
}));

import { isBillDateConfirmDisabled, getBillDateValidation } from '../billImportUtils';
import dayjs from 'dayjs';

describe.sequential('BillImportDrawer footer composition', () => {
  const onClose = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseQuery.mockImplementation((_query?: unknown, options?: { skip?: boolean }) => {
      if (options?.skip) {
        return { data: undefined, loading: false, stopPolling: vi.fn(), startPolling: vi.fn() };
      }
      return {
        data: {
          financialAccounts: [],
          issuers: [],
          transactionCategories: [],
        },
        loading: false,
        stopPolling: vi.fn(),
        startPolling: vi.fn(),
      };
    });
  });

  it('renders upload footer actions outside the scrollable body', () => {
    render(<BillImportDrawer open onClose={onClose} />);

    expect(screen.getByTestId('bill-import-submit-button')).toBeInTheDocument();
    expect(screen.getByTestId('bill-import-cancel-button')).toBeInTheDocument();
  });

  it('calls onClose from the drawer-owned cancel action', async () => {
    const user = userEvent.setup();
    render(<BillImportDrawer open onClose={onClose} />);

    await user.click(screen.getByTestId('bill-import-cancel-button'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe('BillImportDrawer due date confirm gating', () => {
  it('disables confirmation when due date is missing and no fallback due day exists', () => {
    const validation = getBillDateValidation(dayjs('2026-02-07'), null, false);
    expect(isBillDateConfirmDisabled(validation)).toBe(true);
  });

  it('keeps confirmation enabled when due date can be derived from account due day', () => {
    const validation = getBillDateValidation(dayjs('2026-02-07'), null, true);
    expect(isBillDateConfirmDisabled(validation)).toBe(false);
  });
});
