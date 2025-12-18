"use client";

import * as React from "react";

export interface UseDrawerAutoFocusOptions {
  /**
   * Ref to the drawer body container element
   * The hook will search for focusable inputs within this container
   */
  containerRef: React.RefObject<HTMLElement>;
  
  /**
   * Whether the drawer is open
   * Focus will only be set when the drawer opens (transitions from false to true)
   */
  open: boolean;
  
  /**
   * Whether auto-focus is enabled (defaults to true)
   * Set to false to disable auto-focus for specific drawers
   */
  enabled?: boolean;
  
  /**
   * Delay in milliseconds before focusing (defaults to 100)
   * Allows time for drawer animation and content rendering
   */
  delayMs?: number;
}

/**
 * Hook to automatically focus the first focusable input in a drawer when it opens.
 * 
 * **Behavior:**
 * - Finds the first focusable input (input, textarea, select) within the container
 * - Skips disabled, readonly, and hidden inputs
 * - Only focuses when drawer transitions from closed to open
 * - Uses a small delay to allow drawer animation and content rendering
 * 
 * **Focusable Inputs:**
 * - Native form elements: `<input>`, `<textarea>`, `<select>`
 * - MUI TextField components (which render as input elements)
 * - Elements with `tabIndex={0}` or `tabIndex={-1}` (but prefers native inputs)
 * 
 * **Non-Focusable:**
 * - Disabled inputs (`disabled` attribute)
 * - Readonly inputs (`readOnly` attribute)
 * - Hidden inputs (`type="hidden"` or `display: none`)
 * - Inputs with `tabIndex={-1}` (unless no other inputs exist)
 * 
 * **Usage:**
 * ```tsx
 * const bodyRef = useRef<HTMLDivElement>(null);
 * 
 * useDrawerAutoFocus({
 *   containerRef: bodyRef,
 *   open: open,
 *   enabled: true,
 *   delayMs: 100,
 * });
 * 
 * return (
 *   <Drawer open={open}>
 *     <Drawer.Body ref={bodyRef}>
 *       <TextField label="Name" />
 *     </Drawer.Body>
 *   </Drawer>
 * );
 * ```
 * 
 * **Best Practices:**
 * - Always attach the ref to `Drawer.Body` component
 * - Use `enabled: false` if the drawer has conditional inputs that may not be ready
 * - Increase `delayMs` if the drawer has complex animations or async content loading
 * - If a specific input should always be focused, use the `autoFocus` prop on that input instead
 */
export function useDrawerAutoFocus({
  containerRef,
  open,
  enabled = true,
  delayMs = 100,
}: UseDrawerAutoFocusOptions): void {
  const previousOpenRef = React.useRef<boolean>(false);

  React.useEffect(() => {
    // Only focus when drawer transitions from closed to open
    if (!enabled || !open || previousOpenRef.current === open) {
      previousOpenRef.current = open;
      return;
    }

    previousOpenRef.current = open;

    // Wait for drawer animation and content rendering
    const timeoutId = setTimeout(() => {
      const container = containerRef.current;
      if (!container) return;

      // Find all potentially focusable inputs
      const focusableSelectors = [
        'input:not([type="hidden"]):not([disabled]):not([readonly])',
        'textarea:not([disabled]):not([readonly])',
        'select:not([disabled])',
        '[contenteditable="true"]:not([disabled])',
      ].join(', ');

      const focusableElements = Array.from(
        container.querySelectorAll<HTMLElement>(focusableSelectors)
      );

      // Filter out hidden elements
      const visibleFocusableElements = focusableElements.filter((el) => {
        // Check if element is visible
        const style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden') {
          return false;
        }

        // Check if element has zero dimensions
        const rect = el.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) {
          return false;
        }

        return true;
      });

      // Find the first focusable input (prefer native form elements)
      const firstInput = visibleFocusableElements.find((el) => {
        const tagName = el.tagName.toLowerCase();
        return tagName === 'input' || tagName === 'textarea' || tagName === 'select';
      }) || visibleFocusableElements[0];

      // Focus the first input if found
      if (firstInput) {
        try {
          // For MUI TextField, we need to focus the actual input element inside
          if (firstInput.tagName.toLowerCase() === 'div' && firstInput.querySelector('input, textarea, select')) {
            const nestedInput = firstInput.querySelector<HTMLElement>('input, textarea, select');
            if (nestedInput && !nestedInput.hasAttribute('disabled') && !nestedInput.hasAttribute('readonly')) {
              nestedInput.focus();
              return;
            }
          }
          
          // For native inputs or other focusable elements
          firstInput.focus();
        } catch (error) {
          // Silently fail if focus is not possible (e.g., element not yet in DOM)
          console.debug('Failed to focus element in drawer:', error);
        }
      }
    }, delayMs);

    return () => {
      clearTimeout(timeoutId);
    };
  }, [open, enabled, containerRef, delayMs]);
}

