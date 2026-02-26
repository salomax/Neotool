"use client";

import { logger } from "@/shared/utils/logger";
import type { AuthSessionUser } from "./shared";

type SyncAuthSessionOptions = {
  user: AuthSessionUser | null;
  rememberMe?: boolean;
};

/**
 * Syncs feature-flag session context into an HttpOnly cookie managed by a route handler.
 * This is best-effort and intentionally non-blocking for authentication UX.
 */
export async function syncAuthSessionCookie({
  user,
  rememberMe = false,
}: SyncAuthSessionOptions): Promise<void> {
  try {
    const response = await fetch("/api/auth/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ user, rememberMe }),
    });

    if (!response.ok) {
      logger.debug?.("[AuthSession] Failed to sync auth session cookie", {
        status: response.status,
      });
    }
  } catch (error) {
    logger.debug?.("[AuthSession] Failed to sync auth session cookie", error);
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
