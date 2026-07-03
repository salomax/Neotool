import React from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { describe, it, expect, vi } from 'vitest';
import { TxReviewStep } from '../steps/TxReviewStep';
import type { BillDateOverrides } from '../billImportTypes';

const translations: Record<string, string> = {
  'financeTransactions.billImport.txReviewStep.description': 'Review the transactions before confirming.',
  'financeTransactions.billImport.txReviewStep.setupRequired': 'Setup required',
  'financeTransactions.billImport.txReviewStep.billDatesTitle': 'Bill dates',
  'financeTransactions.billImport.txReviewStep.billDatesDescription': 'Review dates',
  'financeTransactions.billImport.txReviewStep.closingDateLabel': 'Closing date',
  'financeTransactions.billImport.txReviewStep.closingDateHint': 'Closing hint',
  'financeTransactions.billImport.txReviewStep.dueDateLabel': 'Due date',
  'financeTransactions.billImport.txReviewStep.dueDateHint': 'Due hint',
  'financeTransactions.billImport.txReviewStep.dueDateRequired': 'Due date is required',
  'financeTransactions.billImport.txReviewStep.confirming': 'Confirming...',
  'financeTransactions.billImport.txReviewStep.confirmButton': 'Confirm {{count}} transaction(s)',
  'financeTransactions.billImport.common.back': 'Back',
  'datePicker.errors.invalidDate': 'Invalid date',
};

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => {
      const template = translations[key] ?? key;
      return options
        ? template.replace(/\{\{(\w+)\}\}/g, (_, token) => String(options[token] ?? ''))
        : template;
    },
  }),
}));

vi.mock('@/shared/components/ui/primitives/DateTimePicker', () => ({
  DateTimePicker: ({
    label,
    value,
    onChange,
    errorMessage,
    helperText,
    required,
  }: {
    label: string;
    value?: Dayjs | null;
    onChange?: (value: Dayjs | null) => void;
    errorMessage?: string;
    helperText?: string;
    required?: boolean;
  }) => (
    <div data-testid={`picker-${label}`}>
      <span>{label}</span>
      {required ? <span data-testid={`required-${label}`}>required</span> : null}
      <span data-testid={`value-${label}`}>
        {value == null ? 'empty' : value.isValid() ? value.format('YYYY-MM-DD') : 'invalid'}
      </span>
      <button type="button" onClick={() => onChange?.(dayjs('invalid'))}>
        invalid-{label}
      </button>
      <button type="button" onClick={() => onChange?.(dayjs('2026-02-20'))}>
        valid-{label}
      </button>
      <button type="button" onClick={() => onChange?.(null)}>
        clear-{label}
      </button>
      {errorMessage ? <span>{errorMessage}</span> : helperText ? <span>{helperText}</span> : null}
    </div>
  ),
}));

vi.mock('../steps/TxReviewItem', () => ({
  TxReviewItem: () => <div data-testid="tx-review-item" />,
}));

const theme = createTheme();

function renderHarness(
  initialOverrides: BillDateOverrides,
  options?: {
    cardAccountOptions?: Array<{
      id: string;
      name: string;
      issuerId: string | null;
      issuerName: string | null;
      issuerKey: string | null;
      maskedNumber: string | null;
      isMultiCard: boolean;
      dueDay: number | null;
    }>;
    importedDueDate?: string | null;
    onBillDateValidationChange?: (validation: unknown) => void;
  },
) {
  function Harness() {
    const [billDateOverrides, setBillDateOverrides] = React.useState(initialOverrides);
    const cardAccountOptions = options?.cardAccountOptions ?? [
      {
        id: 'account-1',
        name: 'Card',
        issuerId: null,
        issuerName: null,
        issuerKey: null,
        maskedNumber: '1234',
        isMultiCard: false,
        dueDay: null,
      },
    ];

    return (
      <>
        <TxReviewStep
          cards={[
            {
              reviewCardId: 'card-1',
              lastFourDigits: '1234',
              financialAccountId: 'account-1',
              importedDueDate: options?.importedDueDate ?? null,
              transactions: [],
            },
          ]}
          txSelections={{}}
          flatTxInclusions={{}}
          flatTxAssignments={{}}
          txCategorySelections={{}}
          billDateOverrides={billDateOverrides}
          isFlatAssignmentMode={false}
          allCardsResolved
          allCategories={[]}
          cardAccountOptions={cardAccountOptions}
          onSetTxSelections={vi.fn()}
          onSetFlatTxInclusions={vi.fn()}
          onSetBillDateOverrides={setBillDateOverrides}
          onUpdateCategory={vi.fn()}
          onBillDateValidationChange={options?.onBillDateValidationChange ?? vi.fn()}
        />
        <pre data-testid="bill-date-overrides">{JSON.stringify(billDateOverrides)}</pre>
      </>
    );
  }

  return render(
    <ThemeProvider theme={theme}>
      <Harness />
    </ThemeProvider>,
  );
}

describe.sequential('TxReviewStep', () => {
  it('does not crash when due date becomes invalid and clears the serialized override', () => {
    renderHarness({ closingDate: '2026-02-07', dueDate: '2026-02-15' });

    fireEvent.click(screen.getByRole('button', { name: 'invalid-Due date' }));

    expect(screen.getByText('Invalid date')).toBeInTheDocument();
    expect(screen.getByTestId('bill-date-overrides').textContent).toContain('"dueDate":""');
  });

  it('recovers after a valid due date is provided', () => {
    renderHarness({ closingDate: '2026-02-07', dueDate: '2026-02-15' });

    fireEvent.click(screen.getByRole('button', { name: 'invalid-Due date' }));
    fireEvent.click(screen.getByRole('button', { name: 'valid-Due date' }));

    expect(screen.getByTestId('bill-date-overrides').textContent).toContain('"dueDate":"2026-02-20"');
  });

  it('reports invalid due date validation to the parent even when overrides are cleared', () => {
    const onBillDateValidationChange = vi.fn();
    function Harness() {
      const [billDateOverrides, setBillDateOverrides] = React.useState<BillDateOverrides>({
        closingDate: '2026-02-07',
        dueDate: '2026-02-15',
      });

      return (
        <TxReviewStep
          cards={[
            {
              reviewCardId: 'card-1',
              lastFourDigits: '1234',
              financialAccountId: 'account-1',
              transactions: [],
            },
          ]}
          txSelections={{}}
          flatTxInclusions={{}}
          flatTxAssignments={{}}
          txCategorySelections={{}}
          billDateOverrides={billDateOverrides}
          isFlatAssignmentMode={false}
          allCardsResolved
          allCategories={[]}
          cardAccountOptions={[{ id: 'account-1', name: 'Card', issuerId: null, issuerName: null, issuerKey: null, maskedNumber: '1234', isMultiCard: false, dueDay: null }]}
          onSetTxSelections={vi.fn()}
          onSetFlatTxInclusions={vi.fn()}
          onSetBillDateOverrides={setBillDateOverrides}
          onUpdateCategory={vi.fn()}
          onBillDateValidationChange={onBillDateValidationChange}
        />
      );
    }

    render(
      <ThemeProvider theme={theme}>
        <Harness />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'invalid-Due date' }));

    expect(onBillDateValidationChange).toHaveBeenCalledWith(
      expect.objectContaining({
        dueDateInvalid: true,
      }),
    );
  });

  it('shows missing-due-date validation when no fallback due day exists', () => {
    renderHarness(
      { closingDate: '2026-02-07', dueDate: '' },
      { importedDueDate: null },
    );

    expect(screen.getByText('Due date is required')).toBeInTheDocument();
    expect(screen.getByTestId('required-Due date')).toBeInTheDocument();
  });

  it('does not require manual due date when the selected account has dueDay', () => {
    renderHarness(
      { closingDate: '2026-02-07', dueDate: '' },
      {
        importedDueDate: null,
        cardAccountOptions: [
          {
            id: 'account-1',
            name: 'Card',
            issuerId: null,
            issuerName: null,
            issuerKey: null,
            maskedNumber: '1234',
            isMultiCard: false,
            dueDay: 15,
          },
        ],
      },
    );

    expect(screen.queryByText('Due date is required')).not.toBeInTheDocument();
    expect(screen.queryByTestId('required-Due date')).not.toBeInTheDocument();
    expect(screen.getByText('Due hint')).toBeInTheDocument();
  });

  it('reports missing due date validation to the parent', () => {
    const onBillDateValidationChange = vi.fn();
    renderHarness(
      { closingDate: '2026-02-07', dueDate: '' },
      { importedDueDate: null, onBillDateValidationChange },
    );

    expect(onBillDateValidationChange).toHaveBeenCalledWith(
      expect.objectContaining({
        dueDateMissing: true,
      }),
    );
  });
});
