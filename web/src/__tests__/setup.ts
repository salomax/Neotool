import '@testing-library/jest-dom';

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
