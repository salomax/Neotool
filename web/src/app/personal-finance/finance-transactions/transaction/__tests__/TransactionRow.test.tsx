import type { ReactElement } from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import { ThemeProvider, createTheme, Table, TableBody } from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { CreditDebitType, TransactionType, type Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX } from '../../../utils/financeTransactionTableStyles';
import { TransactionRow } from '../TransactionRow';

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(() => ({ data: { financialAccounts: [], transactionCategories: [], transactionTags: [] } })),
  useLazyQuery: vi.fn(() => [vi.fn(), { loading: false, data: null }]),
  useMutation: vi.fn(() => [vi.fn(), { loading: false }]),
  useApolloClient: vi.fn(),
}));

vi.mock('@/shared/providers', () => ({
  usePreferences: () => ({ bankingUserPreferences: { ruleCreationMode: 'ALWAYS_PROMPT' } }),
  useToast: () => ({ error: vi.fn(), success: vi.fn(), info: vi.fn(), warning: vi.fn() }),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  i18n: { language: 'pt-BR' },
}));

vi.mock('../SimilarEditDialog', () => ({
  SimilarEditDialog: () => null,
}));

vi.mock('../MerchantSelect', () => ({
  MerchantSelect: () => <div data-testid="merchant-select-mock" />,
}));

vi.mock('../../../components/AttachmentSection', () => ({
  AttachmentSection: () => <div data-testid="attachment-section-mock" />,
}));

const theme = createTheme();
const expectedRadius = `${FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX}px`;

function renderInTable(ui: ReactElement) {
  return render(
    <ThemeProvider theme={theme}>
      <LocalizationProvider dateAdapter={AdapterDayjs}>
        <Table>
          <TableBody>{ui}</TableBody>
        </Table>
      </LocalizationProvider>
    </ThemeProvider>,
  );
}

const transaction = {
  id: 'tx-radius-1',
  financialAccountId: 'acc-1',
  description: 'Radius fixture',
  date: '2026-03-15',
  amount: 42,
  currencyCode: 'BRL',
  creditDebitType: CreditDebitType.Debit,
  type: TransactionType.OtherExpense,
  merchant: null,
  category: null,
  tags: [],
  attachments: [],
  appliedRuleIds: [],
} as unknown as Transaction;

const noopCallbacks = {
  onOpenCategoryPicker: vi.fn(),
  onOpenActionMenu: vi.fn(),
  onOpenAttachmentPreview: vi.fn(),
  onEditClose: vi.fn(),
  onEditExited: vi.fn(),
  onEditSuccess: vi.fn(),
  onEditRefresh: vi.fn(),
  onBusyStart: vi.fn(),
  onBusyEnd: vi.fn(),
};

afterEach(() => {
  cleanup();
});

function parseRadius(value: string): number {
  const n = Number.parseFloat(value);
  return Number.isFinite(n) ? n : 0;
}

function cellRadii(el: HTMLElement) {
  const s = getComputedStyle(el);
  return {
    tl: parseRadius(s.borderTopLeftRadius),
    tr: parseRadius(s.borderTopRightRadius),
    bl: parseRadius(s.borderBottomLeftRadius),
    br: parseRadius(s.borderBottomRightRadius),
  };
}

describe('TransactionRow — date-group corner radius', () => {
  it('applies corner radius only to the true first/last cells, never the Description cell', () => {
    renderInTable(
      <TransactionRow
        transaction={transaction}
        account={null}
        isFirst
        isLast
        isEditing={false}
        isEditFormOpen={false}
        isDeleting={false}
        updatingCategoryTransactionId={null}
        displayedCategory={null}
        selectedAccountId="acc-1"
        activeActionMenuTransactionId={null}
        activeActionMenuAnchorEl={null}
        callbacks={noopCallbacks}
      />,
    );

    const row = screen.getByTestId('transaction-row-tx-radius-1');
    // Include th and td — getAllByRole('cell') excludes rowheader <th> and would skip the buggy cell.
    // eslint-disable-next-line testing-library/no-node-access
    const cells = Array.from(row.querySelectorAll(':scope > .MuiTableCell-root')) as HTMLElement[];
    expect(cells.length).toBeGreaterThanOrEqual(3);

    // eslint-disable-next-line testing-library/no-node-access
    const description = within(row).getByText('Radius fixture').closest('.MuiTableCell-root') as HTMLElement;
    expect(description).toBeTruthy();

    // Under the old component="th" bug this cell matched :first-of-type/:last-of-type and got all four radii.
    // Assert radius first — the property the issue cares about — then the tag that caused it.
    const descRadii = cellRadii(description);
    expect(descRadii.tl + descRadii.tr + descRadii.bl + descRadii.br).toBe(0);
    expect(description.tagName).toBe('TD');
    // eslint-disable-next-line testing-library/no-node-access
    expect(row.querySelector(':scope > th')).toBeNull();

    const first = cells[0]!;
    const last = cells[cells.length - 1]!;
    expect(description).not.toBe(first);
    expect(description).not.toBe(last);

    expect(cellRadii(first).tl).toBe(FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX);
    expect(cellRadii(first).bl).toBe(FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX);
    expect(cellRadii(last).tr).toBe(FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX);
    expect(cellRadii(last).br).toBe(FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX);
    expect(getComputedStyle(first).borderTopLeftRadius).toBe(expectedRadius);
    expect(getComputedStyle(last).borderTopRightRadius).toBe(expectedRadius);
  });
});
