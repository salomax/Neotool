import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { usePortfolioRecalculationStatus } from '../usePortfolioRecalculationStatus';
import { getAuthToken } from '@/shared/utils/auth';

vi.mock('@/shared/utils/auth', () => ({ getAuthToken: vi.fn(() => 'token') }));

function createStream(chunks: string[]): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(new TextEncoder().encode(chunk));
      }
      controller.close();
    },
  });
}

describe.sequential('usePortfolioRecalculationStatus', () => {
  const accountId = '550e8400-e29b-41d4-a716-446655440000';

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    vi.mocked(getAuthToken).mockReturnValue('token');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('givenCompletedStreamEvent_whenHook_thenOnCompletedCalled', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    const payload = {
      jobId: 'job-1',
      accountId,
      financialAccountId: 'fa-1',
      status: 'COMPLETED',
      recalculateFrom: '2026-04-01',
    };
    fetchMock.mockResolvedValue({
      ok: true,
      body: createStream([`data: ${JSON.stringify(payload)}\n\n`]),
    });

    const onCompleted = vi.fn();
    const onStatusChange = vi.fn();

    renderHook(() =>
      usePortfolioRecalculationStatus(accountId, {
        onCompleted,
        onStatusChange,
      })
    );

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/investments/portfolio/${accountId}/recalculation-status/events`,
        expect.objectContaining({ headers: { 'x-auth-token': 'token' } })
      );
      expect(onStatusChange).toHaveBeenCalledWith('COMPLETED');
      expect(onCompleted).toHaveBeenCalledWith(payload);
    });
  });

  it('givenFailedStreamEvent_whenHook_thenOnFailedCalled', async () => {
    const payload = {
      jobId: 'job-1',
      accountId,
      financialAccountId: 'fa-1',
      status: 'FAILED',
      recalculateFrom: '2026-04-01',
      errorCode: 'INTERNAL_ERROR',
      errorDetails: 'boom',
    };

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      body: createStream([`data: ${JSON.stringify(payload)}\n\n`]),
    });

    const onFailed = vi.fn();

    renderHook(() =>
      usePortfolioRecalculationStatus(accountId, {
        onFailed,
      })
    );

    await waitFor(() => {
      expect(onFailed).toHaveBeenCalledWith(payload);
    });
  });

  it('givenConnectionError_whenHook_thenOnConnectionErrorCalled', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('Network error'));

    const onConnectionError = vi.fn();

    renderHook(() =>
      usePortfolioRecalculationStatus(accountId, {
        onConnectionError,
      })
    );

    await waitFor(() => {
      expect(onConnectionError).toHaveBeenCalled();
    });
  });
});
