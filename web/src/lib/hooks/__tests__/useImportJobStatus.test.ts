import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useImportJobStatus } from '../useImportJobStatus';
import { getAuthToken } from '@/shared/utils/auth';
import { getApolloClient } from '@/lib/graphql/client';

const mockApolloClient = vi.hoisted(() => ({ query: vi.fn() }));

vi.mock('@/shared/utils/auth', () => ({ getAuthToken: vi.fn(() => 'token') }));
vi.mock('@/lib/graphql/client', () => ({
  getApolloClient: vi.fn(() => mockApolloClient),
}));

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

describe.sequential('useImportJobStatus', () => {
  const jobId = '550e8400-e29b-41d4-a716-446655440000';

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    vi.mocked(getAuthToken).mockReturnValue('token');
    vi.mocked(getApolloClient).mockReturnValue(
      mockApolloClient as unknown as ReturnType<typeof getApolloClient>
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('givenStreamCompletedEvent_whenHook_thenOnCompletedCalled', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockResolvedValue({
      ok: true,
      body: createStream([`data: ${JSON.stringify({ status: 'COMPLETED' })}\n\n`]),
    });

    const onCompleted = vi.fn();
    const onStatusChange = vi.fn();

    renderHook(() =>
      useImportJobStatus(jobId, {
        onCompleted,
        onReviewRequired: vi.fn(),
        onFailed: vi.fn(),
        onStatusChange,
      })
    );

    await waitFor(
      () => {
        expect(fetchMock).toHaveBeenCalledWith(
          `/api/imports/${jobId}/stream`,
          expect.objectContaining({ headers: { 'x-auth-token': 'token' } })
        );
        expect(onStatusChange).toHaveBeenCalledWith('COMPLETED');
        expect(onCompleted).toHaveBeenCalled();
      },
      { timeout: 5000 }
    );
  });

  it('givenStreamReviewRequired_whenHook_thenOnReviewRequiredCalled', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      body: createStream([`data: ${JSON.stringify({ status: 'REVIEW_REQUIRED' })}\n\n`]),
    });

    const onReviewRequired = vi.fn();

    renderHook(() =>
      useImportJobStatus(jobId, {
        onCompleted: vi.fn(),
        onReviewRequired,
        onFailed: vi.fn(),
      })
    );

    await waitFor(
      () => {
        expect(onReviewRequired).toHaveBeenCalled();
      },
      { timeout: 5000 }
    );
  });

  it('givenConnectionError_whenHook_thenFallbackQueryTriggered', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('Network error'));

    mockApolloClient.query.mockResolvedValue({
      data: {
        transactionImport: {
          id: jobId,
          status: 'COMPLETED',
          errorDetails: null,
        },
      },
    });

    const onCompleted = vi.fn();

    renderHook(() =>
      useImportJobStatus(jobId, {
        onCompleted,
        onReviewRequired: vi.fn(),
        onFailed: vi.fn(),
      })
    );

    await waitFor(() => {
      expect(mockApolloClient.query).toHaveBeenCalledWith(
        expect.objectContaining({ variables: { id: jobId } })
      );
      expect(onCompleted).toHaveBeenCalled();
    });
  });

  it('givenUnmount_whenHook_thenAbortCalled', async () => {
    let abortSignal: AbortSignal | undefined;
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url, opts) => {
      abortSignal = (opts as { signal?: AbortSignal })?.signal;
      return Promise.resolve({
        ok: true,
        body: createStream([]),
      });
    });

    const { unmount } = renderHook(() =>
      useImportJobStatus(jobId, {
        onCompleted: vi.fn(),
        onReviewRequired: vi.fn(),
        onFailed: vi.fn(),
      })
    );

    await waitFor(() => {
      expect(abortSignal).toBeDefined();
    });

    unmount();

    await waitFor(() => {
      expect(abortSignal?.aborted).toBe(true);
    });
  });

  it('givenPartialChunks_whenHook_thenEventsBufferedCorrectly', async () => {
    const payload = JSON.stringify({ status: 'COMPLETED' });
    const chunk1 = `data: ${payload.slice(0, 15)}`;
    const chunk2 = `${payload.slice(15)}\n\n`;

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      body: createStream([chunk1, chunk2]),
    });

    const onCompleted = vi.fn();

    renderHook(() =>
      useImportJobStatus(jobId, {
        onCompleted,
        onReviewRequired: vi.fn(),
        onFailed: vi.fn(),
      })
    );

    await waitFor(() => {
      expect(onCompleted).toHaveBeenCalled();
    });
  });

  it('givenStreamClosesWithoutTerminal_whenHook_thenFallbackQueryResolvesTerminal', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      body: createStream([`data: ${JSON.stringify({ status: 'PROCESSING' })}\n\n`]),
    });

    mockApolloClient.query.mockResolvedValue({
      data: {
        transactionImport: {
          id: jobId,
          status: 'COMPLETED',
          errorDetails: null,
          importedCount: 3,
          skippedCount: 0,
          duplicateReviewCount: 0,
          errorCount: 0,
        },
      },
    });

    const onCompleted = vi.fn();
    const onStatusChange = vi.fn();

    renderHook(() =>
      useImportJobStatus(jobId, {
        onCompleted,
        onReviewRequired: vi.fn(),
        onFailed: vi.fn(),
        onStatusChange,
      })
    );

    await waitFor(() => {
      expect(mockApolloClient.query).toHaveBeenCalledWith(
        expect.objectContaining({ variables: { id: jobId } })
      );
      expect(onStatusChange).toHaveBeenCalledWith('PROCESSING');
      expect(onStatusChange).toHaveBeenCalledWith('COMPLETED');
      expect(onCompleted).toHaveBeenCalled();
    });
  });
});
