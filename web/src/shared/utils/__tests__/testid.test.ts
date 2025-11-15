import { generateTestId, getTestIdProps } from '../testid';

describe('testid utilities', () => {
  describe('generateTestId', () => {
    it('should generate test id with component name only', () => {
      expect(generateTestId('Paper')).toBe('paper');
      expect(generateTestId('Button')).toBe('button');
    });

    it('should generate test id with component name and name prop', () => {
      expect(generateTestId('Paper', 'search-filters')).toBe('paper-search-filters');
      expect(generateTestId('Button', 'submit')).toBe('button-submit');
      expect(generateTestId('Button', 'add-customer')).toBe('button-add-customer');
    });

    it('should handle empty name prop', () => {
      expect(generateTestId('Paper', '')).toBe('paper');
      expect(generateTestId('Button', undefined)).toBe('button');
    });
  });

  describe('getTestIdProps', () => {
    it('should return data-testid prop with generated test id', () => {
      expect(getTestIdProps('Paper', 'search-filters')).toEqual({
        'data-testid': 'paper-search-filters'
      });
    });

    it('should prioritize existing data-testid over generated one', () => {
      expect(getTestIdProps('Paper', 'search-filters', 'custom-test-id')).toEqual({
        'data-testid': 'custom-test-id'
      });
    });

    it('should work without name prop', () => {
      expect(getTestIdProps('Button')).toEqual({
        'data-testid': 'button'
      });
    });
  });
});
