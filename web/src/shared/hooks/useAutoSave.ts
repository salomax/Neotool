"use client";

import * as React from "react";

export function useAutoSave<T extends object>(
  values: T,
  onSave: (_v: T) => Promise<void> | void,
  debounceMs = 800,
) {
  const [isSaving, setSaving] = React.useState(false);
  const latest = React.useRef(values);

  React.useEffect(() => {
    latest.current = values;
  }, [values]);

  React.useEffect(() => {
    const id = setTimeout(async () => {
      setSaving(true);
      await onSave(latest.current);
      setSaving(false);
    }, debounceMs);
    return () => clearTimeout(id);
  }, [values, onSave, debounceMs]);

  return { isSaving };
}
