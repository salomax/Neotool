import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useSyncDefaultSettlementDate } from '../useSyncDefaultSettlementDate';

describe('useSyncDefaultSettlementDate', () => {
  it('does not set settlement when tradeDate is invalid', () => {
    const setValue = vi.fn();

    renderHook(() =>
      useSyncDefaultSettlementDate({
        enabled: true,
        assetKind: 'fund',
        tradeDate: new Date(Number.NaN),
        setValue,
      }),
    );

    expect(setValue).not.toHaveBeenCalled();
  });

  it('sets default settlement when tradeDate is valid', () => {
    const setValue = vi.fn();
    const tradeDate = new Date(2026, 2, 15);

    renderHook(() =>
      useSyncDefaultSettlementDate({
        enabled: true,
        assetKind: 'fund',
        tradeDate,
        setValue,
      }),
    );

    expect(setValue).toHaveBeenCalledTimes(1);
    const [, nextSettlement] = setValue.mock.calls[0];
    expect(nextSettlement).toBeInstanceOf(Date);
    expect((nextSettlement as Date).getFullYear()).toBe(2026);
    expect((nextSettlement as Date).getMonth()).toBe(2);
    expect((nextSettlement as Date).getDate()).toBe(15);
  });

  it('skips sync when disabled even if tradeDate is valid', () => {
    const setValue = vi.fn();

    renderHook(() =>
      useSyncDefaultSettlementDate({
        enabled: false,
        assetKind: 'fund',
        tradeDate: new Date(2026, 2, 15),
        setValue,
      }),
    );

    expect(setValue).not.toHaveBeenCalled();
  });

  it('re-syncs when tradeDate changes to a new valid date', () => {
    const setValue = vi.fn();
    let tradeDate: Date | undefined = new Date(2026, 2, 15);

    const { rerender } = renderHook(() =>
      useSyncDefaultSettlementDate({
        enabled: true,
        assetKind: 'fund',
        tradeDate,
        setValue,
      }),
    );

    expect(setValue).toHaveBeenCalledTimes(1);

    tradeDate = new Date(Number.NaN);
    act(() => {
      rerender();
    });
    expect(setValue).toHaveBeenCalledTimes(1);

    tradeDate = new Date(2026, 2, 20);
    act(() => {
      rerender();
    });
    expect(setValue).toHaveBeenCalledTimes(2);
  });
});
