"use client";

import * as React from "react";

export function useAutoSave<T extends object>(
  values: T,
  onSave: (_v: T) => Promise<void> | void,
  debounceMs = 800,
) {
  const [isSaving, setSaving] = React.useState(false);
  const latest = React.useRef(values);
  const onSaveRef = React.useRef(onSave);

  React.useEffect(() => {
    latest.current = values;
  }, [values]);

  React.useEffect(() => {
    onSaveRef.current = onSave;
  }, [onSave]);

  React.useEffect(() => {
    const id = setTimeout(async () => {
      setSaving(true);
      try {
        await onSaveRef.current(latest.current);
      } finally {
        setSaving(false);
      }
    }, debounceMs);
    return () => clearTimeout(id);
  }, [values, debounceMs]);

  return { isSaving };
}
