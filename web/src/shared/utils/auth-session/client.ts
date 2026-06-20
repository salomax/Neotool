"use client";

import { logger } from "@/shared/utils/logger";
import type { AuthSessionUser } from "./shared";

export const AUTH_SESSION_SYNC_TIMEOUT_MS = 1200;

export type AuthSessionSyncResult =
  | { ok: true; status: "synced"; statusCode: number }
  | { ok: false; status: "http_error"; statusCode: number }
  | { ok: false; status: "timeout" }
  | { ok: false; status: "network_error"; error: unknown };

type SyncAuthSessionOptions = {
  user: AuthSessionUser | null;
  rememberMe?: boolean;
  timeoutMs?: number;
};

/**
 * Syncs feature-flag session context into an HttpOnly cookie managed by a route handler.
 * This is best-effort and intentionally non-blocking for authentication UX.
 */
export async function syncAuthSessionCookie({
  user,
  rememberMe = false,
  timeoutMs = AUTH_SESSION_SYNC_TIMEOUT_MS,
}: SyncAuthSessionOptions): Promise<AuthSessionSyncResult> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch("/api/auth/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ user, rememberMe }),
      signal: controller.signal,
    });

    if (!response.ok) {
      logger.debug?.("[AuthSession] Failed to sync auth session cookie", {
        status: response.status,
      });
      return {
        ok: false,
        status: "http_error",
        statusCode: response.status,
      };
    }

    return {
      ok: true,
      status: "synced",
      statusCode: response.status,
    };
  } catch (error) {
    if (isAbortError(error)) {
      logger.debug?.("[AuthSession] Timed out while syncing auth session cookie", {
        timeoutMs,
      });
      return {
        ok: false,
        status: "timeout",
      };
    }

    logger.debug?.("[AuthSession] Failed to sync auth session cookie", error);
    return {
      ok: false,
      status: "network_error",
      error,
    };
  } finally {
    window.clearTimeout(timeoutId);
  }
}

export async function clearAuthSessionCookie(): Promise<void> {
  try {
    const response = await fetch("/api/auth/session", {
      method: "DELETE",
      credentials: "include",
    });

    if (!response.ok) {
      logger.debug?.("[AuthSession] Failed to clear auth session cookie", {
        status: response.status,
      });
    }
  } catch (error) {
    logger.debug?.("[AuthSession] Failed to clear auth session cookie", error);
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === "AbortError"
    : typeof error === "object" &&
        error !== null &&
        "name" in error &&
        (error as { name?: unknown }).name === "AbortError";
}
