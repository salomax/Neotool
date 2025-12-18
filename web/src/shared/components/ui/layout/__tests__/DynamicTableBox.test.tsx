import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from '@testing-library/react';
import { DynamicTableBox, TABLE_STABILITY_DELAY } from '../DynamicTableBox';

// Mock useDynamicPageSize
const mockUseDynamicPageSize = vi.fn(() => 10);

vi.mock('@/shared/hooks/ui', () => ({
  useDynamicPageSize: () => mockUseDynamicPageSize(),
}));

describe('DynamicTableBox', () => {
  const mockOnTableResize = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('should call onTableResize after stability delay when page size changes', () => {
    mockUseDynamicPageSize.mockReturnValue(10);
    const { rerender } = render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    expect(mockOnTableResize).not.toHaveBeenCalled();
    vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    expect(mockOnTableResize).toHaveBeenCalledWith(10);

    mockUseDynamicPageSize.mockReturnValue(20);
    rerender(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    expect(mockOnTableResize).toHaveBeenCalledTimes(2);
    expect(mockOnTableResize).toHaveBeenLastCalledWith(20);
  });

  it('should not call onTableResize when page size is 0', () => {
    mockUseDynamicPageSize.mockReturnValue(0);
    render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    expect(mockOnTableResize).not.toHaveBeenCalled();
  });

  it('should debounce rapid page size changes', () => {
    mockUseDynamicPageSize.mockReturnValue(10);
    const { rerender } = render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);

    mockUseDynamicPageSize.mockReturnValue(11);
    rerender(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    mockUseDynamicPageSize.mockReturnValue(12);
    rerender(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    mockUseDynamicPageSize.mockReturnValue(13);
    rerender(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    vi.advanceTimersByTime(TABLE_STABILITY_DELAY - 5);
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(5);
    expect(mockOnTableResize).toHaveBeenCalledTimes(2);
    expect(mockOnTableResize).toHaveBeenLastCalledWith(13);
  });

  it('should not call onTableResize when value does not change', () => {
    mockUseDynamicPageSize.mockReturnValue(10);
    const { rerender } = render(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);

    mockUseDynamicPageSize.mockReturnValue(10);
    rerender(
      <DynamicTableBox onTableResize={mockOnTableResize}>
        <div>Test</div>
      </DynamicTableBox>
    );

    vi.advanceTimersByTime(TABLE_STABILITY_DELAY);
    expect(mockOnTableResize).toHaveBeenCalledTimes(1);
  });
});
