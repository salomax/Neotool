import { describe, it, expect } from 'vitest';
import { isValidCPF, isValidCNPJ, cpfSchema, cnpjSchema, cepSchema } from '../zodUtils';

describe('zodUtils', () => {
  describe('isValidCPF', () => {
    it('should return true for valid CPF', () => {
      // Valid CPF: 11144477735
      expect(isValidCPF('111.444.777-35')).toBe(true);
      expect(isValidCPF('11144477735')).toBe(true);
    });

    it('should return false for CPF with wrong length', () => {
      expect(isValidCPF('123456789')).toBe(false);
      expect(isValidCPF('123456789012')).toBe(false);
      expect(isValidCPF('')).toBe(false);
    });

    it('should return false for CPF with all same digits', () => {
      expect(isValidCPF('111.111.111-11')).toBe(false);
      expect(isValidCPF('22222222222')).toBe(false);
      expect(isValidCPF('000.000.000-00')).toBe(false);
    });

    it('should return false for invalid CPF check digits', () => {
      expect(isValidCPF('111.444.777-34')).toBe(false);
      expect(isValidCPF('11144477734')).toBe(false);
    });

    it('should handle CPF with special characters', () => {
      expect(isValidCPF('111.444.777-35')).toBe(true);
      expect(isValidCPF('11144477735')).toBe(true);
    });

    it('should return false for null or undefined', () => {
      expect(isValidCPF('')).toBe(false);
    });
  });

  describe('isValidCNPJ', () => {
    it('should return true for valid CNPJ', () => {
      // Valid CNPJ: 11222333000181
      expect(isValidCNPJ('11.222.333/0001-81')).toBe(true);
      expect(isValidCNPJ('11222333000181')).toBe(true);
    });

    it('should return false for CNPJ with wrong length', () => {
      expect(isValidCNPJ('1234567890123')).toBe(false);
      expect(isValidCNPJ('123456789012345')).toBe(false);
      expect(isValidCNPJ('')).toBe(false);
    });

    it('should return false for CNPJ with all same digits', () => {
      expect(isValidCNPJ('11.111.111/1111-11')).toBe(false);
      expect(isValidCNPJ('22222222222222')).toBe(false);
      expect(isValidCNPJ('00.000.000/0000-00')).toBe(false);
    });

    it('should return false for invalid CNPJ check digits', () => {
      expect(isValidCNPJ('11.222.333/0001-80')).toBe(false);
      expect(isValidCNPJ('11222333000180')).toBe(false);
    });

    it('should handle CNPJ with special characters', () => {
      expect(isValidCNPJ('11.222.333/0001-81')).toBe(true);
      expect(isValidCNPJ('11222333000181')).toBe(true);
    });
  });

  describe('cpfSchema', () => {
    it('should validate valid CPF', async () => {
      const result = await cpfSchema.safeParseAsync('111.444.777-35');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('111.444.777-35');
      }
    });

    it('should reject empty string', async () => {
      const result = await cpfSchema.safeParseAsync('');
      expect(result.success).toBe(false);
    });

    it('should reject invalid CPF', async () => {
      const result = await cpfSchema.safeParseAsync('111.111.111-11');
      expect(result.success).toBe(false);
    });
  });

  describe('cnpjSchema', () => {
    it('should validate valid CNPJ', async () => {
      const result = await cnpjSchema.safeParseAsync('11.222.333/0001-81');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('11.222.333/0001-81');
      }
    });

    it('should reject empty string', async () => {
      const result = await cnpjSchema.safeParseAsync('');
      expect(result.success).toBe(false);
    });

    it('should reject invalid CNPJ', async () => {
      const result = await cnpjSchema.safeParseAsync('11.111.111/1111-11');
      expect(result.success).toBe(false);
    });
  });

  describe('cepSchema', () => {
    it('should validate CEP with dash', async () => {
      const result = await cepSchema.safeParseAsync('12345-678');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('12345-678');
      }
    });

    it('should validate CEP without dash', async () => {
      const result = await cepSchema.safeParseAsync('12345678');
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toBe('12345678');
      }
    });

    it('should reject invalid CEP format', async () => {
      const result1 = await cepSchema.safeParseAsync('1234-567');
      expect(result1.success).toBe(false);

      const result2 = await cepSchema.safeParseAsync('1234567');
      expect(result2.success).toBe(false);

      const result3 = await cepSchema.safeParseAsync('123456789');
      expect(result3.success).toBe(false);
    });

    it('should reject empty string', async () => {
      const result = await cepSchema.safeParseAsync('');
      expect(result.success).toBe(false);
    });
  });
});
