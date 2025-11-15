// web/src/components/form/masks/br.ts
export function onlyDigits(value: string | number | null | undefined): string {
  return String(value ?? "").replace(/\D+/g, "");
}

export function maskCPF(value: string): string {
  const digits = onlyDigits(value).slice(0, 11);
  return digits
    .replace(/^(\d{3})(\d)/, "$1.$2")
    .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1-$2");
}

export function maskCNPJ(value: string): string {
  const digits = onlyDigits(value).slice(0, 14);
  return digits
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2");
}

export function maskCEP(value: string): string {
  const digits = onlyDigits(value).slice(0, 8);
  return digits.replace(/^(\d{5})(\d{1,3})?$/, (_, p1, p2) =>
    p2 ? `${p1}-${p2}` : p1,
  );
}

export function maskPhoneBR(value: string): string {
  // (99) 99999-9999 or (99) 9999-9999
  const digits = onlyDigits(value).slice(0, 11);
  if (digits.length <= 10) {
    return digits
      .replace(/^(\d{2})(\d)/, "($1) $2")
      .replace(/(\d{4})(\d)/, "$1-$2");
  }
  return digits
    .replace(/^(\d{2})(\d)/, "($1) $2")
    .replace(/(\d{5})(\d)/, "$1-$2");
}

export function parseLocaleNumber(
  input: string,
  locale?: string,
): number | null {
  const example = 1000.5;
  const formatted = Intl.NumberFormat(locale).format(example);
  const group = formatted.match(/[^0-9]/)?.[0] ?? ",";
  const decimal = formatted.replace(/[0-9]/g, "")[1] ?? ".";

  let normalized = input
    .replace(new RegExp("\\" + group, "g"), "")
    .replace(new RegExp("\\" + decimal), ".");
  const n = parseFloat(normalized);
  return isNaN(n) ? null : n;
}

export function formatCurrency(
  value: number | null | undefined,
  currency = "BRL",
  locale?: string,
): string {
  const v = typeof value === "number" ? value : 0;
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(v);
}
