import { z } from "zod";
import { digitsOnly } from "./format";

export function isValidCPF(value: string): boolean {
  const cpf = digitsOnly(value);
  if (cpf.length !== 11) return false;
  if (/^(\d)\1{10}$/.test(cpf)) return false;
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    const digit = cpf[i];
    if (digit) sum += parseInt(digit, 10) * (10 - i);
  }
  let d1 = (sum * 10) % 11;
  if (d1 === 10) d1 = 0;
  const digit9 = cpf[9];
  if (digit9 && d1 !== parseInt(digit9, 10)) return false;

  sum = 0;
  for (let i = 0; i < 10; i++) {
    const digit = cpf[i];
    if (digit) sum += parseInt(digit, 10) * (11 - i);
  }
  let d2 = (sum * 10) % 11;
  if (d2 === 10) d2 = 0;
  const digit10 = cpf[10];
  if (digit10 && d2 !== parseInt(digit10, 10)) return false;
  return true;
}

export function isValidCNPJ(value: string): boolean {
  const cnpj = digitsOnly(value);
  if (cnpj.length !== 14) return false;
  if (/^(\d)\1{13}$/.test(cnpj)) return false;

  const calc = (len: number) => {
    const weights =
      len === 12
        ? [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
        : [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    const slice = cnpj.slice(0, len);
    const sum = slice
      .split("")
      .reduce((acc, n, i) => {
        const weight = weights[i];
        return weight ? acc + parseInt(n, 10) * weight : acc;
      }, 0);
    const mod = sum % 11;
    return mod < 2 ? 0 : 11 - mod;
  };

  const d1 = calc(12);
  const digit12 = cnpj[12];
  if (digit12 && d1 !== parseInt(digit12, 10)) return false;
  const d2 = calc(13);
  const digit13 = cnpj[13];
  if (digit13 && d2 !== parseInt(digit13, 10)) return false;
  return true;
}

export function isValidCEP(value: string): boolean {
  const cep = digitsOnly(value);
  return /^\d{8}$/.test(cep);
}

export const zCPF = z.preprocess(
  (v) => digitsOnly(String(v ?? "")),
  z
    .string()
    .length(11, "CPF deve ter 11 dígitos")
    .refine(isValidCPF, "CPF inválido"),
) as unknown as z.ZodEffects<z.ZodString, string>;

export const zCNPJ = z.preprocess(
  (v) => digitsOnly(String(v ?? "")),
  z
    .string()
    .length(14, "CNPJ deve ter 14 dígitos")
    .refine(isValidCNPJ, "CNPJ inválido"),
) as unknown as z.ZodEffects<z.ZodString, string>;

export const zCEP = z.preprocess(
  (v) => digitsOnly(String(v ?? "")),
  z
    .string()
    .length(8, "CEP deve ter 8 dígitos")
    .refine(isValidCEP, "CEP inválido"),
) as unknown as z.ZodEffects<z.ZodString, string>;

export const zCPFOptional = z.union([z.literal(""), zCPF]);
export const zCNPJOptional = z.union([z.literal(""), zCNPJ]);
export const zCEPOptional = z.union([z.literal(""), zCEP]);
