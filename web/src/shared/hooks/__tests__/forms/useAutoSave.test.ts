import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, cleanup } from '@testing-library/react';
import { useAutoSave } from '@/shared/hooks/forms';

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('useAutoSave', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('should initialize with isSaving false', () => {
    const onSave = vi.fn();
    const { result } = renderHook(() =>
      useAutoSave({ name: 'test' }, onSave)
    );

    expect(result.current.isSaving).toBe(false);
  });

  it('should call onSave after debounce delay', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    renderHook(
      ({ values }) => useAutoSave(values, onSave, 500),
      {
        initialProps: { values: { name: 'test' } },
      }
    );

    expect(onSave).not.toHaveBeenCalled();

    // Fast-forward time and flush async operations
    await vi.advanceTimersByTimeAsync(500);
    await vi.runAllTimersAsync();

    expect(onSave).toHaveBeenCalledWith({ name: 'test' });
  });

  it('should set isSaving to true during save', async () => {
    const onSave = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          setTimeout(resolve, 100);
        })
    );

    const { result } = renderHook(() =>
      useAutoSave({ name: 'test' }, onSave, 500)
    );

    // Advance timers to trigger debounce and start the save
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    
    // isSaving should be true while the promise is pending
    expect(result.current.isSaving).toBe(true);

    // Advance timers to let the promise resolve
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
      await vi.runAllTimersAsync();
    });

    // isSaving should be false after the promise resolves
    expect(result.current.isSaving).toBe(false);
  });

  it('should use custom debounce delay', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    renderHook(() => useAutoSave({ name: 'test' }, onSave, 1000));

    await vi.advanceTimersByTimeAsync(500);
    expect(onSave).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(500);
    await vi.runAllTimersAsync();
    
    expect(onSave).toHaveBeenCalled();
  });

  it('should cancel previous save when values change', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const { rerender } = renderHook(
      ({ values }) => useAutoSave(values, onSave, 500),
      {
        initialProps: { values: { name: 'test1' } },
      }
    );

    await vi.advanceTimersByTimeAsync(300);

    rerender({ values: { name: 'test2' } });

    await vi.advanceTimersByTimeAsync(500);
    await vi.runAllTimersAsync();

    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith({ name: 'test2' });
  });

  it('should use latest values even if they change during debounce', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const { rerender } = renderHook(
      ({ values }) => useAutoSave(values, onSave, 500),
      {
        initialProps: { values: { name: 'test1' } },
      }
    );

    await vi.advanceTimersByTimeAsync(300);

    rerender({ values: { name: 'test2' } });
    rerender({ values: { name: 'test3' } });

    await vi.advanceTimersByTimeAsync(500);
    await vi.runAllTimersAsync();

    expect(onSave).toHaveBeenCalledWith({ name: 'test3' });
  });

  it('should handle async onSave function', async () => {
    const onSave = vi.fn(
      async (values) => {
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
    );

    const { result } = renderHook(() =>
      useAutoSave({ name: 'test' }, onSave, 500)
    );

    // Advance timers to trigger debounce and start the save
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    
    // isSaving should be true while the promise is pending
    expect(result.current.isSaving).toBe(true);

    // Advance timers to let the promise resolve
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
      await vi.runAllTimersAsync();
    });

    // isSaving should be false after the promise resolves
    expect(result.current.isSaving).toBe(false);
  });

  it('should handle sync onSave function', async () => {
    const onSave = vi.fn(() => {});

    renderHook(() => useAutoSave({ name: 'test' }, onSave, 500));

    await vi.advanceTimersByTimeAsync(500);
    await vi.runAllTimersAsync();

    expect(onSave).toHaveBeenCalled();
  });

  it('should handle complex object values', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const complexValues = {
      user: { name: 'John', age: 30 },
      items: [1, 2, 3],
      metadata: { active: true },
    };

    renderHook(() => useAutoSave(complexValues, onSave, 500));

    await vi.advanceTimersByTimeAsync(500);
    await vi.runAllTimersAsync();

    expect(onSave).toHaveBeenCalledWith(complexValues);
  });

  it('should cleanup timeout on unmount', () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const { unmount } = renderHook(() =>
      useAutoSave({ name: 'test' }, onSave, 500)
    );

    vi.advanceTimersByTime(300);
    unmount();
    vi.advanceTimersByTime(500);

    expect(onSave).not.toHaveBeenCalled();
  });
});

