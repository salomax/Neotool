"use client";

import React, { useMemo, type RefObject } from "react";

/**
 * Hook that combines an external ref (from forwardRef) with an internal ref.
 * 
 * This is useful when you need to:
 * - Forward a ref to parent components
 * - Keep an internal ref for measurements or DOM access
 * 
 * @param externalRef - The ref from forwardRef (can be a function or object ref)
 * @param internalRef - A mutable ref object to store the element internally
 * @returns A ref callback that updates both refs
 * 
 * @example
 * ```tsx
 * const internalRef = useRef<HTMLDivElement | null>(null);
 * const combinedRef = useCombinedRef(ref, internalRef);
 * 
 * return <Box ref={combinedRef} />;
 * ```
 */
export function useCombinedRef<T extends HTMLElement>(
  externalRef: React.ForwardedRef<T>,
  internalRef: RefObject<T>
): (node: T | null) => void {
  return useMemo(() => {
    return (node: T | null) => {
      // Update internal ref
      // eslint-disable-next-line react-hooks/immutability
      (internalRef as React.MutableRefObject<T | null>).current = node;
      
      // Update external ref
      if (typeof externalRef === "function") {
        externalRef(node);
      } else if (externalRef && typeof externalRef === "object" && "current" in externalRef) {
        // eslint-disable-next-line react-hooks/immutability
        (externalRef as React.MutableRefObject<T | null>).current = node;
      }
    };
  }, [externalRef, internalRef]);
}
