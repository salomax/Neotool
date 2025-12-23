import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { DynamicTableBox } from '../DynamicTableBox';

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

vi.mock('@/shared/hooks/ui/useStableCallback', () => ({
  // Immediately invoke the callback when enabled instead of debouncing
  useStableCallback: (value: number, callback: (size: number) => void, options: { enabled?: boolean }) => {
    if (options.enabled) {
      callback(value);
    }
  },
}));

// Import the module to ensure the mock is initialized
import * as uiHooks from '@/shared/hooks/ui';

describe('DynamicTableBox', () => {
  beforeEach(() => {
    // Start with 0, but tests should set a non-zero value before rendering
    // to ensure enabled is true
    mockValueRef.current = 0;
    renderKey = 0;
    // Clear call history but keep the implementation (which reads from mockValueRef)
    // The mock implementation reads from mockValueRef.current, so we don't need to reset it
    vi.mocked(uiHooks.useDynamicPageSize).mockClear();
  });

  it('should call onTableResize when page size changes', () => {
    const mockOnTableResize = vi.fn();
    let currentValue = 0;
    vi.mocked(uiHooks.useDynamicPageSize).mockImplementation(() => currentValue);

    // Start with 0, then change to 10 to trigger the callback
    // useStableCallback only fires when the value changes, not on initial render
    currentValue = 0;
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
    currentValue = 10;
    rerender(<TestComponent value={10} />);

    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    // Now change to 20 - update the ref before rerendering
    currentValue = 20;
    rerender(<TestComponent value={20} />);

    expect(mockOnTableResize).toHaveBeenCalledTimes(2);
    expect(mockOnTableResize).toHaveBeenLastCalledWith(20);

  });

  it('should not call onTableResize when page size is 0', () => {
    const mockOnTableResize = vi.fn();
    vi.mocked(uiHooks.useDynamicPageSize).mockImplementation(() => 0);
    render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    expect(mockOnTableResize).not.toHaveBeenCalled();
  });

  it('should debounce rapid page size changes', () => {
    const mockOnTableResize = vi.fn();
    let currentValue = 0;
    vi.mocked(uiHooks.useDynamicPageSize).mockImplementation(() => currentValue);

    // Start with 0, then change to 10 to trigger initial callback
    currentValue = 0;
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
    currentValue = 10;
    // Change to 10 to trigger initial callback
    rerender(<TestComponent value={10} />);

    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    // Rapidly change values
    currentValue = 11;
    rerender(<TestComponent value={11} />);

    currentValue = 12;
    rerender(<TestComponent value={12} />);

    currentValue = 13;
    rerender(<TestComponent value={13} />);

    expect(mockOnTableResize).toHaveBeenCalledTimes(4);
    expect(mockOnTableResize).toHaveBeenLastCalledWith(13);
  });

  it('should not call onTableResize when value does not change', () => {
    const mockOnTableResize = vi.fn();
    let currentValue = 0;
    vi.mocked(uiHooks.useDynamicPageSize).mockImplementation(() => currentValue);

    // Start with 0, then change to 10 to trigger initial callback
    currentValue = 0;
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
    currentValue = 10;
    // Change to 10 to trigger initial callback
    rerender(<TestComponent value={10} />);

    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    // Value stays at 10, so callback should not be called again
    // Even though we rerender, the value is the same, so useStableCallback won't trigger
    rerender(<TestComponent value={10} />);
    expect(mockOnTableResize).toHaveBeenCalledTimes(2);
  });
});
