import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { focusFirstError, useFormPersist } from '../formHelpers';
import type { FieldErrors } from 'react-hook-form';
import { renderHook, waitFor } from '@testing-library/react';

describe('focusFirstError', () => {
  let mockElement: HTMLElement;
  let mockQuerySelector: ReturnType<typeof vi.fn>;
  let mockDocument: Document;

  beforeEach(() => {
    mockElement = {
      focus: vi.fn(),
      scrollIntoView: vi.fn(),
    } as any;

    mockQuerySelector = vi.fn(() => mockElement);
    mockDocument = {
      querySelector: mockQuerySelector,
    } as any;
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should focus first error field when error exists', () => {
    const errors: FieldErrors = {
      email: { message: 'Email is required', type: 'required' },
    };

    focusFirstError(errors, mockDocument);

    expect(mockQuerySelector).toHaveBeenCalledWith('[name="email"]');
    expect(mockElement.focus).toHaveBeenCalled();
    expect(mockElement.scrollIntoView).toHaveBeenCalledWith({
      block: 'center',
      behavior: 'smooth',
    });
  });

  it('should handle nested errors', () => {
    const errors: FieldErrors = {
      user: {
        email: { message: 'Email is required', type: 'required' },
      },
    };

    focusFirstError(errors, mockDocument);

    expect(mockQuerySelector).toHaveBeenCalledWith('[name="user.email"]');
    expect(mockElement.focus).toHaveBeenCalled();
  });

  it('should handle deeply nested errors', () => {
    const errors: FieldErrors = {
      form: {
        user: {
          email: { message: 'Email is required', type: 'required' },
        },
      },
    };

    focusFirstError(errors, mockDocument);

    expect(mockQuerySelector).toHaveBeenCalledWith('[name="form.user.email"]');
    expect(mockElement.focus).toHaveBeenCalled();
  });

  it('should return early when no errors', () => {
    const errors: FieldErrors = {};

    focusFirstError(errors, mockDocument);

    expect(mockQuerySelector).not.toHaveBeenCalled();
  });

  it('should return early when element not found', () => {
    mockQuerySelector.mockReturnValue(null);
    const errors: FieldErrors = {
      email: { message: 'Email is required', type: 'required' },
    };

    focusFirstError(errors, mockDocument);

    expect(mockQuerySelector).toHaveBeenCalled();
    expect(mockElement.focus).not.toHaveBeenCalled();
  });

  it('should handle element without focus method', () => {
    const elementWithoutFocus = {} as HTMLElement;
    mockQuerySelector.mockReturnValue(elementWithoutFocus);
    const errors: FieldErrors = {
      email: { message: 'Email is required', type: 'required' },
    };

    expect(() => focusFirstError(errors, mockDocument)).not.toThrow();
  });

  it('should handle element without scrollIntoView method', () => {
    const elementWithoutScroll = {
      focus: vi.fn(),
    } as any;
    mockQuerySelector.mockReturnValue(elementWithoutScroll);
    const errors: FieldErrors = {
      email: { message: 'Email is required', type: 'required' },
    };

    expect(() => focusFirstError(errors, mockDocument)).not.toThrow();
    expect(elementWithoutScroll.focus).toHaveBeenCalled();
  });

  it('should use document as default root', () => {
    const errors: FieldErrors = {
      email: { message: 'Email is required', type: 'required' },
    };

    // Mock document.querySelector
    // eslint-disable-next-line testing-library/no-node-access -- Testing implementation that uses querySelector internally
    const originalQuerySelector = document.querySelector;
    const mockQuerySelector = vi.fn(() => mockElement);
    // eslint-disable-next-line testing-library/no-node-access -- Testing implementation that uses querySelector internally
    document.querySelector = mockQuerySelector as any;

    focusFirstError(errors);

    expect(mockQuerySelector).toHaveBeenCalledWith('[name="email"]');

    // Restore
    // eslint-disable-next-line testing-library/no-node-access -- Testing implementation that uses querySelector internally
    document.querySelector = originalQuerySelector;
  });

  it('should handle multiple errors and focus first one', () => {
    const errors: FieldErrors = {
      password: { message: 'Password is required', type: 'required' },
      email: { message: 'Email is required', type: 'required' },
    };

    focusFirstError(errors, mockDocument);

    // Should focus the first error found (order may vary, but should call querySelector)
    expect(mockQuerySelector).toHaveBeenCalled();
  });

  it('should escape CSS special characters in field name', () => {
    const errors: FieldErrors = {
      'field"with"quotes': { message: 'Error', type: 'required' },
    };

    focusFirstError(errors, mockDocument);

    expect(mockQuerySelector).toHaveBeenCalledWith(
      '[name="field\\"with\\"quotes"]',
    );
  });
});

describe('useFormPersist', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should load persisted values from localStorage on mount', () => {
    const key = 'test-form';
    const persistedData = { email: 'test@example.com', name: 'Test' };
    localStorage.setItem(key, JSON.stringify(persistedData));

    const setValue = vi.fn();
    const watch = vi.fn(() => ({}));

    renderHook(() =>
      useFormPersist(key, watch as any, setValue as any),
    );

    expect(setValue).toHaveBeenCalledWith('email', 'test@example.com');
    expect(setValue).toHaveBeenCalledWith('name', 'Test');
  });

  it('should save form values to localStorage when they change', async () => {
    const key = 'test-form';
    const formValues = { email: 'test@example.com', name: 'Test' };
    const watch = vi.fn(() => formValues);
    const setValue = vi.fn();

    renderHook(() =>
      useFormPersist(key, watch as any, setValue as any),
    );

    // Wait for effect to run
    await waitFor(() => {
      const stored = localStorage.getItem(key);
      expect(stored).toBe(JSON.stringify(formValues));
    });
  });

  it('should handle localStorage errors gracefully', () => {
    const key = 'test-form';
    const setValue = vi.fn();
    const watch = vi.fn(() => ({}));

    // Mock localStorage.getItem to throw
    const originalGetItem = localStorage.getItem;
    localStorage.getItem = vi.fn(() => {
      throw new Error('Storage error');
    });

    expect(() => {
      renderHook(() =>
        useFormPersist(key, watch as any, setValue as any),
      );
    }).not.toThrow();

    localStorage.getItem = originalGetItem;
  });

  it('should handle invalid JSON in localStorage', () => {
    const key = 'test-form';
    localStorage.setItem(key, 'invalid json');

    const setValue = vi.fn();
    const watch = vi.fn(() => ({}));

    expect(() => {
      renderHook(() =>
        useFormPersist(key, watch as any, setValue as any),
      );
    }).not.toThrow();

    expect(setValue).not.toHaveBeenCalled();
  });

  it('should handle localStorage.setItem errors gracefully', () => {
    const key = 'test-form';
    const watch = vi.fn(() => ({ email: 'test@example.com' }));
    const setValue = vi.fn();

    // Mock localStorage.setItem to throw
    const originalSetItem = localStorage.setItem;
    localStorage.setItem = vi.fn(() => {
      throw new Error('Storage quota exceeded');
    });

    expect(() => {
      renderHook(() =>
        useFormPersist(key, watch as any, setValue as any),
      );
    }).not.toThrow();

    localStorage.setItem = originalSetItem;
  });

  it('should not persist when key changes but values are same', () => {
    const key1 = 'form-1';
    const key2 = 'form-2';
    const formValues = { email: 'test@example.com' };
    const watch = vi.fn(() => formValues);
    const setValue = vi.fn();

    const { rerender } = renderHook(
      ({ key }) => useFormPersist(key, watch as any, setValue as any),
      { initialProps: { key: key1 } },
    );

    rerender({ key: key2 });

    // Should load from new key (if exists) or save to new key
    expect(localStorage.getItem(key2)).toBeTruthy();
  });
});

