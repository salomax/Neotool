'use client';

import * as React from 'react';
import type { NewHoldingFlowPhase } from './newHoldingFlow.types';
import type { NewHoldingGateSelection } from './newHoldingFlow.types';

export interface UseNewHoldingFlowStateOptions {
  open: boolean;
  /** Changes to this key reset the flow to step 1 (e.g. intent kind + asset class). */
  resetKey: string;
}

export function useNewHoldingFlowState({ open, resetKey }: UseNewHoldingFlowStateOptions) {
  const [phase, setPhase] = React.useState<NewHoldingFlowPhase>({ kind: 'select' });

  React.useEffect(() => {
    if (!open) {
      setPhase({ kind: 'select' });
    }
  }, [open]);

  React.useEffect(() => {
    setPhase({ kind: 'select' });
  }, [resetKey]);

  const confirmSelection = React.useCallback((selection: NewHoldingGateSelection) => {
    setPhase({ kind: 'form', selection });
  }, []);

  const goToSelect = React.useCallback(() => {
    setPhase({ kind: 'select' });
  }, []);

  return { phase, confirmSelection, goToSelect };
}
