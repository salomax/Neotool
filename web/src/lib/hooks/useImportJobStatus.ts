'use client';

import { useEffect, useRef } from 'react';
import { getAuthToken } from '@/shared/utils/auth';
import { getApolloClient } from '@/lib/graphql/client';
import { TRANSACTION_IMPORT } from '@/lib/graphql/operations/banking/queries';

/** Import job status values from the backend (matches ImportStatus enum). */
export type ImportStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'REVIEW_REQUIRED';

const TERMINAL_STATUSES: ImportStatus[] = [
  'COMPLETED',
  'FAILED',
  'REVIEW_REQUIRED',
];

function isTerminal(status: string): status is ImportStatus {
  return TERMINAL_STATUSES.includes(status as ImportStatus);
}

/** Payload sent over SSE (matches backend ImportJobStatusPayload). */
export interface ImportJobStatusPayload {
  status: ImportStatus;
  errorDetails?: string | null;
  importedCount?: number | null;
  skippedCount?: number | null;
  duplicateReviewCount?: number | null;
  errorCount?: number | null;
}

export interface UseImportJobStatusCallbacks {
  onCompleted: () => void;
  onReviewRequired: () => void;
  onFailed: (errorDetails?: unknown) => void;
  onStatusChange?: (status: ImportStatus) => void;
  /** Called on each SSE event (for progress: status, importedCount, etc.). */
  onEvent?: (payload: ImportJobStatusPayload) => void;
}

interface SsePayload extends ImportJobStatusPayload {}
const FALLBACK_POLL_INTERVAL_MS = 5000;

/**
 * Subscribes to import job status via SSE (fetch + ReadableStream).
 * Supports custom headers (x-auth-token). On connection issues or missed terminal SSE events,
 * falls back to GraphQL status checks.
 * Cleans up (abort) on unmount or jobId change.
 */
export function useImportJobStatus(
  jobId: string | null,
  callbacks: UseImportJobStatusCallbacks
): void {
  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;

  useEffect(() => {
    if (!jobId) return;

    const abortController = new AbortController();
    let receivedTerminal = false;
    let fallbackInterval: ReturnType<typeof setInterval> | null = null;

    const dispatchTerminal = (payload: SsePayload) => {
      if (receivedTerminal) return;
      receivedTerminal = true;
      if (fallbackInterval) {
        clearInterval(fallbackInterval);
        fallbackInterval = null;
      }
      const { onCompleted, onReviewRequired, onFailed, onStatusChange, onEvent } =
        callbacksRef.current;
      onEvent?.(payload);
      onStatusChange?.(payload.status);
      switch (payload.status) {
        case 'COMPLETED':
          onCompleted();
          break;
        case 'REVIEW_REQUIRED':
          onReviewRequired();
          break;
        case 'FAILED':
          onFailed(payload.errorDetails ?? undefined);
          break;
        default:
          break;
      }
    };

    const runFallbackQuery = async (silentFailure = false) => {
      try {
        const client = getApolloClient();
        const result = await client.query<{
          transactionImport: {
            id: string;
            status: string;
            errorDetails?: string | null;
            importedCount?: number | null;
            skippedCount?: number | null;
            duplicateReviewCount?: number | null;
            errorCount?: number | null;
          } | null;
        }>({
          query: TRANSACTION_IMPORT,
          variables: { id: jobId },
          fetchPolicy: 'network-only',
        });
        const job = result.data?.transactionImport;
        if (job?.status && isTerminal(job.status)) {
          dispatchTerminal({
            status: job.status as ImportStatus,
            errorDetails: job.errorDetails ?? undefined,
            importedCount: job.importedCount ?? undefined,
            skippedCount: job.skippedCount ?? undefined,
            duplicateReviewCount: job.duplicateReviewCount ?? undefined,
            errorCount: job.errorCount ?? undefined,
          });
        }
      } catch {
        if (!silentFailure) {
          callbacksRef.current.onFailed(undefined);
        }
      }
    };

    const token = getAuthToken();
    if (!token) {
      runFallbackQuery();
      return () => {};
    }

    const url = `/api/imports/${jobId}/stream`;
    fetch(url, {
      signal: abortController.signal,
      headers: { 'x-auth-token': token },
    })
      .then(async (response) => {
        if (!response.ok || !response.body) {
          await runFallbackQuery();
          return;
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const processLine = (line: string) => {
          if (line.startsWith('data:')) {
            const jsonStr = line.slice(5).trim();
            if (!jsonStr) return;
            try {
              const data = JSON.parse(jsonStr) as SsePayload;
              if (data.status) {
                callbacksRef.current.onEvent?.(data);
                callbacksRef.current.onStatusChange?.(data.status as ImportStatus);
                if (isTerminal(data.status)) {
                  dispatchTerminal(data);
                  abortController.abort();
                }
              }
            } catch {
              // ignore malformed payload
            }
          }
        };

        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split(/\n\n+/);
            buffer = events.pop() ?? '';
            for (const event of events) {
              const lines = event.split('\n');
              for (const line of lines) processLine(line);
            }
          }
          if (buffer) {
            const lines = buffer.split('\n');
            for (const line of lines) processLine(line);
          }
          if (!receivedTerminal && !abortController.signal.aborted) {
            await runFallbackQuery();
          }
        } finally {
          reader.releaseLock();
        }
      })
      .catch((err) => {
        if (err?.name === 'AbortError') return;
        if (!receivedTerminal) runFallbackQuery();
      });

    // Safety net when SSE connection remains open but terminal event is missed.
    fallbackInterval = setInterval(() => {
      if (receivedTerminal || abortController.signal.aborted) return;
      void runFallbackQuery(true);
    }, FALLBACK_POLL_INTERVAL_MS);

    return () => {
      if (fallbackInterval) {
        clearInterval(fallbackInterval);
        fallbackInterval = null;
      }
      abortController.abort();
    };
  }, [jobId]);
}
