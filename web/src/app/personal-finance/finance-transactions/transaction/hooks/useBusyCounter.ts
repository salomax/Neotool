import { useCallback, useState } from 'react';

export function useBusyCounter() {
  const [busyCount, setBusyCount] = useState(0);
  const beginBusy = useCallback(() => setBusyCount((c) => c + 1), []);
  const endBusy = useCallback(() => setBusyCount((c) => (c > 0 ? c - 1 : 0)), []);
  return { busyCount, beginBusy, endBusy };
}
