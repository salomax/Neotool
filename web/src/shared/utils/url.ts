import { encodeId } from "./idObfuscation";

export function toSearchString(params: Record<string, any>) {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === "") return;
    sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : "";
}

export function parseNumberParam(v: string | null, fallback = 0) {
  if (v == null) return fallback;
  const n = parseInt(v, 10);
  return Number.isFinite(n) ? n : fallback;
}

/**
 * Creates a URL-friendly slug from text.
 * - Converts to lowercase
 * - Normalizes accents (NFD) and removes diacritics
 * - Replaces non-alphanumeric chars with hyphens
 * - Removes leading/trailing hyphens
 */
export function toSlug(text: string): string {
  if (!text) return "";
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

/**
 * Creates a semantic path segment combining a slug and an optional ID.
 * Uses a dot (.) as separator to avoid collisions with hyphens in the encoded ID.
 *
 * @param name - The text to slugify (e.g., institution name)
 * @param id - Optional ID to encode and append. If provided, it will be obfuscated.
 * @returns The semantic path segment (e.g., "banco-do-brasil.encodedId" or "banco-do-brasil")
 */
export function createSemanticPath(name: string, id?: string): string {
  const slug = toSlug(name);
  if (!id) return slug;
  const encoded = encodeId(id);
  // Use dot separator because encoded ID (base64url) may contain hyphens but not dots
  return `${slug}.${encoded}`;
}

/**
 * Extracts the encoded ID from a semantic path segment.
 * Expects the ID to be after the last dot (.).
 * Falls back to last hyphen if no dot is found (for backward compatibility or user manual entry).
 *
 * @param segment - The path segment
 * @returns The encoded ID part, or the original segment if no separator found
 */
export function extractIdFromSemanticPath(segment: string): string {
  if (!segment) return "";
  
  const lastDot = segment.lastIndexOf('.');
  if (lastDot !== -1) {
    return segment.substring(lastDot + 1);
  }
  
  // Fallback for hyphen separator (if used manually or in legacy links)
  // Note: This is risky if the encoded ID contains hyphens, but we try our best.
  const lastHyphen = segment.lastIndexOf('-');
  if (lastHyphen !== -1) {
    return segment.substring(lastHyphen + 1);
  }
  
  return segment;
}
