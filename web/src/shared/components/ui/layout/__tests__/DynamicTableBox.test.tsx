import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, act } from '@testing-library/react';
import { DynamicTableBox, TABLE_STABILITY_DELAY } from '../DynamicTableBox';

// Mock useDynamicPageSize
// Use a ref to track value so React sees it as changing
const mockValueRef = { current: 0 };
const mockUseDynamicPageSize = vi.fn(() => {
  // Call the mock function to track calls
  mockUseDynamicPageSize();
  return mockValueRef.current;
});

// Track render count to simulate value changes
let renderKey = 0;
vi.mock('@/shared/hooks/ui', () => ({
  useDynamicPageSize: () => {
    // Return value based on current ref value
    // The key prop will force React to see this as a new render
    return mockValueRef.current;
  },
}));

describe('DynamicTableBox', () => {
  const mockOnTableResize = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    mockValueRef.current = 0; // Reset value for each test
    renderKey = 0; // Reset render key for each test
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('should call onTableResize after stability delay when page size changes', () => {
    const { rerender } = render(
      <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    // Trigger a re-render to change value from 0 to 10
    mockValueRef.current = 10;
    act(() => {
      rerender(
        <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
    });

    // Wait for the change from 0 to 10 to stabilize
    act(() => {
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    mockValueRef.current = 20;
    act(() => {
      rerender(
        <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
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
    const { rerender } = render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    // Trigger initial change from 0 to 10
    mockValueRef.current = 10;
    act(() => {
      rerender(
        <DynamicTableBox onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    mockValueRef.current = 11;
    act(() => {
      rerender(
        <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
    });

    mockValueRef.current = 12;
    act(() => {
      rerender(
        <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
    });

    mockValueRef.current = 13;
    act(() => {
      rerender(
        <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
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
    const { rerender } = render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    // Trigger initial change from 0 to 10
    mockValueRef.current = 10;
    act(() => {
      rerender(
        <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    // Value stays at 10, so callback should not be called again
    mockValueRef.current = 10;
    act(() => {
      rerender(
        <DynamicTableBox key={renderKey++} onTableResize={mockOnTableResize}>
          <div>Test</div>
        </DynamicTableBox>
      );
      vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    });
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
  });
});
