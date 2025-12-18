import { describe, it, expect } from 'vitest';
import { toSearchString, parseNumberParam } from '../url';

describe('url utilities', () => {
  describe('toSearchString', () => {
    it('should return empty string for empty object', () => {
      expect(toSearchString({})).toBe('');
    });

    it('should create search string with single param', () => {
      expect(toSearchString({ page: 1 })).toBe('?page=1');
    });

    it('should create search string with multiple params', () => {
      expect(toSearchString({ page: 1, limit: 10 })).toBe('?page=1&limit=10');
    });

    it('should exclude undefined values', () => {
      expect(toSearchString({ page: 1, limit: undefined })).toBe('?page=1');
    });

    it('should exclude null values', () => {
      expect(toSearchString({ page: 1, limit: null })).toBe('?page=1');
    });

    it('should exclude empty string values', () => {
      expect(toSearchString({ page: 1, search: '' })).toBe('?page=1');
    });

    it('should convert values to strings', () => {
      expect(toSearchString({ page: 1, active: true })).toBe('?page=1&active=true');
    });

    it('should handle string values', () => {
      expect(toSearchString({ name: 'test' })).toBe('?name=test');
    });

    it('should handle number values', () => {
      expect(toSearchString({ count: 42 })).toBe('?count=42');
    });

    it('should handle boolean values', () => {
      expect(toSearchString({ active: true, disabled: false })).toBe('?active=true&disabled=false');
    });

    it('should URL encode special characters', () => {
      expect(toSearchString({ query: 'hello world' })).toBe('?query=hello+world');
    });

    it('should handle complex object with mixed types', () => {
      const params = {
        page: 1,
        limit: 10,
        search: 'test',
        active: true,
        excluded: undefined,
        empty: '',
        nullValue: null,
      };
      const result = toSearchString(params);
      expect(result).toContain('page=1');
      expect(result).toContain('limit=10');
      expect(result).toContain('search=test');
      expect(result).toContain('active=true');
      expect(result).not.toContain('excluded');
      expect(result).not.toContain('empty');
      expect(result).not.toContain('nullValue');
    });
  });

  describe('parseNumberParam', () => {
    it('should return fallback for null input', () => {
      expect(parseNumberParam(null)).toBe(0);
    });

    it('should return fallback for null input with custom fallback', () => {
      expect(parseNumberParam(null, 10)).toBe(10);
    });

    it('should parse valid number string', () => {
      expect(parseNumberParam('123')).toBe(123);
    });

    it('should parse zero', () => {
      expect(parseNumberParam('0')).toBe(0);
    });

    it('should parse negative numbers', () => {
      expect(parseNumberParam('-123')).toBe(-123);
    });

    it('should return fallback for invalid number string', () => {
      expect(parseNumberParam('abc')).toBe(0);
    });

    it('should return custom fallback for invalid number string', () => {
      expect(parseNumberParam('abc', 42)).toBe(42);
    });

    it('should return fallback for empty string', () => {
      expect(parseNumberParam('')).toBe(0);
    });

    it('should return fallback for decimal string (parseInt behavior)', () => {
      expect(parseNumberParam('123.45')).toBe(123);
    });

    it('should handle very large numbers', () => {
      expect(parseNumberParam('999999999')).toBe(999999999);
    });

    it('should handle string with leading zeros', () => {
      expect(parseNumberParam('007')).toBe(7);
    });

    it('should handle string with whitespace (parseInt behavior)', () => {
      expect(parseNumberParam('  123  ')).toBe(123);
    });
  });
});

