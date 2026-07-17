import { describe, it, expect } from 'vitest';
import {
  extractNextFromSearchParams,
  sanitizeRedirectTarget,
  resolvePostLoginRedirect,
  buildAuthHrefWithNext,
} from '../redirect-target';

function paramsFromObject(record: Record<string, string>): URLSearchParams {
  return new URLSearchParams(record);
}

describe('redirect-target', () => {
  describe('extractNextFromSearchParams', () => {
    it('returns null when params are null or undefined', () => {
      expect(extractNextFromSearchParams(null)).toBeNull();
      expect(extractNextFromSearchParams(undefined)).toBeNull();
    });

    it('returns null when next param is missing', () => {
      const params = paramsFromObject({});
      expect(extractNextFromSearchParams(params)).toBeNull();
    });

    it('returns null when next param is empty string', () => {
      const params = paramsFromObject({ next: '' });
      expect(extractNextFromSearchParams(params)).toBeNull();
    });

    it('returns null when next param is whitespace only', () => {
      const params = paramsFromObject({ next: '   ' });
      expect(extractNextFromSearchParams(params)).toBeNull();
    });

    it('returns value when next is present', () => {
      const params = paramsFromObject({ next: '/dashboard' });
      expect(extractNextFromSearchParams(params)).toBe('/dashboard');
    });

    it('trims and returns non-empty value', () => {
      const params = paramsFromObject({ next: '  /profile  ' });
      expect(extractNextFromSearchParams(params)).toBe('/profile');
    });

    it('uses first value when multiple next params exist', () => {
      const params = new URLSearchParams();
      params.append('next', '/first');
      params.append('next', '/second');
      expect(extractNextFromSearchParams(params)).toBe('/first');
    });

    it('works with SearchParams-like object', () => {
      const paramsLike = { get: (k: string) => (k === 'next' ? '/app' : null) };
      expect(extractNextFromSearchParams(paramsLike)).toBe('/app');
    });
  });

  describe('sanitizeRedirectTarget', () => {
    it('returns valid path /', () => {
      expect(sanitizeRedirectTarget('/')).toBe('/');
    });

    it('returns valid path /foo', () => {
      expect(sanitizeRedirectTarget('/foo')).toBe('/foo');
    });

    it('returns valid path /foo/bar', () => {
      expect(sanitizeRedirectTarget('/foo/bar')).toBe('/foo/bar');
    });

    it('returns valid path with query and hash', () => {
      expect(sanitizeRedirectTarget('/page?x=1#section')).toBe('/page?x=1#section');
    });

    it('returns fallback for null', () => {
      expect(sanitizeRedirectTarget(null)).toBe('/');
    });

    it('returns fallback for undefined', () => {
      expect(sanitizeRedirectTarget(undefined)).toBe('/');
    });

    it('returns fallback for empty string', () => {
      expect(sanitizeRedirectTarget('')).toBe('/');
    });

    it('returns fallback for whitespace only', () => {
      expect(sanitizeRedirectTarget('   ')).toBe('/');
    });

    it('returns fallback for // (double slash)', () => {
      expect(sanitizeRedirectTarget('//')).toBe('/');
    });

    it('returns fallback for //evil.com', () => {
      expect(sanitizeRedirectTarget('//evil.com')).toBe('/');
    });

    it('returns fallback for https://evil.com', () => {
      expect(sanitizeRedirectTarget('https://evil.com')).toBe('/');
    });

    it('returns fallback for javascript:alert(1)', () => {
      expect(sanitizeRedirectTarget('javascript:alert(1)')).toBe('/');
    });

    it('returns fallback for data:text/html,...', () => {
      expect(sanitizeRedirectTarget('data:text/html,<script>')).toBe('/');
    });

    it('returns fallback for path without leading slash', () => {
      expect(sanitizeRedirectTarget('dashboard')).toBe('/');
    });

    it('returns fallback for /signin', () => {
      expect(sanitizeRedirectTarget('/signin')).toBe('/');
    });

    it('returns fallback for /signin/', () => {
      expect(sanitizeRedirectTarget('/signin/')).toBe('/');
    });

    it('returns fallback for /signup', () => {
      expect(sanitizeRedirectTarget('/signup')).toBe('/');
    });

    it('returns fallback for /forgot-password', () => {
      expect(sanitizeRedirectTarget('/forgot-password')).toBe('/');
    });

    it('returns fallback for /reset-password', () => {
      expect(sanitizeRedirectTarget('/reset-password')).toBe('/');
    });

    it('returns fallback for /verify-email', () => {
      expect(sanitizeRedirectTarget('/verify-email')).toBe('/');
    });

    it('returns fallback for /verify-email-link', () => {
      expect(sanitizeRedirectTarget('/verify-email-link')).toBe('/');
    });

    it('returns fallback for path under auth segment', () => {
      expect(sanitizeRedirectTarget('/signin/extra')).toBe('/');
    });

    it('uses custom fallback when provided', () => {
      expect(sanitizeRedirectTarget(null, '/home')).toBe('/home');
      expect(sanitizeRedirectTarget('/signin', '/dashboard')).toBe('/dashboard');
    });
  });

  describe('open-redirect prevention', () => {
    it('allows / (root)', () => {
      expect(sanitizeRedirectTarget('/')).toBe('/');
    });

    it('allows /personal-finance?tab=a', () => {
      expect(sanitizeRedirectTarget('/personal-finance?tab=a')).toBe(
        '/personal-finance?tab=a'
      );
    });

    it('rejects https://evil.com', () => {
      expect(sanitizeRedirectTarget('https://evil.com')).toBe('/');
    });

    it('rejects //evil.com', () => {
      expect(sanitizeRedirectTarget('//evil.com')).toBe('/');
    });

    it('rejects javascript:alert(1)', () => {
      expect(sanitizeRedirectTarget('javascript:alert(1)')).toBe('/');
    });

    it('rejects /signin and falls back to /', () => {
      expect(sanitizeRedirectTarget('/signin')).toBe('/');
    });

    it('rejects empty and falls back to /', () => {
      expect(sanitizeRedirectTarget('')).toBe('/');
    });

    it('rejects null and falls back to /', () => {
      expect(sanitizeRedirectTarget(null)).toBe('/');
    });
  });

  describe('resolvePostLoginRedirect', () => {
    it('returns sanitized path when next is valid', () => {
      const params = paramsFromObject({ next: '/dashboard' });
      expect(resolvePostLoginRedirect(params)).toBe('/dashboard');
    });

    it('returns fallback when next is missing', () => {
      const params = paramsFromObject({});
      expect(resolvePostLoginRedirect(params)).toBe('/');
    });

    it('returns fallback when next is invalid', () => {
      const params = paramsFromObject({ next: 'https://evil.com' });
      expect(resolvePostLoginRedirect(params)).toBe('/');
    });

    it('returns fallback when next is auth route', () => {
      const params = paramsFromObject({ next: '/signin' });
      expect(resolvePostLoginRedirect(params)).toBe('/');
    });

    it('uses custom fallback when provided', () => {
      const params = paramsFromObject({});
      expect(resolvePostLoginRedirect(params, '/welcome')).toBe('/welcome');
    });
  });

  describe('buildAuthHrefWithNext', () => {
    it('returns path without query when next is missing', () => {
      expect(buildAuthHrefWithNext('/signup', paramsFromObject({}))).toBe('/signup');
      expect(buildAuthHrefWithNext('/signin', paramsFromObject({}))).toBe('/signin');
    });

    it('preserves a safe invitation next target', () => {
      const next = '/invitations/accept?token=abc';
      const params = paramsFromObject({ next });
      expect(buildAuthHrefWithNext('/signup', params)).toBe(
        `/signup?next=${encodeURIComponent(next)}`,
      );
      expect(buildAuthHrefWithNext('/signin', params)).toBe(
        `/signin?next=${encodeURIComponent(next)}`,
      );
    });

    it('drops unsafe next targets', () => {
      const params = paramsFromObject({ next: 'https://evil.com' });
      expect(buildAuthHrefWithNext('/signup', params)).toBe('/signup');
    });
  });
});
