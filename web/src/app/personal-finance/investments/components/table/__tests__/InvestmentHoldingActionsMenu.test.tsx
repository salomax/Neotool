import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InvestmentHoldingActionsMenu } from '../InvestmentHoldingActionsMenu';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe.sequential('InvestmentHoldingActionsMenu', () => {
  let anchorEl: HTMLButtonElement;

  beforeEach(() => {
    anchorEl = document.createElement('button');
    document.body.appendChild(anchorEl);
  });

  afterEach(() => {
    anchorEl.remove();
    vi.clearAllMocks();
  });

  const baseProps = {
    deleting: false,
    onClose: vi.fn(),
    onViewDetails: vi.fn(),
    onEdit: vi.fn().mockResolvedValue(undefined),
    onRemove: vi.fn(),
    onBuy: vi.fn(),
    onSell: vi.fn(),
    buyLabel: 'buy',
    sellLabel: 'sell',
    showTransactionActions: true,
    onRecordProvento: vi.fn(),
    onRecordSplit: vi.fn(),
    onRecordBonus: vi.fn(),
    onRecordSubscription: vi.fn(),
    onRecordWithholdingTax: vi.fn(),
    onRecordAmortization: vi.fn(),
    onRecordStockLending: vi.fn(),
    onTransferCustody: vi.fn(),
  };

  it('shows transfer custody action when quantity is greater than zero', async () => {
    const user = userEvent.setup();
    const onTransferCustody = vi.fn();
    const onClose = vi.fn();
    render(
      <InvestmentHoldingActionsMenu
        {...baseProps}
        anchorEl={anchorEl}
        onTransferCustody={onTransferCustody}
        onClose={onClose}
        holding={
          {
            id: 'holding-1',
            quantity: 1,
            asset: { assetType: 'STOCK' },
          } as any
        }
      />,
    );

    const transferAction = await screen.findByText('investments.table.rowActions.transferCustody');
    expect(transferAction).toBeInTheDocument();
    await user.click(transferAction);
    expect(onTransferCustody).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('hides transfer custody action when quantity is zero', () => {
    render(
      <InvestmentHoldingActionsMenu
        {...baseProps}
        anchorEl={anchorEl}
        holding={
          {
            id: 'holding-1',
            quantity: 0,
            asset: { assetType: 'STOCK' },
          } as any
        }
      />,
    );

    expect(screen.queryByText('investments.table.rowActions.transferCustody')).not.toBeInTheDocument();
  });
});
