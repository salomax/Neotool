import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, act } from '@testing-library/react';
import { DynamicTableBox, TABLE_STABILITY_DELAY } from '../DynamicTableBox';

// Mock useDynamicPageSize
// Use a ref to track value - React will see changes when component re-renders
const mockValueRef = { current: 0 };

// Track render count to simulate value changes
let renderKey = 0;

// Define the mock function inside the factory to avoid hoisting issues
vi.mock('@/shared/hooks/ui', () => {
  return {
    useDynamicPageSize: vi.fn(() => {
      // Always read the current value from the ref
      // This ensures that when the component re-renders, it gets the updated value
      return mockValueRef.current;
    }),
  };
});

// Import the module to ensure the mock is initialized
import * as uiHooks from '@/shared/hooks/ui';

describe('DynamicTableBox', () => {
  const mockOnTableResize = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    // Start with 0, but tests should set a non-zero value before rendering
    // to ensure enabled is true
    mockValueRef.current = 0;
    renderKey = 0;
    // Clear call history but keep the implementation (which reads from mockValueRef)
    // The mock implementation reads from mockValueRef.current, so we don't need to reset it
    vi.mocked(uiHooks.useDynamicPageSize).mockClear();
    mockOnTableResize.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('should call onTableResize after stability delay when page size changes', () => {
    // Start with 0, then change to 10 to trigger the callback
    // useStableCallback only fires when the value changes, not on initial render
    mockValueRef.current = 0;
    const TestComponent = ({ value }: { value: number }) => {
      return (
        <DynamicTableBox onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
    };
    
    // Render with 0 first (callback won't fire because enabled is false when value is 0)
    const { rerender } = render(<TestComponent value={0} />);
    
    // Update the ref before rerendering so the mock returns the new value
    mockValueRef.current = 10;
    // Now change to 10 - this should trigger the callback
    act(() => {
      rerender(<TestComponent value={10} />);
    });

    // Wait for the value change to stabilize
    act(() => {
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    // Now change to 20 - update the ref before rerendering
    mockValueRef.current = 20;
    act(() => {
      rerender(<TestComponent value={20} />);
    });

    act(() => {
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(2);
    expect(mockOnTableResize).toHaveBeenLastCalledWith(20);

  });

  it('should not call onTableResize when page size is 0', () => {
    mockValueRef.current = 0;
    render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    act(() => {
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).not.toHaveBeenCalled();
  });

  it('should debounce rapid page size changes', () => {
    // Start with 0, then change to 10 to trigger initial callback
    mockValueRef.current = 0;
    const TestComponent = ({ value }: { value: number }) => {
      return (
        <DynamicTableBox onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
    };
    
    // Render with 0 first
    const { rerender } = render(<TestComponent value={0} />);
    
    // Update the ref before rerendering
    mockValueRef.current = 10;
    // Change to 10 to trigger initial callback
    act(() => {
      rerender(<TestComponent value={10} />);
    });

    // Wait for initial value change to stabilize
    act(() => {
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    // Rapidly change values
    mockValueRef.current = 11;
    act(() => {
      rerender(<TestComponent value={11} />);
    });

    mockValueRef.current = 12;
    act(() => {
      rerender(<TestComponent value={12} />);
    });

    mockValueRef.current = 13;
    act(() => {
      rerender(<TestComponent value={13} />);
    });

    act(() => {
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY - 5);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    act(() => {
      vi.advanceTimersByTime(5);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(2);
    expect(mockOnTableResize).toHaveBeenLastCalledWith(13);
  });

  it('should not call onTableResize when value does not change', () => {
    // Start with 0, then change to 10 to trigger initial callback
    mockValueRef.current = 0;
    const TestComponent = ({ value }: { value: number }) => {
      return (
        <DynamicTableBox onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
    };
    
    // Render with 0 first
    const { rerender } = render(<TestComponent value={0} />);
    
    // Update the ref before rerendering
    mockValueRef.current = 10;
    // Change to 10 to trigger initial callback
    act(() => {
      rerender(<TestComponent value={10} />);
    });

    // Wait for initial value change to stabilize
    act(() => {
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    // Value stays at 10, so callback should not be called again
    // Even though we rerender, the value is the same, so useStableCallback won't trigger
    act(() => {
      rerender(<TestComponent value={10} />);
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
  });
});
