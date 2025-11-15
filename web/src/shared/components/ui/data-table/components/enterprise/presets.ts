// src/components/table/enterprise/presets.ts
export type ColumnState = any[];

export function savePreset(key: string, state: ColumnState) {
  try {
    localStorage.setItem(key, JSON.stringify(state));
  } catch {
    // Ignore localStorage errors
  }
}

export function loadPreset(key: string): ColumnState | null {
  try {
    const s = localStorage.getItem(key);
    return s ? (JSON.parse(s) as ColumnState) : null;
  } catch {
    return null;
  }
}

export function clearPreset(key: string) {
  try {
    localStorage.removeItem(key);
  } catch {
    // Ignore localStorage errors
  }
}
