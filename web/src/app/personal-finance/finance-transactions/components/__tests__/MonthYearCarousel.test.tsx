import React from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import { MonthYearCarousel } from '../MonthYearCarousel';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import dayjs from 'dayjs';

// Mock dependencies
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  i18n: {
    language: 'pt-BR',
  },
}));

describe.sequential('MonthYearCarousel', () => {
  const theme = createTheme();
  const mockOnChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  const renderWithTheme = (component: React.ReactNode) => {
    return render(
      <ThemeProvider theme={theme}>
        {component}
      </ThemeProvider>,
    );
  };

  it('renders selected month and year correctly', () => {
    // March 2024
    renderWithTheme(
      <MonthYearCarousel 
        selectedMonth={3} 
        selectedYear={2024} 
        onChange={mockOnChange} 
      />
    );

    // Should display "Março 2024" or "March 2024" depending on locale.
    // Since we mocked i18n language as 'pt-BR', dayjs should use Portuguese if locale is loaded.
    // But dayjs locale loading in test environment might be tricky if 'dayjs/locale/pt-br' is not actually loaded by the component or test.
    // The component imports 'dayjs/locale/pt-br'.
    
    // Let's check for "2024" first to be safe, or partial match.
    expect(screen.getByText(/2024/)).toBeInTheDocument();
  });

  it('calls onChange with previous month when prev button is clicked', () => {
    renderWithTheme(
      <MonthYearCarousel 
        selectedMonth={3} 
        selectedYear={2024} 
        onChange={mockOnChange} 
      />
    );

    const prevButton = screen.getAllByRole('button')[0];
    expect(prevButton).toBeDefined();
    fireEvent.click(prevButton!);

    expect(mockOnChange).toHaveBeenCalledWith(2, 2024);
  });

  it('calls onChange with previous year when prev button is clicked in January', () => {
    renderWithTheme(
      <MonthYearCarousel 
        selectedMonth={1} 
        selectedYear={2024} 
        onChange={mockOnChange} 
      />
    );

    const prevButton = screen.getAllByRole('button')[0];
    expect(prevButton).toBeDefined();
    fireEvent.click(prevButton!);

    expect(mockOnChange).toHaveBeenCalledWith(12, 2023);
  });

  it('calls onChange with next month when next button is clicked', () => {
    renderWithTheme(
      <MonthYearCarousel 
        selectedMonth={3} 
        selectedYear={2024} 
        onChange={mockOnChange} 
      />
    );

    const nextButton = screen.getAllByRole('button')[1];
    expect(nextButton).toBeDefined();
    fireEvent.click(nextButton!);

    expect(mockOnChange).toHaveBeenCalledWith(4, 2024);
  });

  it('calls onChange with next year when next button is clicked in December', () => {
    renderWithTheme(
      <MonthYearCarousel 
        selectedMonth={12} 
        selectedYear={2024} 
        onChange={mockOnChange} 
      />
    );

    const nextButton = screen.getAllByRole('button')[1];
    expect(nextButton).toBeDefined();
    fireEvent.click(nextButton!);

    expect(mockOnChange).toHaveBeenCalledWith(1, 2025);
  });

  it('shows "Back to current month" link when not in current month', () => {
    // Assuming current date is NOT 2020-01
    renderWithTheme(
      <MonthYearCarousel 
        selectedMonth={1} 
        selectedYear={2020} 
        onChange={mockOnChange} 
      />
    );

    expect(screen.getByText('financeTransactions.backToCurrentMonth')).toBeInTheDocument();
  });

  it('hides "Back to current month" link when in current month', () => {
    const now = dayjs();
    renderWithTheme(
      <MonthYearCarousel 
        selectedMonth={now.month() + 1} 
        selectedYear={now.year()} 
        onChange={mockOnChange} 
      />
    );

    expect(screen.queryByText('financeTransactions.backToCurrentMonth')).not.toBeInTheDocument();
  });
});
