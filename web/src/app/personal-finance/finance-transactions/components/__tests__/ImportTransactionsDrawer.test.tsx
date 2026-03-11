import { describe, it, expect, vi, beforeEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ImportTransactionsDrawer } from '../ImportTransactionsDrawer';
import { useImportJobStatus } from '@/lib/hooks/useImportJobStatus';
import { FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';

const mockMutate = vi.fn(() =>
  Promise.resolve({ data: { importTransactionsFile: { import: { id: 'job-123' } } } }),
);
const mockUseQuery = vi.fn((_query?: unknown, _options?: unknown) => ({ data: { financialAccounts: [] }, loading: false }));

vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(() => [mockMutate, { loading: false, error: null }]),
  useQuery: (query: unknown, options?: unknown) => mockUseQuery(query, options),
}));

vi.mock('@/lib/hooks/useImportJobStatus', () => ({
  useImportJobStatus: vi.fn(),
}));

class MockFileReader {
  result: string | ArrayBuffer | null = 'data:application/x-ofx;base64,T0ZY';
  onload: ((this: FileReader, ev: ProgressEvent<FileReader>) => unknown) | null = null;
  onerror: ((this: FileReader, ev: ProgressEvent<FileReader>) => unknown) | null = null;

  readAsDataURL(_blob: Blob): void {
    const ev = new ProgressEvent('load') as ProgressEvent<FileReader>;
    this.onload?.call(this as unknown as FileReader, ev);
  }
}

describe('ImportTransactionsDrawer', () => {
  const onClose = vi.fn();

  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.stubGlobal('FileReader', MockFileReader);
    vi.mocked(useImportJobStatus).mockImplementation(() => {});
    mockUseQuery.mockReturnValue({ data: { financialAccounts: [] }, loading: false });
  });

  it('givenClosed_whenRendered_thenRendersNothing', () => {
    render(
      <ImportTransactionsDrawer open={false} onClose={onClose} selectedAccountId="acc-1" />,
    );
    expect(screen.queryByText(/Import statement|Importar extrato/)).not.toBeInTheDocument();
  });

  it('givenOpenWithSelectedAccount_whenRendered_thenShowsUploadStep', () => {
    render(
      <ImportTransactionsDrawer open onClose={onClose} selectedAccountId="acc-1" selectedAccountType={FinancialAccountType.Bank} />,
    );
    expect(screen.getAllByText(/Select file|Selecione o arquivo/).length).toBeGreaterThan(0);
  });

  it('givenConsolidatedView_whenRendered_thenShowsAccountSelectionStep', () => {
    mockUseQuery.mockReturnValue({
      data: {
        financialAccounts: [
          {
            id: 'acc-1',
            name: 'Conta XP',
            type: 'BANK',
            issuer: { name: 'XP', logoAsset: null },
            issuerName: 'XP',
          },
        ],
      },
      loading: false,
    } as unknown as ReturnType<typeof mockUseQuery>);
    render(
      <ImportTransactionsDrawer open onClose={onClose} selectedAccountId={null} />,
    );
    expect(
      screen.getByText(
        /Select account to import into|Selecione a conta para importar/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Conta XP')).toBeInTheDocument();
  });

  it('givenConsolidatedView_whenRendered_thenFiltersOutCards', () => {
    mockUseQuery.mockReturnValue({
      data: {
        financialAccounts: [
          {
            id: 'acc-1',
            name: 'Conta XP',
            type: 'BANK',
            issuer: { name: 'XP', logoAsset: null },
            issuerName: 'XP',
          },
          {
            id: 'card-1',
            name: 'Cartao Gold',
            type: 'CREDIT',
            issuer: { name: 'Nubank', logoAsset: null },
            issuerName: 'Nubank',
          },
        ],
      },
      loading: false,
    } as unknown as ReturnType<typeof mockUseQuery>);

    render(
      <ImportTransactionsDrawer open onClose={onClose} selectedAccountId={null} />,
    );

    expect(screen.queryAllByText('Conta XP').length).toBeGreaterThan(0);
    expect(screen.queryAllByText('Cartao Gold').length).toBe(0);
  });

  it('givenDuplicateImportError_whenSubmit_thenShowsFriendlyTranslatedError', async () => {
    const user = userEvent.setup();

    mockMutate.mockRejectedValueOnce({
      graphQLErrors: [
        {
          message: 'This file was already imported or is currently being processed for this account',
          extensions: {
            code: 'IMPORT_FILE_ALREADY_EXISTS',
            parameters: {
              existingJobId: 'job-999',
              existingStatus: 'PROCESSING',
            },
          },
        },
      ],
    });

    render(
      <ImportTransactionsDrawer open onClose={onClose} selectedAccountId="acc-1" selectedAccountType={FinancialAccountType.Bank} />,
    );

    const [input] = screen.getAllByTestId('import-transactions-file-input') as HTMLInputElement[];
    const file = new File(['ofx'], 'statement.ofx', { type: 'application/x-ofx' });
    await user.upload(input, file);

    const submitButton = screen.getByRole('button', { name: /Importar|Import/i });
    await user.click(submitButton);

    await waitFor(() => {
      expect(
        screen.getByText(/já foi importado|already been imported/i),
      ).toBeInTheDocument();
    });

    expect(screen.queryByText(/Algo deu errado/i)).not.toBeInTheDocument();
  });

});
