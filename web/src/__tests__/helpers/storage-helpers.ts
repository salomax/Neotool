import { vi } from 'vitest';

/**
 * Creates a mock storage object (localStorage or sessionStorage) for testing.
 * 
 * @returns Mock storage object with Map-based implementation
 * 
 * @example
 * ```tsx
 * const localStorageMock = createMockStorage();
 * Object.defineProperty(window, 'localStorage', {
 *   value: localStorageMock,
 *   writable: true,
 *   configurable: true,
 * });
 * ```
 */
export function createMockStorage() {
  const storage = new Map<string, string>();
  return {
    getItem: vi.fn((key: string) => storage.get(key) || null),
    setItem: vi.fn((key: string, value: string) => {
      storage.set(key, value);
    }),
    removeItem: vi.fn((key: string) => {
      storage.delete(key);
    }),
    clear: vi.fn(() => {
      storage.clear();
    }),
    get length() {
      return storage.size;
    },
    key: vi.fn((index: number) => {
      const keys = Array.from(storage.keys());
      return keys[index] || null;
    }),
  };
}

/**
 * Sets up both localStorage and sessionStorage mocks for a test.
 * 
 * @returns Object with localStorageMock and sessionStorageMock
 * 
 * @example
 * ```tsx
 * const { localStorageMock, sessionStorageMock } = setupStorageMocks();
 * 
 * // Use in tests
 * localStorageMock.setItem('key', 'value');
 * expect(localStorageMock.getItem('key')).toBe('value');
 * ```
 */
export function setupStorageMocks() {
  const localStorageMock = createMockStorage();
  const sessionStorageMock = createMockStorage();

  Object.defineProperty(window, 'localStorage', {
    value: localStorageMock,
    writable: true,
    configurable: true,
  });

  Object.defineProperty(window, 'sessionStorage', {
    value: sessionStorageMock,
    writable: true,
    configurable: true,
  });

  return {
    localStorageMock,
    sessionStorageMock,
  };
}

/**
 * Clears all storage mocks and restores original behavior.
 */
export function clearStorageMocks() {
  if (typeof window !== 'undefined') {
    delete (window as any).localStorage;
    delete (window as any).sessionStorage;
  }
}
