// web/src/components/form/helpers/formHelpers.ts
"use client";

import * as React from "react";
import type { FieldErrors } from "react-hook-form";

export function focusFirstError(
  errors: FieldErrors,
  root: Document | HTMLElement = document,
) {
  const firstKey = findFirstErrorPath(errors);
  if (!firstKey) return;
  const name = firstKey;
  const el = root.querySelector(
    `[name="${cssEscape(name)}"]`,
  ) as HTMLElement | null;
  if (el && typeof (el as any).focus === "function") {
    (el as any).focus();
    if (typeof el.scrollIntoView === "function") {
      el.scrollIntoView({ block: "center", behavior: "smooth" });
    }
  }
}

function findFirstErrorPath(
  errors: FieldErrors,
  parentPath: string[] = [],
): string | null {
  for (const key of Object.keys(errors)) {
    const val: any = (errors as any)[key];
    if (!val) continue;
    if ("message" in val || "type" in val) {
      return [...parentPath, key].join(".");
    }
    if (typeof val === "object") {
      const nested = findFirstErrorPath(val as any, [...parentPath, key]);
      if (nested) return nested;
    }
  }
  return null;
}

// Minimal CSS.escape polyfill for name selectors
function cssEscape(str: string): string {
  return str.replace(/"/g, '\\"');
}

// Persist form values to localStorage
export function useFormPersist<T extends Record<string, any>>(
  key: string,
  watch: (_names?: string | string[]) => any,
  setValue: (_name: any, _value: any) => void,
) {
  React.useEffect(() => {
    try {
      const raw = localStorage.getItem(key);
      if (raw) {
        const data = JSON.parse(raw);
        Object.entries(data).forEach(([k, v]) => setValue(k, v));
      }
    } catch {
      // Ignore localStorage errors
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  React.useEffect(() => {
    const values = watch();
    try {
      localStorage.setItem(key, JSON.stringify(values));
    } catch {
      // Ignore localStorage errors
    }
  }, [key, watch]);
}
