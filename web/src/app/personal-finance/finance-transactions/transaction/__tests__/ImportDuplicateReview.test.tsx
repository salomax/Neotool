import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useMutation } from '@apollo/client/react';
import { ImportDuplicateReview } from '../ImportDuplicateReview';

const mockToast = {
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
};

const mockResolve = vi.fn(() =>
  Promise.resolve({
    data: {
      resolveImportDuplicate: {
        id: 'job-1',
        status: 'REVIEW_REQUIRED',
        duplicateReviewCount: 0,
        duplicateCandidates: [],
      },
    },
  }),
);

const jobWithCandidates = {
  id: 'job-1',
  status: 'REVIEW_REQUIRED',
  duplicateReviewCount: 2,
  duplicateCandidates: [
    {
      rowIndex: 0,
      parsed: {
        creditDebitType: 'DEBIT',
        amount: 100,
        date: '2025-01-15',
        description: 'Imported purchase',
        currencyCode: 'BRL',
      },
      candidates: [
        {
          id: 'tx-1',
          date: '2025-01-15',
          description: 'Existing purchase',
          amount: 100,
          creditDebitType: 'DEBIT',
          currencyCode: 'BRL',
          source: 'MANUAL',
        },
      ],
    },
    {
      rowIndex: 1,
      parsed: {
        creditDebitType: 'DEBIT',
        amount: 50,
        date: '2025-01-16',
        description: 'Imported fee',
        currencyCode: 'BRL',
      },
      candidates: [
        {
          id: 'tx-2',
          date: '2025-01-16',
          description: 'Existing fee',
          amount: 50,
          creditDebitType: 'DEBIT',
          currencyCode: 'BRL',
          source: 'OFX',
        },
      ],
    },
  ],
};

const mockClientQuery = vi.fn();

vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(),
  useApolloClient: () => ({
    query: mockClientQuery,
  }),
}));

vi.mock('@/shared/providers', () => ({
  useToast: () => mockToast,
}));

describe.sequential('ImportDuplicateReview', () => {
  const onComplete = vi.fn();
  const onClose = vi.fn();

  beforeEach(() => {
    mockResolve.mockClear();
    onComplete.mockClear();
    onClose.mockClear();
    mockClientQuery.mockReset();
    vi.mocked(useMutation).mockReturnValue([mockResolve, { loading: false, error: undefined }] as unknown as ReturnType<typeof useMutation>);

    mockClientQuery.mockImplementation(async (opts: { variables?: { id?: string } }) => {
      const id = opts?.variables?.id;
      if (!id) return { data: { transactionImport: null } };
      return { data: { transactionImport: jobWithCandidates } };
    });
  });

  it('givenCandidates_whenRendered_thenShowsFirstCandidate', async () => {
    render(
      <ImportDuplicateReview jobIds={['job-1']} onComplete={onComplete} onClose={onClose} />,
    );
    await waitFor(() => {
      expect(screen.getByText('Imported purchase')).toBeInTheDocument();
    });
    expect(screen.getByText('Imported purchase')).toBeInTheDocument();
    expect(screen.getByText('Existing purchase')).toBeInTheDocument();
    expect(screen.getByText('Imported fee')).toBeInTheDocument();
    expect(screen.getByText('Existing fee')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Keep both|Manter ambas/ }).length).toBeGreaterThanOrEqual(2);
  });

  it('givenFirstResolution_whenClicked_thenCallsMutation', async () => {
    render(
      <ImportDuplicateReview jobIds={['job-1']} onComplete={onComplete} onClose={onClose} />,
    );
    await waitFor(() => {
      expect(
        screen.getAllByRole('button', { name: /Keep both|Manter ambas/ }).length
      ).toBeGreaterThanOrEqual(1);
    });

    const keepBothButtons = screen.getAllByRole('button', { name: /Keep both|Manter ambas/ });
    fireEvent.click(keepBothButtons[0]!);

    await waitFor(() => {
      expect(mockResolve).toHaveBeenCalledTimes(1);
      expect(mockResolve).toHaveBeenCalledWith({
        variables: {
          input: { jobId: 'job-1', rowIndex: 0, resolution: 'KEEP_BOTH' },
        },
      });
    });
  });

  it('givenAllResolved_whenResolvedSequentially_thenCompletesAndCloses', async () => {
    render(
      <ImportDuplicateReview jobIds={['job-1']} onComplete={onComplete} onClose={onClose} />,
    );
    await waitFor(() => {
      expect(
        screen.getAllByRole('button', { name: /Keep both|Manter ambas/ }).length
      ).toBeGreaterThanOrEqual(2);
    });

    fireEvent.click(screen.getAllByRole('button', { name: /Keep both|Manter ambas/ })[0]!);

    await waitFor(() => {
      expect(mockResolve).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getAllByRole('button', { name: /Keep both|Manter ambas/ })[0]!);

    await waitFor(() => {
      expect(mockResolve).toHaveBeenCalledTimes(2);
      expect(mockResolve).toHaveBeenNthCalledWith(1, {
        variables: {
          input: { jobId: 'job-1', rowIndex: 0, resolution: 'KEEP_BOTH' },
        },
      });
      expect(mockResolve).toHaveBeenNthCalledWith(2, {
        variables: {
          input: { jobId: 'job-1', rowIndex: 1, resolution: 'KEEP_BOTH' },
        },
      });
      expect(onComplete).toHaveBeenCalledTimes(1);
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });

  it('givenMutationError_whenResolve_thenShowsError', async () => {
    mockResolve.mockRejectedValueOnce(new Error('network down'));
    render(
      <ImportDuplicateReview jobIds={['job-1']} onComplete={onComplete} onClose={onClose} />,
    );
    await waitFor(() => {
      expect(
        screen.getAllByRole('button', { name: /Keep both|Manter ambas/ }).length
      ).toBeGreaterThanOrEqual(1);
    });
    fireEvent.click(screen.getAllByRole('button', { name: /Keep both|Manter ambas/ })[0]!);

    await waitFor(() => {
      expect(mockToast.error).toHaveBeenCalled();
      expect(onClose).not.toHaveBeenCalled();
    });
  });
});
