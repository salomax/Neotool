import React from 'react';
import { render, screen, cleanup } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import { MonthSummary, MonthSummaryData } from '../MonthSummary';
import { ThemeProvider, createTheme } from '@mui/material/styles';

// Mock dependencies
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('MonthSummary', () => {
  const theme = createTheme();
  let container: HTMLElement | null = null;

  const mockSummary: MonthSummaryData = {
    year: 2024,
    month: 3,
    totalIncome: 5000,
    totalExpenses: 3000,
    closingBalance: 2000,
    openingBalance: 0,
    transfersIn: 0,
    transfersOut: 0,
    projectedClosingBalance: 2000,
    isCurrentMonth: true,
  };

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
  });

  afterEach(() => {
    cleanup();
    if (container && container.parentNode) {
      container.parentNode.removeChild(container);
    }
    container = null;
  });

  const renderWithTheme = (component: React.ReactNode) => {
    return render(
      <ThemeProvider theme={theme}>
        {component}
      </ThemeProvider>,
      { container: container! }
    );
  };

  it('renders loading state', () => {
    const { container } = renderWithTheme(<MonthSummary summary={undefined} loading={true} />);
    // Check for skeletons
    const skeletons = container.querySelectorAll('.MuiSkeleton-root');
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it('renders summary data correctly', () => {
    renderWithTheme(<MonthSummary summary={mockSummary} loading={false} />);
    
    // Check for formatted currency values
    // Using regex to match formatted currency (R$ 5.000,00)
    // The space might be a non-breaking space
    expect(screen.getByText(/5\.000,00/)).toBeInTheDocument(); // Income
    expect(screen.getByText(/3\.000,00/)).toBeInTheDocument(); // Expenses
    expect(screen.getByText(/2\.000,00/)).toBeInTheDocument(); // Balance
    
    // Check for percentage text
    expect(screen.getByText(/63% financeTransactions.monthSummary.total/)).toBeInTheDocument(); // Income % (5000/8000)
    expect(screen.getByText(/38% financeTransactions.monthSummary.total/)).toBeInTheDocument(); // Expenses % (3000/8000)
  });

  it('renders consolidated tooltip when isConsolidated is true', async () => {
    renderWithTheme(<MonthSummary summary={mockSummary} loading={false} isConsolidated={true} />);
    
    // Check for InfoIcon (rendered as SVG)
    // We can look for the tooltip title or the icon itself if it has a role/label
    // But since Tooltip triggers on hover, we might just check if the icon is present.
    // The InfoIcon is rendered inside the Balance item.
    
    // We can check if the icon exists. Material UI icons usually have class MuiSvgIcon-root.
    // A better way might be to look for the tooltip text if we can trigger it, 
    // or just assume the icon presence implies the tooltip logic ran.
    
    // However, the text "financeTransactions.monthSummary.consolidatedTooltip" is passed as title prop to Tooltip.
    // In MUI Tooltip, the title is usually an aria-label on the child or a separate element on hover.
    // Let's try to find the InfoIcon by test-id if we had one, or by SVG content.
    // Or we can query by the tooltip text if it's in the DOM (MUI Tooltip puts it in a Portal on hover).
    
    // Let's verify the tooltip logic by checking if the icon is rendered.
    // The icon is rendered conditionally: {tooltip && (...)}
    
    // We can find the icon by its data-testid if we add one, or by class.
    // Since we don't want to modify source code just for tests if avoidable, let's use querySelector.
    const infoIcons = screen.getAllByTestId('info-icon');
    expect(infoIcons.length).toBeGreaterThan(0);
  });
});
