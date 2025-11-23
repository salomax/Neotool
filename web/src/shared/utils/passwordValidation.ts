/**
 * Password validation rules and utilities
 */

export interface PasswordValidationRule {
  label: string;
  test: (password: string) => boolean;
}

export interface PasswordValidationResult {
  isValid: boolean;
  rules: Array<{
    label: string;
    isValid: boolean;
  }>;
}

/**
 * Password validation rules
 * - At least 8 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one number
 * - At least one special character
 */
export const passwordValidationRules: PasswordValidationRule[] = [
  {
    label: "At least 8 characters",
    test: (password) => password.length >= 8,
  },
  {
    label: "At least one uppercase letter",
    test: (password) => /[A-Z]/.test(password),
  },
  {
    label: "At least one lowercase letter",
    test: (password) => /[a-z]/.test(password),
  },
  {
    label: "At least one number",
    test: (password) => /\d/.test(password),
  },
  {
    label: "At least one special character",
    test: (password) => /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(password),
  },
];

/**
 * Validate password against all rules
 */
export function validatePassword(password: string): PasswordValidationResult {
  const rules = passwordValidationRules.map((rule) => ({
    label: rule.label,
    isValid: rule.test(password),
  }));

  const isValid = rules.every((rule) => rule.isValid);

  return {
    isValid,
    rules,
  };
}

/**
 * Check if password meets all requirements
 */
export function isPasswordValid(password: string): boolean {
  return validatePassword(password).isValid;
}

