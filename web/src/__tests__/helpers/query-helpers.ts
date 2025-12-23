import { screen, type Screen } from '@testing-library/react';

/**
 * Helper to safely query elements that may render multiple times.
 * 
 * Components may render multiple times in tests (e.g., React StrictMode, 
 * test setup issues). This helper handles that by using getAllBy* queries
 * and returning the first element, with validation.
 * 
 * @example
 * ```tsx
 * // Instead of:
 * const button = screen.getByTestId('my-button');
 * 
 * // Use:
 * const button = getFirstByTestId('my-button');
 * expect(button).toBeInTheDocument();
 * ```
 */
export function getFirstByTestId(testId: string): HTMLElement {
  const elements = screen.getAllByTestId(testId);
  if (elements.length === 0) {
    throw new Error(`No elements found with testid="${testId}"`);
  }
  return elements[0]!;
}

/**
 * Helper to safely query elements by label text that may render multiple times.
 */
export function getFirstByLabelText(text: string | RegExp): HTMLElement {
  const elements = screen.getAllByLabelText(text);
  if (elements.length === 0) {
    throw new Error(`No elements found with label text="${text}"`);
  }
  return elements[0]!;
}

/**
 * Helper to safely query elements by role that may render multiple times.
 */
export function getFirstByRole(
  role: string,
  options?: { name?: string | RegExp }
): HTMLElement {
  const elements = screen.getAllByRole(role, options);
  if (elements.length === 0) {
    throw new Error(`No elements found with role="${role}"`);
  }
  return elements[0]!;
}

/**
 * Helper to safely query elements by text that may render multiple times.
 */
export function getFirstByText(text: string | RegExp): HTMLElement {
  const elements = screen.getAllByText(text);
  if (elements.length === 0) {
    throw new Error(`No elements found with text="${text}"`);
  }
  return elements[0]!;
}

/**
 * Helper to safely query elements by placeholder text that may render multiple times.
 */
export function getFirstByPlaceholderText(text: string | RegExp): HTMLElement {
  const elements = screen.getAllByPlaceholderText(text);
  if (elements.length === 0) {
    throw new Error(`No elements found with placeholder="${text}"`);
  }
  return elements[0]!;
}

/**
 * Helper to safely query elements by display value that may render multiple times.
 */
export function getFirstByDisplayValue(value: string | RegExp): HTMLElement {
  const elements = screen.getAllByDisplayValue(value);
  if (elements.length === 0) {
    throw new Error(`No elements found with display value="${value}"`);
  }
  return elements[0]!;
}

/**
 * Helper to safely query elements by title that may render multiple times.
 */
export function getFirstByTitle(title: string | RegExp): HTMLElement {
  const elements = screen.getAllByTitle(title);
  if (elements.length === 0) {
    throw new Error(`No elements found with title="${title}"`);
  }
  return elements[0]!;
}

/**
 * Helper to safely query elements by alt text that may render multiple times.
 */
export function getFirstByAltText(text: string | RegExp): HTMLElement {
  const elements = screen.getAllByAltText(text);
  if (elements.length === 0) {
    throw new Error(`No elements found with alt text="${text}"`);
  }
  return elements[0]!;
}

/**
 * Type-safe wrapper around screen queries that handles multiple renders.
 * Automatically uses getAllBy* and returns the first element.
 */
export const safeQueries = {
  getByTestId: getFirstByTestId,
  getByLabelText: getFirstByLabelText,
  getByRole: getFirstByRole,
  getByText: getFirstByText,
  getByPlaceholderText: getFirstByPlaceholderText,
  getByDisplayValue: getFirstByDisplayValue,
  getByTitle: getFirstByTitle,
  getByAltText: getFirstByAltText,
  // Keep queryBy* as-is since they return null when not found
  queryByTestId: screen.queryByTestId.bind(screen),
  queryByLabelText: screen.queryByLabelText.bind(screen),
  queryByRole: screen.queryByRole.bind(screen),
  queryByText: screen.queryByText.bind(screen),
  // Keep getAllBy* as-is for when you need all elements
  getAllByTestId: screen.getAllByTestId.bind(screen),
  getAllByLabelText: screen.getAllByLabelText.bind(screen),
  getAllByRole: screen.getAllByRole.bind(screen),
  getAllByText: screen.getAllByText.bind(screen),
} as const;
