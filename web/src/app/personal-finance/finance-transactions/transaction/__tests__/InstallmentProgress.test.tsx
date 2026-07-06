import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { InstallmentProgress } from '../InstallmentProgress';

const mockT = vi.fn((key: string) => {
  const translations: Record<string, string> = {
    'financeTransactions.transactionTable.installments.of': 'de',
    'financeTransactions.transactionTable.installments.progressAriaLabel': 'Parcela {{current}} de {{total}}',
  };
  return translations[key] ?? key;
});

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({ t: mockT, i18n: { language: 'pt-BR' } }),
}));

describe('InstallmentProgress', () => {
  beforeEach(() => {
    mockT.mockClear();
  });

  it('renders localized installment progress for pt-BR', () => {
    render(<InstallmentProgress installmentNumber={10} totalInstallments={12} />);

    expect(screen.getByText('10 de 12')).toBeInTheDocument();
    expect(mockT).toHaveBeenCalledWith('financeTransactions.transactionTable.installments.of');
  });
});
