'use client';

import * as React from 'react';

type StorageType = 'local' | 'session';

function getStorage(storage: StorageType): Storage | null {
  if (typeof window === 'undefined') return null;
  return storage === 'session' ? window.sessionStorage : window.localStorage;
}

export function useStoredState<T>(
  key: string,
  defaultValue: T,
  storage: StorageType = 'local',
): [T, React.Dispatch<React.SetStateAction<T>>] {
  const [value, setValue] = React.useState<T>(defaultValue);

  React.useEffect(() => {
    const targetStorage = getStorage(storage);
    if (!targetStorage) return;

    try {
      const stored = targetStorage.getItem(key);
      if (stored == null) return;
      setValue(JSON.parse(stored) as T);
    } catch {
      return;
    }
  }, [key, storage]);

  React.useEffect(() => {
    const targetStorage = getStorage(storage);
    if (!targetStorage) return;

    try {
      targetStorage.setItem(key, JSON.stringify(value));
    } catch {
      return;
    }
  }, [key, storage, value]);

  return [value, setValue];
}

export type { StorageType };
