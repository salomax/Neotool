import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { TransactionAccountCell } from '../TransactionAccountCell';

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      if (key === 'financeTransactions.multipleCards') return 'Multiple cards';
      return key;
    },
  }),
}));

describe('TransactionAccountCell', () => {
  const theme = createTheme();
  afterEach(() => {
    cleanup();
  });

  it('shows "Multiple cards" for multi-card account without transaction card identifier', () => {
    render(
      <ThemeProvider theme={theme}>
        <TransactionAccountCell
          account={{
            id: 'acc-1',
            type: 'CREDIT',
            subtype: 'CREDIT_CARD',
            name: 'Cartão XP',
            cardData: { isMultiCard: true, maskedNumber: null, cardNetwork: 'VISA' },
          } as any}
          transaction={{
            id: 'tx-1',
            cardIdentifier: null,
          } as any}
        />
      </ThemeProvider>,
    );

    expect(screen.getByText('Multiple cards')).toBeInTheDocument();
  });

  it('prioritizes transaction card identifier over multi-card fallback label', () => {
    const { container } = render(
      <ThemeProvider theme={theme}>
        <TransactionAccountCell
          account={{
            id: 'acc-1',
            type: 'CREDIT',
            subtype: 'CREDIT_CARD',
            name: 'Cartão XP',
            cardData: { isMultiCard: true, maskedNumber: null, cardNetwork: 'VISA' },
          } as any}
          transaction={{
            id: 'tx-1',
            cardIdentifier: 'final 7788',
          } as any}
        />
      </ThemeProvider>,
    );

    const cell = within(container);
    expect(cell.getByText('•••• 7788')).toBeInTheDocument();
    expect(cell.queryByText('Multiple cards')).not.toBeInTheDocument();
  });
});
