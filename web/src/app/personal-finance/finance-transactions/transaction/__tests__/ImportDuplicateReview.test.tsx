import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useQuery, useMutation } from '@apollo/client/react';
import { ImportDuplicateReview } from '../ImportDuplicateReview';

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

vi.mock('@apollo/client/react', () => ({
  useQuery: vi.fn(),
  useMutation: vi.fn(),
}));

describe.sequential('ImportDuplicateReview', () => {
  const onComplete = vi.fn();
  const onClose = vi.fn();

  beforeEach(() => {
    mockResolve.mockClear();
    onComplete.mockClear();
    onClose.mockClear();
    vi.mocked(useMutation).mockReturnValue([mockResolve, { loading: false, error: undefined }] as unknown as ReturnType<typeof useMutation>);
    vi.mocked(useQuery).mockImplementation(((doc: unknown, opts?: { variables?: { id?: string }; skip?: boolean }) => {
        const id = opts?.variables?.id;
        const skip = opts?.skip ?? true;
        if (skip || !id) return { data: null, loading: false, error: undefined };
        return { data: { transactionImport: jobWithCandidates }, loading: false, error: undefined };
      }) as typeof useQuery);
  });

  it('givenCandidates_whenRendered_thenAllShown', async () => {
    render(
      <ImportDuplicateReview jobId="job-1" onComplete={onComplete} onClose={onClose} />,
    );
    await waitFor(() => {
      expect(
        screen.getByText(
          /transaction\(s\) need review|transação\(ões\) precisam de revisão/,
        ),
      ).toBeInTheDocument();
    });
    expect(screen.getByText('Imported purchase')).toBeInTheDocument();
    expect(screen.getByText('Existing purchase')).toBeInTheDocument();
    expect(screen.getByText('Imported fee')).toBeInTheDocument();
    expect(screen.getByText('Existing fee')).toBeInTheDocument();
    const newLabels = screen.getAllByText(/New \(imported\)|Nova \(importada\)/);
    const existingLabels = screen.getAllByText(/Existing|Existente/);
    expect(newLabels.length).toBeGreaterThanOrEqual(1);
    expect(existingLabels.length).toBeGreaterThanOrEqual(1);
  });

  it('givenNotAllResolved_whenConcluir_thenButtonDisabled', async () => {
    render(
      <ImportDuplicateReview jobId="job-1" onComplete={onComplete} onClose={onClose} />,
    );
    await waitFor(() => {
      expect(
        screen.getAllByRole('button', { name: /Keep both|Manter ambas/ }).length,
      ).toBeGreaterThanOrEqual(1);
    });
    const finishButton = screen.getByRole('button', {
      name: /Finish review|Concluir revisão/,
    });
    expect(finishButton).toBeDisabled();
    const keepBothButtons = screen.getAllByRole('button', { name: /Keep both|Manter ambas/ });
    fireEvent.click(keepBothButtons[0]!);
    await waitFor(() => {
      expect(finishButton).toBeDisabled();
    });
  });

  it('givenAllResolved_whenConcluir_thenMutationCalledForEach', async () => {
    render(
      <ImportDuplicateReview jobId="job-1" onComplete={onComplete} onClose={onClose} />,
    );
    await waitFor(() => {
      expect(
        screen.getAllByRole('button', { name: /Keep both|Manter ambas/ }).length,
      ).toBe(2);
    });
    const keepBothButtons = screen.getAllByRole('button', { name: /Keep both|Manter ambas/ });
    fireEvent.click(keepBothButtons[0]!);
    fireEvent.click(keepBothButtons[1]!);
    await waitFor(() => {
      const finishButton = screen.getByRole('button', {
        name: /Finish review|Concluir revisão/,
      });
      expect(finishButton).not.toBeDisabled();
    });
    const finishButton = screen.getByRole('button', {
      name: /Finish review|Concluir revisão/,
    });
    fireEvent.click(finishButton);
    await waitFor(() => {
      expect(mockResolve).toHaveBeenCalledTimes(2);
      expect(mockResolve).toHaveBeenCalledWith({
        variables: {
          input: { jobId: 'job-1', rowIndex: 0, resolution: 'KEEP_BOTH' },
        },
      });
      expect(mockResolve).toHaveBeenCalledWith({
        variables: {
          input: { jobId: 'job-1', rowIndex: 1, resolution: 'KEEP_BOTH' },
        },
      });
      expect(onComplete).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });
});
