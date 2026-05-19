import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useNewHoldingFlowState } from '../useNewHoldingFlowState';
import type { CryptoGateResult } from '../newHoldingFlow.types';

const sampleCrypto: CryptoGateResult = {
  kind: 'crypto',
  name: 'Bitcoin',
  ticker: 'BTC',
  network: 'Mainnet',
};

describe('useNewHoldingFlowState', () => {
  it('starts in select phase', () => {
    const { result } = renderHook(() => useNewHoldingFlowState({ open: true, resetKey: 'new-buy-crypto' }));
    expect(result.current.phase).toEqual({ kind: 'select' });
  });

  it('confirmSelection moves to form with selection', () => {
    const { result } = renderHook(() => useNewHoldingFlowState({ open: true, resetKey: 'k' }));
    act(() => {
      result.current.confirmSelection(sampleCrypto);
    });
    expect(result.current.phase).toEqual({ kind: 'form', selection: sampleCrypto });
  });

  it('goToSelect returns to select from form', () => {
    const { result } = renderHook(() => useNewHoldingFlowState({ open: true, resetKey: 'k' }));
    act(() => {
      result.current.confirmSelection(sampleCrypto);
    });
    act(() => {
      result.current.goToSelect();
    });
    expect(result.current.phase).toEqual({ kind: 'select' });
  });

  it('resets to select when open becomes false', () => {
    const { result, rerender } = renderHook(
      ({ open }: { open: boolean }) => useNewHoldingFlowState({ open, resetKey: 'k' }),
      { initialProps: { open: true } },
    );
    act(() => {
      result.current.confirmSelection(sampleCrypto);
    });
    rerender({ open: false });
    expect(result.current.phase).toEqual({ kind: 'select' });
  });

  it('resets to select when resetKey changes', () => {
    const { result, rerender } = renderHook(
      ({ resetKey }: { resetKey: string }) => useNewHoldingFlowState({ open: true, resetKey }),
      { initialProps: { resetKey: 'new-buy-funds' } },
    );
    act(() => {
      result.current.confirmSelection(sampleCrypto);
    });
    rerender({ resetKey: 'new-sell-funds' });
    expect(result.current.phase).toEqual({ kind: 'select' });
  });
});
