'use client';

import { useEffect, useRef } from 'react';
import { getAuthToken } from '@/shared/utils/auth';

export type PortfolioRecalculationStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

const TERMINAL_STATUSES: PortfolioRecalculationStatus[] = ['COMPLETED', 'FAILED'];

function isTerminal(status: string): status is PortfolioRecalculationStatus {
  return TERMINAL_STATUSES.includes(status as PortfolioRecalculationStatus);
}

export interface PortfolioRecalculationStatusPayload {
  jobId: string;
  accountId: string;
  financialAccountId?: string | null;
  status: PortfolioRecalculationStatus;
  recalculateFrom: string;
  daysProcessed?: number | null;
  errorCode?: string | null;
  errorDetails?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface UsePortfolioRecalculationStatusCallbacks {
  onCompleted?: (payload: PortfolioRecalculationStatusPayload) => void;
  onFailed?: (payload: PortfolioRecalculationStatusPayload) => void;
  onStatusChange?: (status: PortfolioRecalculationStatus) => void;
  onEvent?: (payload: PortfolioRecalculationStatusPayload) => void;
  onConnectionError?: () => void;
}

export function usePortfolioRecalculationStatus(
  accountId: string | null,
  callbacks: UsePortfolioRecalculationStatusCallbacks,
  reconnectKey = 0,
): void {
  const callbacksRef = useRef(callbacks);
  callbacksRef.current = callbacks;

  useEffect(() => {
    if (!accountId) return;

    const abortController = new AbortController();
    let receivedTerminal = false;

    const dispatchEvent = (payload: PortfolioRecalculationStatusPayload) => {
      callbacksRef.current.onEvent?.(payload);
      callbacksRef.current.onStatusChange?.(payload.status);

      if (!isTerminal(payload.status) || receivedTerminal) return;
      receivedTerminal = true;

      if (payload.status === 'COMPLETED') {
        callbacksRef.current.onCompleted?.(payload);
      } else if (payload.status === 'FAILED') {
        callbacksRef.current.onFailed?.(payload);
      }
    };

    const token = getAuthToken();
    if (!token) {
      callbacksRef.current.onConnectionError?.();
      return () => {};
    }

    const url = `/api/investments/portfolio/${accountId}/recalculation-status/events`;
    fetch(url, {
      signal: abortController.signal,
      headers: { 'x-auth-token': token },
    })
      .then(async (response) => {
        if (!response.ok || !response.body) {
          callbacksRef.current.onConnectionError?.();
          return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const processLine = (line: string) => {
          const normalizedLine = line.trimStart();
          if (!normalizedLine.startsWith('data:')) return;
          const jsonStr = normalizedLine.slice(5).trim();
          if (!jsonStr) return;

          try {
            const payload = JSON.parse(jsonStr) as PortfolioRecalculationStatusPayload;
            if (!payload.status) return;
            dispatchEvent(payload);
            if (isTerminal(payload.status)) {
              abortController.abort();
            }
          } catch {
            // Ignore malformed payloads to keep the stream alive.
          }
        };

        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split(/\r?\n\r?\n+/);
            buffer = events.pop() ?? '';
            for (const event of events) {
              const lines = event.split(/\r?\n/);
              for (const line of lines) processLine(line);
            }
          }

          if (buffer) {
            const lines = buffer.split(/\r?\n/);
            for (const line of lines) processLine(line);
          }

          if (!receivedTerminal && !abortController.signal.aborted) {
            callbacksRef.current.onConnectionError?.();
          }
        } finally {
          reader.releaseLock();
        }
      })
      .catch((err) => {
        if (err?.name === 'AbortError') return;
        callbacksRef.current.onConnectionError?.();
      });

    return () => {
      abortController.abort();
    };
  }, [accountId, reconnectKey]);
}
