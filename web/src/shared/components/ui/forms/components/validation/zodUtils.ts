// web/src/components/form/validation/zodUtils.ts
import { z } from "zod";
import { onlyDigits } from "../masks/br";

export const isValidCPF = (value: string): boolean => {
  const cpf = onlyDigits(value);
  if (cpf.length !== 11 || /^(\d)\1+$/.test(cpf)) return false;

  const calc = (base: string, factor: number) => {
    let total = 0;
    for (let i = 0; i < base.length; i++) {
      const digit = base[i];
      if (digit) {
        total += parseInt(digit, 10) * (factor - i);
      }
    }
    const rest = total % 11;
    return rest < 2 ? 0 : 11 - rest;
  };
  const d1 = calc(cpf.slice(0, 9), 10);
  const d2 = calc(cpf.slice(0, 10), 11);
  return cpf.endsWith(`${d1}${d2}`);
};

export const isValidCNPJ = (value: string): boolean => {
  const cnpj = onlyDigits(value);
  if (cnpj.length !== 14 || /^(\d)\1+$/.test(cnpj)) return false;

  const calc = (base: string) => {
    const weights = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    let sum = 0;
    for (let i = 0; i < base.length; i++) {
      const digit = base[i];
      const weight = weights[weights.length - base.length + i];
      if (digit && weight) {
        sum += parseInt(digit, 10) * weight;
      }
    }
    const rest = sum % 11;
    return rest < 2 ? 0 : 11 - rest;
  };
  const d1 = calc(cnpj.slice(0, 12));
  const d2 = calc(cnpj.slice(0, 13));
  return cnpj.endsWith(`${d1}${d2}`);
};

export const cpfSchema = z
  .string()
  .nonempty()
  .refine(isValidCPF, "Invalid CPF");
export const cnpjSchema = z
  .string()
  .nonempty()
  .refine(isValidCNPJ, "Invalid CNPJ");
export const cepSchema = z
  .string()
  .regex(/^\d{5}-?\d{3}$/, "Invalid CEP (expected 00000-000)");
