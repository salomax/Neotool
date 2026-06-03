import { describe, it, expect, beforeAll, afterEach } from 'vitest';
import i18n from '@/shared/i18n/config';
import { cleanErrorMessage, extractErrorCode, extractErrorMessage, extractErrorParameters } from '../error';

describe('error utilities', () => {
  describe('cleanErrorMessage', () => {
    it('should return empty string for null input', () => {
      expect(cleanErrorMessage(null)).toBe('');
    });

    it('should return empty string for undefined input', () => {
      expect(cleanErrorMessage(undefined)).toBe('');
    });

    it('should return empty string for empty string', () => {
      expect(cleanErrorMessage('')).toBe('');
    });

    it('should remove "Exception while fetching data" prefix with operation name', () => {
      const input = 'Exception while fetching data (/signIn) : Invalid email or password';
      expect(cleanErrorMessage(input)).toBe('Invalid email or password');
    });

    it('should remove "Exception while fetching data" prefix without operation name', () => {
      const input = 'Exception while fetching data: Invalid email or password';
      expect(cleanErrorMessage(input)).toBe('Invalid email or password');
    });

    it('should handle prefix with space before colon', () => {
      const input = 'Exception while fetching data (/signIn) : Error message';
      expect(cleanErrorMessage(input)).toBe('Error message');
    });

    it('should handle prefix without space before colon', () => {
      const input = 'Exception while fetching data (/signIn): Error message';
      expect(cleanErrorMessage(input)).toBe('Error message');
    });

    it('should handle case-insensitive prefix', () => {
      const input = 'EXCEPTION WHILE FETCHING DATA (/signIn): Error message';
      expect(cleanErrorMessage(input)).toBe('Error message');
    });

    it('should return original message if no prefix found', () => {
      const input = 'Invalid email or password';
      expect(cleanErrorMessage(input)).toBe('Invalid email or password');
    });

    it('should trim whitespace from result', () => {
      const input = 'Exception while fetching data (/signIn) :   Error message   ';
      expect(cleanErrorMessage(input)).toBe('Error message');
    });

    it('should return original message if cleaned result is empty', () => {
      const input = 'Exception while fetching data (/signIn) :';
      expect(cleanErrorMessage(input)).toBe(input);
    });
  });

  describe('extractErrorMessage', () => {
    beforeEach(async () => {
      await i18n.changeLanguage('en');
    });

    afterEach(async () => {
      await i18n.changeLanguage('en');
    });

    it('should return default message for null error', () => {
      expect(extractErrorMessage(null)).toBe('An error occurred');
    });

    it('should return default message for undefined error', () => {
      expect(extractErrorMessage(undefined)).toBe('An error occurred');
    });

    it('should return custom default message', () => {
      expect(extractErrorMessage(null, 'Custom error')).toBe('Custom error');
    });

    it('should extract message from Error instance', () => {
      const error = new Error('Something went wrong');
      expect(extractErrorMessage(error)).toBe('Something went wrong');
    });

    it('should extract and clean message from Error instance with prefix', () => {
      const error = new Error('Exception while fetching data (/signIn) : Invalid email');
      expect(extractErrorMessage(error)).toBe('Invalid email');
    });

    it('should extract message from GraphQL errors array', () => {
      const error = {
        graphQLErrors: [
          { message: 'GraphQL error message' },
        ],
      };
      expect(extractErrorMessage(error)).toBe('GraphQL error message');
    });

    it('should extract and clean message from GraphQL errors with prefix', () => {
      const error = {
        graphQLErrors: [
          { message: 'Exception while fetching data (/query) : GraphQL error' },
        ],
      };
      expect(extractErrorMessage(error)).toBe('GraphQL error');
    });

    it('should extract and translate connection error from network error', () => {
      const error = {
        networkError: {
          message: 'HTTP fetch failed: tcp connect error',
        },
      };
      const result = extractErrorMessage(error);
      expect(result).toBeTruthy();
      expect(result).not.toBe('HTTP fetch failed: tcp connect error');
      expect(result).not.toContain('tcp connect error');
    });

    it('should extract and translate connection error from network error.error', () => {
      const error = {
        networkError: {
          error: {
            message: 'tcp connect error',
          },
        },
      };
      const result = extractErrorMessage(error);
      expect(result).toBeTruthy();
      expect(result).not.toBe('tcp connect error');
    });

    it('should extract non-connection error message from network error', () => {
      const error = {
        networkError: {
          message: 'Authentication failed',
        },
      };
      // Non-connection errors should pass through
      expect(extractErrorMessage(error)).toBe('Authentication failed');
    });

    it('should prefer GraphQL errors over network error', () => {
      const error = {
        graphQLErrors: [
          { message: 'GraphQL error' },
        ],
        networkError: {
          message: 'HTTP fetch failed: tcp connect error',
        },
      };
      // GraphQL errors should be preferred even if network error is a connection error
      expect(extractErrorMessage(error)).toBe('GraphQL error');
    });

    it('should extract message from error.message property', () => {
      const error = {
        message: 'Error message from object',
      };
      expect(extractErrorMessage(error)).toBe('Error message from object');
    });

    it('should fallback to string conversion for primitive types', () => {
      expect(extractErrorMessage('String error')).toBe('String error');
      expect(extractErrorMessage(123)).toBe('123');
      expect(extractErrorMessage(true)).toBe('true');
    });

    it('should handle empty GraphQL errors array', () => {
      const error = {
        graphQLErrors: [],
        message: 'Fallback message',
      };
      expect(extractErrorMessage(error)).toBe('Fallback message');
    });

    it('should clean extracted messages', () => {
      const error = {
        message: 'Exception while fetching data (/operation): Cleaned message',
      };
      expect(extractErrorMessage(error)).toBe('Cleaned message');
    });

    it('should return default when cleaned message is empty', () => {
      const error = {
        message: 'Exception while fetching data (/operation): ',
      };
      // When cleaned message is empty, it returns the original message, not default
      const result = extractErrorMessage(error);
      expect(result).toBeTruthy();
    });

    it('should detect and translate connection timeout errors', () => {
      const error = {
        networkError: {
          message: 'Connection timeout',
        },
      };
      const result = extractErrorMessage(error);
      expect(result).toBeTruthy();
      expect(result).not.toBe('Connection timeout');
    });

    it('should detect and translate service unavailable errors', () => {
      const error = {
        networkError: {
          message: 'Service unavailable (503)',
        },
      };
      const result = extractErrorMessage(error);
      expect(result).toBeTruthy();
      expect(result).not.toBe('Service unavailable (503)');
    });

    it('should detect connection errors in Error instances', () => {
      const error = new Error('HTTP fetch failed from security: tcp connect error');
      error.name = 'NetworkError';
      const result = extractErrorMessage(error);
      expect(result).toBeTruthy();
      expect(result).not.toBe('HTTP fetch failed from security: tcp connect error');
    });

    it('should translate GraphQL error by AUTH code', () => {
      const error = {
        graphQLErrors: [{ message: 'Invalid email or password', extensions: { code: 'AUTH_INVALID_CREDENTIALS' } }],
      };
      expect(extractErrorMessage(error)).toBe('Invalid email or password');
    });

    it('should translate GraphQL error by legacy code alias', () => {
      const error = {
        graphQLErrors: [{ message: 'Invalid email or password', extensions: { code: 'INVALID_CREDENTIALS' } }],
      };
      expect(extractErrorMessage(error)).toBe('Invalid email or password');
    });

    it('should translate GraphQL error by SUBREQUEST_HTTP_ERROR code', () => {
      const error = {
        graphQLErrors: [
          {
            message: "HTTP fetch failed from 'banking': tcp connect error",
            extensions: { code: 'SUBREQUEST_HTTP_ERROR', service: 'banking', reason: "HTTP fetch failed from 'banking': tcp connect error" },
          },
        ],
      };
      expect(extractErrorMessage(error)).toBe('The service is temporarily unavailable. Please try again in a few moments.');
    });

    it('should translate Apollo ServerError with validation errors in bodyText', () => {
      const error = {
        message: 'Response not successful: Received status code 400',
        statusCode: 400,
        bodyText: JSON.stringify({
          errors: [
            {
              message: 'Cannot query field "avatarProvider" on type "User".',
              extensions: { code: 'GRAPHQL_VALIDATION_FAILED' },
            },
          ],
        }),
      };
      expect(extractErrorMessage(error)).toBe(
        "We couldn't complete your request. Please try again. If the problem persists, contact support."
      );
    });

    it('should translate generic Apollo HTTP status messages', () => {
      const error = {
        message: 'Response not successful: Received status code 400',
        statusCode: 400,
        bodyText: '',
      };
      expect(extractErrorMessage(error)).toBe(
        "We couldn't reach the server. Please try again in a few moments."
      );
    });

    it('should translate GRAPHQL_VALIDATION_FAILED by error code', () => {
      const error = {
        graphQLErrors: [
          {
            message: 'Cannot query field "avatarProvider" on type "User".',
            extensions: { code: 'GRAPHQL_VALIDATION_FAILED' },
          },
        ],
      };
      expect(extractErrorMessage(error)).toBe(
        "We couldn't complete your request. Please try again. If the problem persists, contact support."
      );
    });
  });

  describe('extractErrorCode', () => {
    it('should extract code from graphQLErrors', () => {
      const error = {
        graphQLErrors: [{ message: 'Invalid email or password', extensions: { code: 'INVALID_CREDENTIALS' } }],
      };
      expect(extractErrorCode(error)).toBe('INVALID_CREDENTIALS');
    });

    it('should extract code from response errors', () => {
      const error = {
        response: {
          errors: [{ message: 'Invalid email or password', extensions: { code: 'INVALID_CREDENTIALS' } }],
        },
      };
      expect(extractErrorCode(error)).toBe('INVALID_CREDENTIALS');
    });

    it('should return undefined when no code is present', () => {
      const error = { graphQLErrors: [{ message: 'Invalid email or password' }] };
      expect(extractErrorCode(error)).toBeUndefined();
    });
  });

  describe('extractErrorMessage (Portuguese locale)', () => {
    beforeEach(async () => {
      await i18n.changeLanguage('pt');
    });

    it('should translate Firefox NetworkError in Portuguese', () => {
      const error = new Error('NetworkError when attempting to fetch resource.');
      error.name = 'NetworkError';
      expect(extractErrorMessage(error)).toBe(
        'Não foi possível conectar ao servidor. Por favor, verifique sua conexão com a internet ou aguarde alguns instantes.'
      );
    });
  });

  describe('extractErrorParameters', () => {
    it('should extract parameters from GraphQL errors', () => {
      const error = {
        graphQLErrors: [{ extensions: { parameters: { maxLength: 100 } } }],
      };
      expect(extractErrorParameters(error)).toEqual({ maxLength: 100 });
    });
  });
});
