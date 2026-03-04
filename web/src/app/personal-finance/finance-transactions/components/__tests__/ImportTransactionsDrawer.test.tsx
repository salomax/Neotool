import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ImportTransactionsDrawer } from '../ImportTransactionsDrawer';
import { useImportJobStatus } from '@/lib/hooks/useImportJobStatus';

const mockMutate = vi.fn(() =>
  Promise.resolve({ data: { importTransactionsFile: { import: { id: 'job-123' } } } }),
);
const mockUseQuery = vi.fn(() => ({ data: { financialAccounts: [] }, loading: false }));

vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(() => [mockMutate, { loading: false, error: null }]),
  useQuery: (...args: unknown[]) => mockUseQuery(...args),
}));

vi.mock('@/lib/hooks/useImportJobStatus', () => ({
  useImportJobStatus: vi.fn(),
}));

class MockFileReader {
  result: string | ArrayBuffer | null = 'data:application/x-ofx;base64,T0ZY';
  onload: ((this: FileReader, ev: ProgressEvent<FileReader>) => unknown) | null = null;
  onerror: ((this: FileReader, ev: ProgressEvent<FileReader>) => unknown) | null = null;

  readAsDataURL(_blob: Blob): void {
    this.onload?.call(this as unknown as FileReader, new ProgressEvent('load'));
  }
}

describe('ImportTransactionsDrawer', () => {
  const onClose = vi.fn();

  beforeEach(() => {
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
      <ImportTransactionsDrawer open onClose={onClose} selectedAccountId="acc-1" />,
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
            issuer: { name: 'XP', logoAsset: null },
            issuerName: 'XP',
          },
        ],
      },
      loading: false,
    });
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
});
