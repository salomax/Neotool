import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useDebouncedFieldValidator } from '../useDebouncedFieldValidator';
import type { UseFormSetError, UseFormClearErrors } from 'react-hook-form';

// Run sequentially to keep fake timers isolated per file
describe.sequential('useDebouncedFieldValidator', () => {
  let setError: ReturnType<typeof vi.fn>;
  let clearErrors: ReturnType<typeof vi.fn>;
  let validate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    setError = vi.fn();
    clearErrors = vi.fn();
    validate = vi.fn();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  it('should clear errors immediately when value changes', async () => {
    validate.mockResolvedValue(true);

    const { rerender } = renderHook(
      ({ value }) =>
        useDebouncedFieldValidator(
          'testField' as any,
          value,
          validate,
          setError as any,
          clearErrors as any,
        ),
      { initialProps: { value: 'initial' } },
    );

    await waitFor(() => {
      expect(clearErrors).toHaveBeenCalledWith('testField');
    });

    rerender({ value: 'changed' });

    expect(clearErrors).toHaveBeenCalledTimes(2);
  });

  it('should validate after delay when value is valid', async () => {
    validate.mockResolvedValue(true);

    renderHook(() =>
      useDebouncedFieldValidator(
        'testField' as any,
        'test value',
        validate,
        setError as any,
        clearErrors as any,
        20,
      ),
    );

    await waitFor(() => {
      expect(validate).toHaveBeenCalledWith('test value');
      expect(setError).not.toHaveBeenCalled();
    });
  });

  it('should set error when validation fails', async () => {
    validate.mockResolvedValue('Error message');

    renderHook(() =>
      useDebouncedFieldValidator(
        'testField' as any,
        'invalid value',
        validate,
        setError as any,
        clearErrors as any,
        20,
      ),
    );

    await waitFor(() => {
      expect(validate).toHaveBeenCalledWith('invalid value');
      expect(setError).toHaveBeenCalledWith('testField', {
        type: 'validate',
        message: 'Error message',
      });
    });
  });

  it('should debounce validation calls', async () => {
    validate.mockResolvedValue(true);

    const { rerender } = renderHook(
      ({ value }) =>
        useDebouncedFieldValidator(
        'testField' as any,
        value,
        validate,
        setError as any,
        clearErrors as any,
        20,
      ),
      { initialProps: { value: 'value1' } },
    );

    // Change value multiple times quickly
    rerender({ value: 'value2' });
    await sleep(10);
    rerender({ value: 'value3' });
    await sleep(10);
    rerender({ value: 'value4' });

    // Should not have validated yet
    expect(validate).not.toHaveBeenCalled();

    await waitFor(() => {
      // Should only validate once with the last value
      expect(validate).toHaveBeenCalledTimes(1);
      expect(validate).toHaveBeenCalledWith('value4');
    });
  });

  it('should use custom delay', async () => {
    validate.mockResolvedValue(true);

    renderHook(() =>
      useDebouncedFieldValidator(
        'testField' as any,
        'test value',
        validate,
        setError as any,
        clearErrors as any,
        50,
      ),
    );

    await waitFor(() => {
      expect(validate).toHaveBeenCalled();
    });
  });

  it('should cleanup timer on unmount', () => {
    validate.mockResolvedValue(true);

    const { unmount } = renderHook(() =>
      useDebouncedFieldValidator(
        'testField' as any,
        'test value',
        validate,
        setError as any,
        clearErrors as any,
        20,
      ),
    );

    unmount();

    expect(validate).not.toHaveBeenCalled();
  });

  it('should cleanup previous timer when value changes', async () => {
    validate.mockResolvedValue(true);

    const { rerender } = renderHook(
      ({ value }) =>
        useDebouncedFieldValidator(
        'testField' as any,
        value,
        validate,
        setError as any,
        clearErrors as any,
        30,
      ),
      { initialProps: { value: 'value1' } },
    );

    // Change value - should cancel first timer
    rerender({ value: 'value2' });

    await waitFor(() => {
      expect(validate).toHaveBeenCalledTimes(1);
      expect(validate).toHaveBeenCalledWith('value2');
    });
  });

  it('should handle async validation errors', async () => {
    const error = new Error('Validation failed');
    validate.mockRejectedValue(error);

    renderHook(() =>
      useDebouncedFieldValidator(
        'testField' as any,
        'test value',
        validate,
        setError as any,
        clearErrors as any,
        20,
      ),
    );

    await waitFor(() => {
      expect(validate).toHaveBeenCalled();
      // Should not set error on rejection (component should handle)
      expect(setError).not.toHaveBeenCalled();
    });
  });

  it('should handle field name changes', async () => {
    validate.mockResolvedValue(true);

    const { rerender } = renderHook(
      ({ fieldName }) =>
        useDebouncedFieldValidator(
          fieldName as any,
          'test value',
          validate,
          setError as any,
          clearErrors as any,
          20,
        ),
      { initialProps: { fieldName: 'field1' } },
    );

    rerender({ fieldName: 'field2' });

    await waitFor(() => {
      expect(clearErrors).toHaveBeenCalledWith('field2');
    });
  });
  it('should handle field name changes', () => {
    validate.mockResolvedValue(true);

    const { rerender } = renderHook(
      ({ name }) =>
        useDebouncedFieldValidator(
          name as any,
          'test value',
          validate,
          setError as any,
          clearErrors as any,
          500,
        ),
      { initialProps: { name: 'field1' } },
    );

    expect(clearErrors).toHaveBeenCalledWith('field1');

    rerender({ name: 'field2' });

    expect(clearErrors).toHaveBeenCalledWith('field2');
  });
});
