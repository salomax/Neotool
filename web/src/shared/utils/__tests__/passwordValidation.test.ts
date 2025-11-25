import { describe, it, expect } from 'vitest';
import {
  passwordValidationRules,
  validatePassword,
  isPasswordValid,
} from '../passwordValidation';

describe('passwordValidation utilities', () => {
  describe('passwordValidationRules', () => {
    it('should have 5 validation rules', () => {
      expect(passwordValidationRules).toHaveLength(5);
    });

    it('should have rule for minimum 8 characters', () => {
      const rule = passwordValidationRules.find((r) => r.label.includes('8 characters'));
      expect(rule).toBeDefined();
      expect(rule?.test('12345678')).toBe(true);
      expect(rule?.test('1234567')).toBe(false);
    });

    it('should have rule for uppercase letter', () => {
      const rule = passwordValidationRules.find((r) => r.label.includes('uppercase'));
      expect(rule).toBeDefined();
      expect(rule?.test('A')).toBe(true);
      expect(rule?.test('a')).toBe(false);
    });

    it('should have rule for lowercase letter', () => {
      const rule = passwordValidationRules.find((r) => r.label.includes('lowercase'));
      expect(rule).toBeDefined();
      expect(rule?.test('a')).toBe(true);
      expect(rule?.test('A')).toBe(false);
    });

    it('should have rule for number', () => {
      const rule = passwordValidationRules.find((r) => r.label.includes('number'));
      expect(rule).toBeDefined();
      expect(rule?.test('1')).toBe(true);
      expect(rule?.test('a')).toBe(false);
    });

    it('should have rule for special character', () => {
      const rule = passwordValidationRules.find((r) => r.label.includes('special'));
      expect(rule).toBeDefined();
      expect(rule?.test('!')).toBe(true);
      expect(rule?.test('a')).toBe(false);
    });
  });

  describe('validatePassword', () => {
    it('should return invalid for empty password', () => {
      const result = validatePassword('');
      expect(result.isValid).toBe(false);
      expect(result.rules).toHaveLength(5);
    });

    it('should return invalid for password missing all requirements', () => {
      const result = validatePassword('123');
      expect(result.isValid).toBe(false);
      // '123' has numbers, so the number rule is valid
      // Check that at least some rules are invalid
      expect(result.rules.some((r) => r.isValid === false)).toBe(true);
      // Check specific rules that should be invalid
      const lengthRule = result.rules.find((r) => r.label.includes('8 characters'));
      const uppercaseRule = result.rules.find((r) => r.label.includes('uppercase'));
      const lowercaseRule = result.rules.find((r) => r.label.includes('lowercase'));
      const specialRule = result.rules.find((r) => r.label.includes('special'));
      expect(lengthRule?.isValid).toBe(false);
      expect(uppercaseRule?.isValid).toBe(false);
      expect(lowercaseRule?.isValid).toBe(false);
      expect(specialRule?.isValid).toBe(false);
    });

    it('should return valid for password meeting all requirements', () => {
      const result = validatePassword('ValidPass123!');
      expect(result.isValid).toBe(true);
      expect(result.rules.every((r) => r.isValid === true)).toBe(true);
    });

    it('should return invalid for password missing length requirement', () => {
      const result = validatePassword('Pass1!');
      expect(result.isValid).toBe(false);
      const lengthRule = result.rules.find((r) => r.label.includes('8 characters'));
      expect(lengthRule?.isValid).toBe(false);
    });

    it('should return invalid for password missing uppercase', () => {
      const result = validatePassword('validpass123!');
      expect(result.isValid).toBe(false);
      const uppercaseRule = result.rules.find((r) => r.label.includes('uppercase'));
      expect(uppercaseRule?.isValid).toBe(false);
    });

    it('should return invalid for password missing lowercase', () => {
      const result = validatePassword('VALIDPASS123!');
      expect(result.isValid).toBe(false);
      const lowercaseRule = result.rules.find((r) => r.label.includes('lowercase'));
      expect(lowercaseRule?.isValid).toBe(false);
    });

    it('should return invalid for password missing number', () => {
      const result = validatePassword('ValidPass!');
      expect(result.isValid).toBe(false);
      const numberRule = result.rules.find((r) => r.label.includes('number'));
      expect(numberRule?.isValid).toBe(false);
    });

    it('should return invalid for password missing special character', () => {
      const result = validatePassword('ValidPass123');
      expect(result.isValid).toBe(false);
      const specialRule = result.rules.find((r) => r.label.includes('special'));
      expect(specialRule?.isValid).toBe(false);
    });

    it('should return all rules with labels', () => {
      const result = validatePassword('ValidPass123!');
      expect(result.rules).toHaveLength(5);
      result.rules.forEach((rule) => {
        expect(rule.label).toBeTruthy();
        expect(typeof rule.isValid).toBe('boolean');
      });
    });

    it('should handle password with exactly 8 characters', () => {
      const result = validatePassword('Valid1!@');
      expect(result.isValid).toBe(true);
    });

    it('should handle password with various special characters', () => {
      const specialChars = ['!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '+', '-', '=', '[', ']', '{', '}', ';', ':', '"', "'", '\\', '|', ',', '.', '<', '>', '/', '?'];
      specialChars.forEach((char) => {
        const password = `ValidPass1${char}`;
        const result = validatePassword(password);
        const specialRule = result.rules.find((r) => r.label.includes('special'));
        expect(specialRule?.isValid).toBe(true);
      });
    });
  });

  describe('isPasswordValid', () => {
    it('should return false for invalid password', () => {
      expect(isPasswordValid('')).toBe(false);
      expect(isPasswordValid('short')).toBe(false);
      expect(isPasswordValid('nouppercase123!')).toBe(false);
    });

    it('should return true for valid password', () => {
      expect(isPasswordValid('ValidPass123!')).toBe(true);
      expect(isPasswordValid('AnotherValid1@')).toBe(true);
    });

    it('should match validatePassword result', () => {
      const passwords = ['', 'short', 'ValidPass123!', 'nouppercase123!'];
      passwords.forEach((password) => {
        expect(isPasswordValid(password)).toBe(validatePassword(password).isValid);
      });
    });
  });
});

