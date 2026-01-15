import '@testing-library/jest-dom';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// Mock ResizeObserver for Recharts ResponsiveContainer
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

global.ResizeObserver = ResizeObserverMock as any;

// Automatically cleanup after each test to prevent memory leaks
// This is especially important when running tests in parallel
afterEach(async () => {
  // Cleanup React Testing Library rendered components
  cleanup();
  
  // Clear Apollo Client cache if it exists
  // This prevents cache accumulation across tests which causes memory leaks
  try {
    const { getApolloClient } = await import('@/lib/graphql/client');
    const apolloClient = getApolloClient();
    if (apolloClient) {
      // clearStore() removes all data from the cache
      // This is async and returns a Promise
      await apolloClient.clearStore();
    }
  } catch (error) {
    // Ignore errors if client doesn't exist or isn't initialized
    // This is expected in some test environments
  }
  
  // Note: React Query cache cleanup is handled by test utilities
  // Tests that use React Query should use createTestQueryWrapper() from test-utils
  // which automatically clears the cache after each test
  // The AppQueryProvider's singleton client is not used in tests, so we don't need to clear it
  
  // Clear localStorage and sessionStorage
  // Tests may modify storage which can leak between tests
  if (typeof localStorage !== 'undefined') {
    localStorage.clear();
  }
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.clear();
  }
  
  // Clear all timers (setTimeout, setInterval, etc.)
  // Prevents timer accumulation across tests
  vi.clearAllTimers();
  
  // Clear all mocks
  // Prevents mock state from persisting between tests
  vi.clearAllMocks();
  
  // Encourage GC between tests when --expose-gc is enabled
  if (typeof global !== 'undefined' && typeof (global as any).gc === 'function') {
    (global as any).gc();
  }
});

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
