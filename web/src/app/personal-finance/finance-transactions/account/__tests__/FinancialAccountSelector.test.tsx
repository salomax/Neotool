import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { FinancialAccountSelector } from '../FinancialAccountSelector';

const mockUseQuery = vi.fn();

vi.mock('@apollo/client/react', () => ({
  useQuery: (query: unknown, options?: unknown) => mockUseQuery(query, options),
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('@/shared/components/ui/icons/CardNetworkIcon', () => ({
  CardNetworkIcon: () => null,
}));

vi.mock('@/shared/hooks/ui/useLogoBorderColor', () => ({
  useLogoBorderColor: () => null,
}));

describe.sequential('FinancialAccountSelector', () => {
  const theme = createTheme();

  const renderSelector = (props?: Partial<React.ComponentProps<typeof FinancialAccountSelector>>) => {
    const onSelectAccount = vi.fn();

    render(
      <ThemeProvider theme={theme}>
        <FinancialAccountSelector
          onNewAccount={vi.fn()}
          onSelectAccount={onSelectAccount}
          showNewAccountAction={false}
          autoSelectSingleAccount={false}
          {...props}
        />
      </ThemeProvider>,
    );

    return { onSelectAccount };
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('opens the selector with a single eligible account when opt-in is enabled', async () => {
    const user = userEvent.setup();
    mockUseQuery.mockReturnValue({
      data: {
        financialAccounts: [
          { id: 'card-1', name: 'Nubank', type: 'CREDIT', issuer: { name: 'Nubank', logoAsset: null }, cardData: null },
        ],
      },
      loading: false,
    });

    const { onSelectAccount } = renderSelector({ allowSingleAccountSelection: true });

    await user.click(screen.getByText('financeTransactions.selector.allAccounts'));
    expect(await screen.findByTestId('account-selector-popover')).toBeInTheDocument();

    await user.click(screen.getByTestId('financial-account-option'));
    expect(onSelectAccount).toHaveBeenCalledWith('card-1');
  });

  it('keeps single-account selector closed when opt-in is disabled', async () => {
    const user = userEvent.setup();
    mockUseQuery.mockReturnValue({
      data: {
        financialAccounts: [
          { id: 'card-1', name: 'Nubank', type: 'CREDIT', issuer: { name: 'Nubank', logoAsset: null }, cardData: null },
        ],
      },
      loading: false,
    });

    renderSelector();

    await user.click(screen.getByText('financeTransactions.selector.allAccounts'));
    expect(screen.queryByTestId('account-selector-popover')).not.toBeInTheDocument();
  });

  it('preserves default multi-account behavior without the new prop', async () => {
    const user = userEvent.setup();
    mockUseQuery.mockReturnValue({
      data: {
        financialAccounts: [
          { id: 'card-1', name: 'Nubank', type: 'CREDIT', issuer: { name: 'Nubank', logoAsset: null }, cardData: null },
          { id: 'card-2', name: 'Itaú', type: 'CREDIT', issuer: { name: 'Itaú', logoAsset: null }, cardData: null },
        ],
      },
      loading: false,
    });

    renderSelector();

    await user.click(screen.getByText('financeTransactions.selector.allAccounts'));
    expect(await screen.findByTestId('account-selector-popover')).toBeInTheDocument();
  });
});
