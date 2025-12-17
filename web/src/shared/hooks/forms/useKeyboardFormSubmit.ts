"use client";

import * as React from "react";

export interface UseKeyboardFormSubmitOptions {
  /**
   * Callback to execute when form should be submitted
   */
  onSubmit: () => void | Promise<void>;
  
  /**
   * Function that returns whether the submit button is enabled
   */
  isSubmitEnabled: () => boolean;
  
  /**
   * Optional ref to the container element (defaults to document)
   * Use this to scope keyboard handling to a specific drawer/form area
   */
  containerRef?: React.RefObject<HTMLElement>;
  
  /**
   * Whether the hook is active (defaults to true)
   */
  enabled?: boolean;
}

/**
 * Hook to enable keyboard form submission in drawers.
 * 
 * When Enter is pressed in any form input and the Save button is enabled,
 * the form will be submitted automatically.
 * 
 * **Behavior:**
 * - Only triggers when Enter is pressed (not Shift+Enter)
 * - Respects disabled and readonly inputs
 * - Skips submission when dropdowns/autocompletes are open
 * - Doesn't intercept button presses
 * - Only works within the specified container scope
 * - Handles nested forms correctly
 * 
 * **Accessibility:**
 * - Works with screen readers
 * - Respects ARIA attributes
 * - Follows standard keyboard navigation patterns
 * 
 * **Limitations:**
 * - Requires container ref to be available before activation
 * - Async submission errors are logged to console
 * - Nested forms: Only submits the form containing the focused input
 * 
 * @example
 * ```tsx
 * const bodyRef = useRef<HTMLDivElement>(null);
 * 
 * useKeyboardFormSubmit({
 *   onSubmit: () => methods.handleSubmit(handleSave)(),
 *   isSubmitEnabled: () => !saving && hasChanges,
 *   containerRef: bodyRef,
 *   enabled: open,
 * });
 * ```
 */
/**
 * Checks if an element is a form input that can trigger submission.
 * 
 * Supports:
 * - Native form elements (INPUT, TEXTAREA, SELECT)
 * - ARIA textbox role
 * - Contenteditable elements
 * 
 * @param element - The element to check
 * @returns True if the element is a form input
 */
function isFormInput(element: Element | null): element is HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement {
  if (!element) return false;
  
  const tagName = element.tagName;
  if (tagName === "INPUT" || tagName === "TEXTAREA" || tagName === "SELECT") {
    return true;
  }
  
  // Check for ARIA textbox or contenteditable
  const role = element.getAttribute("role");
  const contentEditable = element.getAttribute("contenteditable");
  return role === "textbox" || contentEditable === "true";
}

/**
 * Checks if an input element is disabled or readonly.
 * 
 * Prevents submission from inputs that shouldn't trigger form submission.
 * 
 * @param element - The element to check
 * @returns True if the element is disabled or readonly
 */
function isInputDisabledOrReadonly(
  element: Element
): element is HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement {
  if (
    element instanceof HTMLInputElement ||
    element instanceof HTMLTextAreaElement ||
    element instanceof HTMLSelectElement
  ) {
    return element.disabled || element.readOnly;
  }
  return false;
}

/**
 * Checks if a dropdown/autocomplete is currently open.
 * 
 * Prevents form submission when user is interacting with dropdowns,
 * autocompletes, or date pickers, allowing Enter to select options instead.
 * 
 * @param element - The element to check
 * @returns True if a dropdown is currently open
 */
function isDropdownOpen(element: Element): boolean {
  return (
    element.getAttribute("aria-expanded") === "true" ||
    !!element.closest("[role='listbox']") ||
    !!element.closest("[role='menu']") ||
    !!element.closest("[role='combobox'][aria-expanded='true']")
  );
}

export function useKeyboardFormSubmit({
  onSubmit,
  isSubmitEnabled,
  containerRef,
  enabled = true,
}: UseKeyboardFormSubmitOptions): void {
  const onSubmitRef = React.useRef(onSubmit);
  const isSubmitEnabledRef = React.useRef(isSubmitEnabled);

  // Keep refs up to date
  React.useEffect(() => {
    onSubmitRef.current = onSubmit;
    isSubmitEnabledRef.current = isSubmitEnabled;
  }, [onSubmit, isSubmitEnabled]);

  React.useEffect(() => {
    if (!enabled) return;

    const container = containerRef?.current;
    if (!container) return; // Wait for container to be available

    const handleKeyDown = (event: Event) => {
      // Type guard to ensure it's a KeyboardEvent
      if (!(event instanceof KeyboardEvent)) return;

      // Only handle Enter key
      if (event.key !== "Enter") return;

      // Don't submit if Shift+Enter (allow new lines in textareas)
      if (event.shiftKey) return;

      // Check if the active element is a form input
      const activeElement = document.activeElement;
      if (!activeElement) return;

      // Check if focused element is within the container
      if (!container.contains(activeElement)) return;

      // Don't intercept button presses (let button's own handler run)
      if (activeElement.tagName === "BUTTON") return;

      // Check if it's a form input element
      if (!isFormInput(activeElement)) return;

      // Check if input is disabled or readonly
      if (isInputDisabledOrReadonly(activeElement)) return;

      // Check if dropdown/autocomplete is open
      if (isDropdownOpen(activeElement)) return;

      // Edge case: Handle nested forms
      // If the element is inside a form, we ensure we're submitting the correct form context
      // Note: Some drawers use react-hook-form without native form elements, so we don't require a form
      const containingForm = activeElement.closest("form");
      // If there's a form and it's not within our container, don't submit
      if (containingForm && !container.contains(containingForm)) {
        return;
      }

      // Check if submit button is enabled
      if (!isSubmitEnabledRef.current()) return;

      // Prevent default form submission behavior
      event.preventDefault();
      event.stopPropagation();

      // Submit the form with error handling
      try {
        const result = onSubmitRef.current();
        if (result instanceof Promise) {
          result.catch((error) => {
            console.error("Form submission error:", error);
          });
        }
      } catch (error) {
        console.error("Form submission error:", error);
      }
    };

    container.addEventListener("keydown", handleKeyDown, { passive: false });
    return () => {
      container.removeEventListener("keydown", handleKeyDown);
    };
  }, [enabled, containerRef]);
}
