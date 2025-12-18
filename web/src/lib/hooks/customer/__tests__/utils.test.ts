import { describe, it, expect } from 'vitest';
import { extractErrorMessage } from '../utils';

describe('customer utils', () => {
  // TODO: Re-enable tests when issues are resolved
  it('should re-export extractErrorMessage from shared utils', () => {
    expect(extractErrorMessage).toBeDefined();
    expect(typeof extractErrorMessage).toBe('function');
  });

  it('should work with error instances', () => {
    const error = new Error('Test error');
    expect(extractErrorMessage(error)).toBe('Test error');
  });

  it('should work with default message', () => {
    expect(extractErrorMessage(null)).toBe('An error occurred');
  });
});

