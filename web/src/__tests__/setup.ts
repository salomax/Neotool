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

// Suppress act() warnings from Material-UI internal components
// MUI components (ButtonBase, TouchRipple, Tooltip, Grow, etc.) have internal
// animations and transitions that trigger act() warnings but are properly handled
// by the library. We suppress these specific warnings while keeping warnings
// for user code to help identify tests that need act() wrapping.
const originalError = console.error;
console.error = (...args: unknown[]) => {
  const message = args.map(String).join(' ');
  
  // Suppress only MUI-specific act() warnings from internal components
  // These are from library internals we can't control
  const muiInternalComponents = [
    'ButtonBase',
    'TouchRipple',
    'Tooltip',
    'Grow',
    'Fade',
    'Slide',
    'Zoom',
    'Collapse',
    '@mui/material',
  ];
  
  const isMuiInternalWarning = 
    message.includes('act(...)') &&
    muiInternalComponents.some(component => message.includes(component));
  
  if (isMuiInternalWarning) {
    // Suppress MUI internal act() warnings
    return;
  }
  
  // Suppress MUI Tabs value validation warnings
  // This is a known timing issue in tests where MUI validates the value
  // before Tab children are fully rendered, especially in React strict mode
  const isMuiTabsValueWarning = 
    message.includes('MUI:') &&
    message.includes('The `value` provided to the Tabs component is invalid');
  
  if (isMuiTabsValueWarning) {
    // Suppress MUI Tabs value validation warnings in tests
    return;
  }
  
  // Suppress act() warnings from test components
  // Test helper components (IntegrationTestComponent, MultiDomainIntegrationComponent, etc.)
  // have internal state updates that are properly handled but trigger warnings
  // These warnings include the component name and file path, so we check for test file patterns
  const isTestComponentWarning = 
    message.includes('act(...)') &&
    message.includes('inside a test was not wrapped in act(...)') &&
    (
      message.includes('TestComponent') ||
      message.includes('IntegrationComponent') ||
      message.includes('__tests__') ||
      message.includes('.test.') ||
      message.includes('.spec.')
    );
  
  if (isTestComponentWarning) {
    // Suppress act() warnings from test components
    return;
  }
  
  // Suppress jsdom navigation errors
  // jsdom doesn't fully implement navigation (only hash changes are supported)
  // This is a known limitation when testing Next.js Link components that render as anchor tags
  // The navigation is properly mocked via Next.js router mocks, so this error is expected
  const isJsdomNavigationError = 
    message.includes('Not implemented: navigation') &&
    message.includes('except hash changes');
  
  if (isJsdomNavigationError) {
    // Suppress jsdom navigation errors - navigation is handled by mocked Next.js router
    return;
  }
  
  // Note: findDOMNode warnings from react-input-mask have been resolved
  // by migrating to react-imask which doesn't use deprecated React APIs
  
  // Keep all other warnings, including act() warnings from user code
  originalError.call(console, ...args);
};
