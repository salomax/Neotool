import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MovimentacoesTab } from '../MovimentacoesTab';
import { InvestmentTransactionType } from '@/lib/graphql/types/__generated__/graphql';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('MovimentacoesTab', () => {
  it('shows withholding-tax label instead of generic redeem label', () => {
    render(
      <MovimentacoesTab
        locale="pt-BR"
        currencyCode="BRL"
        buyLabel="Aplicar"
        sellLabel="Resgatar"
        transactions={[
          {
            id: 'tx-1',
            type: InvestmentTransactionType.WithholdingTax,
            direction: 'OUTFLOW',
            tradeDate: '2026-05-29',
            quantity: 8.12,
            unitPrice: 1.23,
            grossValue: 10.0,
            netValue: 10.0,
          },
        ]}
        onEditTransaction={vi.fn()}
        onDeleteTransaction={vi.fn()}
      />,
    );

    expect(screen.getByText('investments.detailDrawer.transactionTypes.WITHHOLDING_TAX')).toBeInTheDocument();
    expect(screen.queryByText('Resgatar')).not.toBeInTheDocument();
  });
});
