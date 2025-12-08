import '@testing-library/jest-dom';

// Extend Window interface to include getCurrentEventPriority
declare global {
  interface Window {
    getCurrentEventPriority?: () => number;
  }
}

// Fix for React DOM 18.3.1 "window is not defined" error in tests
// React DOM's development build tries to access window.getCurrentEventPriority
// during state updates, which can fail in async contexts in jsdom
// This ensures window is always available globally
if (typeof window === 'undefined' && typeof globalThis !== 'undefined') {
  // @ts-ignore - jsdom should provide window, but we ensure it exists
  globalThis.window = globalThis;
}

// Ensure window has all necessary properties for React DOM
if (typeof window !== 'undefined') {
  // Ensure window.document exists (jsdom should provide this, but we ensure it)
  if (!window.document && typeof document !== 'undefined') {
    Object.defineProperty(window, 'document', {
      value: document,
      writable: true,
      configurable: true,
    });
  }
  
  // Mock getCurrentEventPriority if it doesn't exist (React DOM 18.3.1 expects this)
  if (!window.getCurrentEventPriority) {
    Object.defineProperty(window, 'getCurrentEventPriority', {
      value: () => 0, // Default priority
      writable: true,
      configurable: true,
    });
  }
}

// Suppress React act() warnings in tests
// These warnings come from React when state updates occur outside of act().
// Many libraries (Material-UI, third-party components) have internal async state updates
// that are properly handled by the library but trigger these warnings in tests.
// Suppressing these warnings doesn't affect test correctness.
const originalError = console.error;
console.error = (...args: unknown[]) => {
  // Check all arguments for React act() warnings
  const message = args.map(String).join(' ');
  if (
    message.includes('Warning: An update to') &&
    message.includes('inside a test was not wrapped in act(...)')
  ) {
    // Suppress all React act() warnings
    return;
  }
  // Also suppress the general act() warning message
  if (
    message.includes('act(...)') &&
    (message.includes('not wrapped') || message.includes('wrapped in act'))
  ) {
    return;
  }
  originalError.call(console, ...args);
};
