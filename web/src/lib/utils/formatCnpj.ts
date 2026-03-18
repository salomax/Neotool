const ONLY_DIGITS = /\D+/g;

export function formatCnpj(value: string | null | undefined): string {
  const digits = String(value ?? "").replace(ONLY_DIGITS, "");
  if (digits.length !== 14) return String(value ?? "");

  // 12.345.678/0001-95
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12, 14)}`;
}

