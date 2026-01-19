import { describe, it, expect } from 'vitest';
import { encodeId, decodeId, safeDecodeId } from '../idObfuscation';

describe('idObfuscation utilities', () => {
  describe('encodeId', () => {
    it('should encode a simple numeric ID', () => {
      const id = '26264220';
      const encoded = encodeId(id);
      expect(encoded).toBeTruthy();
      expect(encoded).not.toBe(id);
      expect(encoded).not.toContain('+');
      expect(encoded).not.toContain('/');
      expect(encoded).not.toContain('=');
    });

    it('should encode a string ID', () => {
      const id = 'test-id-123';
      const encoded = encodeId(id);
      expect(encoded).toBeTruthy();
      expect(encoded).not.toBe(id);
    });

    it('should produce URL-safe output', () => {
      const id = '26264220';
      const encoded = encodeId(id);
      // Should not contain characters that need URL encoding
      expect(encodeURIComponent(encoded)).toBe(encoded);
    });

    it('should throw error for empty string', () => {
      expect(() => encodeId('')).toThrow('ID cannot be empty');
    });
  });

  describe('decodeId', () => {
    it('should decode an encoded numeric ID', () => {
      const id = '26264220';
      const encoded = encodeId(id);
      const decoded = decodeId(encoded);
      expect(decoded).toBe(id);
    });

    it('should decode an encoded string ID', () => {
      const id = 'test-id-123';
      const encoded = encodeId(id);
      const decoded = decodeId(encoded);
      expect(decoded).toBe(id);
    });

    it('should handle round-trip encoding/decoding', () => {
      const testIds = [
        '26264220',
        '12345678',
        'C123456',
        'test-id',
        'special-chars-!@#$%',
      ];

      testIds.forEach(id => {
        const encoded = encodeId(id);
        const decoded = decodeId(encoded);
        expect(decoded).toBe(id);
      });
    });

    it('should throw error for empty string', () => {
      expect(() => decodeId('')).toThrow('Encoded ID cannot be empty');
    });

    it('should throw error for invalid encoded string', () => {
      expect(() => decodeId('invalid!@#')).toThrow();
    });
  });

  describe('safeDecodeId', () => {
    it('should decode a valid encoded ID', () => {
      const id = '26264220';
      const encoded = encodeId(id);
      const decoded = safeDecodeId(encoded);
      expect(decoded).toBe(id);
    });

    it('should return null for empty string', () => {
      expect(safeDecodeId('')).toBeNull();
    });

    it('should return null for null input', () => {
      expect(safeDecodeId(null)).toBeNull();
    });

    it('should return null for undefined input', () => {
      expect(safeDecodeId(undefined)).toBeNull();
    });

    it('should return null for invalid encoded string', () => {
      expect(safeDecodeId('invalid!@#')).toBeNull();
    });

    it('should handle round-trip with safe decode', () => {
      const id = '26264220';
      const encoded = encodeId(id);
      const decoded = safeDecodeId(encoded);
      expect(decoded).toBe(id);
    });
  });

  describe('URL safety', () => {
    it('should produce URL-safe encoded strings', () => {
      const testIds = [
        '26264220',
        '12345678',
        'C123456',
        'test-id',
      ];

      testIds.forEach(id => {
        const encoded = encodeId(id);
        // Should not need URL encoding
        expect(encodeURIComponent(encoded)).toBe(encoded);
        // Should work in URL paths
        const url = `/test/${encoded}`;
        expect(url).not.toContain('%');
      });
    });
  });
});
