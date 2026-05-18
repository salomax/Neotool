import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { FundGateResult } from '../newHoldingFlow.types';

const mockFundSelection: FundGateResult = {
  kind: 'fund_cvm',
  listingMode: 'CVM',
  fund: {
    id: 'fund-1',
    name: 'Example Fund',
    cnpj: '00.000.000/0001-91',
    assetType: 'MUTUAL_FUND',
    isActive: true,
  },
};

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('../../../InvestmentRecordDrawer', () => ({
  InvestmentRecordDrawer: ({
    children,
    title,
  }: {
    title: string;
    children: (ctx: { resolvedFinancialAccountId: string | null }) => React.ReactNode;
  }) => (
    <div>
      <div data-testid="shell-drawer-title">{title}</div>
      {children({ resolvedFinancialAccountId: 'fa-1' })}
    </div>
  ),
}));

vi.mock('../newHoldingFlowRegistry', () => ({
  newHoldingSelectTitleKey: {
    funds: 'investments.newHoldingFlow.selectFundTitle',
    stocks: 'stocks.title',
    reits: 'reits.title',
    crypto: 'crypto.title',
  },
  newHoldingFormTitleKey: {
    funds: 'investments.recordFund.title',
    stocks: 'stocks.form',
    reits: 'reits.form',
    crypto: 'crypto.form',
  },
  newHoldingSelectGate: {
    funds: ({ onContinue }: { onContinue: (s: FundGateResult) => void }) => (
      <button type="button" data-testid="stub-gate-continue" onClick={() => onContinue(mockFundSelection)}>
        continue
      </button>
    ),
    stocks: () => null,
    reits: () => null,
    crypto: () => null,
  },
}));

vi.mock('../../../RecordFundHoldingDrawer', () => ({
  RecordFundHoldingDrawer: () => <div data-testid="stub-record-fund" />,
}));

import { NewHoldingFlowShell } from '../NewHoldingFlowShell';

describe('NewHoldingFlowShell', () => {
  it('shows select title then form surface after continue; Alterar returns to select', async () => {
    const user = userEvent.setup();
    render(
      <NewHoldingFlowShell
        open
        onClose={vi.fn()}
        intent={{ kind: 'new-buy', assetClass: 'funds' }}
        financialAccountId="fa-1"
        financialAccounts={[{ id: 'fa-1', label: 'A' } as never]}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('shell-drawer-title')).toHaveTextContent('investments.newHoldingFlow.selectFundTitle');
    });
    expect(screen.queryByTestId('stub-record-fund')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('stub-gate-continue'));

    await waitFor(() => {
      expect(screen.getByTestId('shell-drawer-title')).toHaveTextContent('investments.recordFund.title');
    });
    expect(screen.getByTestId('stub-record-fund')).toBeInTheDocument();

    await user.click(screen.getByText('investments.newHoldingFlow.changeAsset'));

    await waitFor(() => {
      expect(screen.getByTestId('shell-drawer-title')).toHaveTextContent('investments.newHoldingFlow.selectFundTitle');
    });
    expect(screen.queryByTestId('stub-record-fund')).not.toBeInTheDocument();
  });
});
