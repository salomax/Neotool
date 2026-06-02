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
  const baseUrl = new URL(currentConfig.baseURL);
  const authEntries = [
    { name: 'auth_token', value: session.token },
    { name: 'auth_user', value: JSON.stringify(session.user) },
  ];

  if (session.refreshToken) {
    authEntries.push({ name: 'auth_refresh_token', value: session.refreshToken });
  }

  return {
    cookies: [
      {
        name: 'auth_hint',
        value: '1',
        domain: baseUrl.hostname,
        path: '/',
        httpOnly: false,
        secure: baseUrl.protocol === 'https:',
        sameSite: 'Lax',
        expires: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 7,
      },
    ],
    origins: [
      {
        origin: baseUrl.origin,
        localStorage: rememberMe ? authEntries : [],
      },
    ],
  };
}
