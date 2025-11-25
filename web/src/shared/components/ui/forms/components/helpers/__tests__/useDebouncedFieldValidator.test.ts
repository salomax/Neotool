import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useDebouncedFieldValidator } from '../useDebouncedFieldValidator';
import type { UseFormSetError, UseFormClearErrors } from 'react-hook-form';

describe('useDebouncedFieldValidator', () => {
  let setError: ReturnType<typeof vi.fn>;
  let clearErrors: ReturnType<typeof vi.fn>;
  let validate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    setError = vi.fn();
    clearErrors = vi.fn();
    validate = vi.fn();
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('should clear errors immediately when value changes', () => {
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

    expect(clearErrors).toHaveBeenCalledWith('testField');

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
        500,
      ),
    );

    expect(validate).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(500);

    expect(validate).toHaveBeenCalledWith('test value');
    expect(setError).not.toHaveBeenCalled();
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
        500,
      ),
    );

    await vi.advanceTimersByTimeAsync(500);

    expect(validate).toHaveBeenCalledWith('invalid value');
    expect(setError).toHaveBeenCalledWith('testField', {
      type: 'validate',
      message: 'Error message',
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
          500,
        ),
      { initialProps: { value: 'value1' } },
    );

    // Change value multiple times quickly
    rerender({ value: 'value2' });
    await vi.advanceTimersByTimeAsync(200);
    rerender({ value: 'value3' });
    await vi.advanceTimersByTimeAsync(200);
    rerender({ value: 'value4' });

    // Should not have validated yet
    expect(validate).not.toHaveBeenCalled();

    // Advance past delay
    await vi.advanceTimersByTimeAsync(500);

    // Should only validate once with the last value
    expect(validate).toHaveBeenCalledTimes(1);
    expect(validate).toHaveBeenCalledWith('value4');
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
        1000,
      ),
    );

    await vi.advanceTimersByTimeAsync(500);
    expect(validate).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(500);

    expect(validate).toHaveBeenCalled();
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
        500,
      ),
    );

    unmount();

    // Advance timers - should not call validate after unmount
    vi.advanceTimersByTime(500);

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
          500,
        ),
      { initialProps: { value: 'value1' } },
    );

    // Start first timer
    await vi.advanceTimersByTimeAsync(200);

    // Change value - should cancel first timer
    rerender({ value: 'value2' });

    // Advance past original delay
    await vi.advanceTimersByTimeAsync(400);

    // Should not have validated with first value
    expect(validate).not.toHaveBeenCalled();

    // Advance to trigger second timer
    await vi.advanceTimersByTimeAsync(300);

    expect(validate).toHaveBeenCalledTimes(1);
    expect(validate).toHaveBeenCalledWith('value2');
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
        500,
      ),
    );

    await vi.advanceTimersByTimeAsync(500);

    expect(validate).toHaveBeenCalled();
    // Should not set error on rejection (component should handle)
    expect(setError).not.toHaveBeenCalled();
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

