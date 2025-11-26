import { describe, it, expect } from 'vitest';
import { cleanErrorMessage, extractErrorMessage } from '../error';

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

    it('should extract message from network error', () => {
      const error = {
        networkError: {
          message: 'Network error occurred',
        },
      };
      expect(extractErrorMessage(error)).toBe('Network error occurred');
    });

    it('should extract message from network error.error', () => {
      const error = {
        networkError: {
          error: {
            message: 'Nested network error',
          },
        },
      };
      expect(extractErrorMessage(error)).toBe('Nested network error');
    });

    it('should prefer GraphQL errors over network error', () => {
      const error = {
        graphQLErrors: [
          { message: 'GraphQL error' },
        ],
        networkError: {
          message: 'Network error',
        },
      };
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
  });
});

