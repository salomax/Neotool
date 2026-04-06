import { useCallback, useState } from 'react';

/**
 * Manages a toggleable filter value that is scoped to a key.
 * When scopeKey changes, the effective value resets to null automatically.
 * Toggling the same value twice clears it.
 */
export function useScopedToggleFilter<T>(
  scopeKey: string,
  isEqual: (a: T, b: T) => boolean = (a: T, b: T) => a === b,
) {
  const [state, setState] = useState<{ scopeKey: string; value: T | null }>(() => ({
    scopeKey,
    value: null,
  }));

  const value = state.scopeKey === scopeKey ? state.value : null;

  const toggle = useCallback(
    (newValue: T | null) => {
      setState((prev) => {
        const current = prev.scopeKey === scopeKey ? prev.value : null;
        const isSame = newValue !== null && current !== null && isEqual(current, newValue);
        return {
          scopeKey,
          value: newValue === null || isSame ? null : newValue,
        };
      });
    },
    [scopeKey, isEqual],
  );

  const clear = useCallback(() => {
    setState({ scopeKey, value: null });
  }, [scopeKey]);

  return { value, toggle, clear };
}
