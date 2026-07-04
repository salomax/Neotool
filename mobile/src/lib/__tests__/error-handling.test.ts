import {
  classifyAppError,
  extractErrorCode,
  isRetryableAppError,
  translateAppError,
} from '../error-handling';
import type { TFunction } from 'i18next';

describe('error-handling', () => {
  it('extracts GraphQL auth codes from Apollo-style errors', () => {
    const error = {
      errors: [
        {
          message: 'Invalid credentials',
          extensions: {
            code: 'AUTH_INVALID_CREDENTIALS',
          },
        },
      ],
    };

    expect(extractErrorCode(error)).toBe('AUTH_INVALID_CREDENTIALS');
  });

  it('classifies auth errors and marks them as non-retryable', () => {
    const error = {
      errors: [
        {
          message: 'Session expired',
          extensions: {
            code: 'AUTH_SESSION_EXPIRED',
          },
        },
      ],
    };

    expect(classifyAppError(error)).toEqual({
      category: 'auth',
      code: 'AUTH_SESSION_EXPIRED',
      messageKey: 'errors.codes.AUTH_SESSION_EXPIRED',
      fallbackMessage: 'Authentication failed.',
    });
    expect(isRetryableAppError(error)).toBe(false);
  });

  it('classifies business validation errors with field mappings', () => {
    const error = {
      errors: [
        {
          message: 'Email already exists',
          extensions: {
            code: 'USER_EMAIL_ALREADY_EXISTS',
          },
        },
      ],
    };

    expect(classifyAppError(error)).toEqual({
      category: 'business-validation',
      code: 'USER_EMAIL_ALREADY_EXISTS',
      messageKey: 'errors.codes.USER_EMAIL_ALREADY_EXISTS',
      fallbackMessage: 'Validation failed.',
      fieldErrors: {
        email: 'errors.codes.USER_EMAIL_ALREADY_EXISTS',
      },
    });
    expect(isRetryableAppError(error)).toBe(false);
  });

  it('classifies network errors from transport messages', () => {
    const error = new Error('Network request failed');

    expect(classifyAppError(error)).toEqual({
      category: 'network',
      code: undefined,
      messageKey: 'errors.network',
      fallbackMessage: 'Network error. Please check your connection.',
    });
    expect(isRetryableAppError(error)).toBe(true);
  });

  it('does not retry unknown errors by default', () => {
    const error = new Error('Internal server error');

    expect(classifyAppError(error)).toEqual({
      category: 'unknown',
      code: undefined,
      messageKey: 'errors.generic',
      fallbackMessage: 'Something went wrong. Please try again.',
    });
    expect(isRetryableAppError(error)).toBe(false);
  });

  it('translates the resolved message key', () => {
    const error = {
      errors: [
        {
          message: 'Invalid credentials',
          extensions: {
            code: 'AUTH_INVALID_CREDENTIALS',
          },
        },
      ],
    };

    const translate = ((key: string) => `translated:${key}`) as unknown as TFunction;
    const translated = translateAppError(error, translate);

    expect(translated.message).toBe('translated:errors.codes.AUTH_INVALID_CREDENTIALS');
  });
});
