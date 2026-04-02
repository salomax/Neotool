import type { StorageState } from '@playwright/test';
import { currentConfig } from '../config/environments';
import type { AuthSession } from './graphql';

interface BuildAuthStorageStateOptions {
  rememberMe?: boolean;
}

/**
 * Creates a Playwright storage state object compatible with the app's auth model.
 * We persist auth in localStorage so the page starts authenticated without a UI login.
 */
export function buildAuthStorageState(
  session: AuthSession,
  options: BuildAuthStorageStateOptions = {}
): StorageState {
  const { rememberMe = true } = options;
  const authEntries = [
    { name: 'auth_token', value: session.token },
    { name: 'auth_user', value: JSON.stringify(session.user) },
  ];

  if (session.refreshToken) {
    authEntries.push({ name: 'auth_refresh_token', value: session.refreshToken });
  }

  return {
    cookies: [],
    origins: [
      {
        origin: new URL(currentConfig.baseURL).origin,
        localStorage: rememberMe ? authEntries : [],
      },
    ],
  };
}
