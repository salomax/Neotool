import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Tabs, { TabItem } from '../Tabs';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock ResizeObserver (needed for both our component and MUI Tabs)
global.ResizeObserver = class ResizeObserver {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
  constructor(callback: ResizeObserverCallback) {
    // Store callback if needed for tests
  }
} as any;

const mockTabs: TabItem[] = [
  {
    id: 'tab1',
    label: 'Tab 1',
    content: <div>Content 1</div>,
    closable: true,
  },
  {
    id: 'tab2',
    label: 'Tab 2',
    content: <div>Content 2</div>,
    closable: true,
  },
  {
    id: 'tab3',
    label: 'Tab 3',
    content: <div>Content 3</div>,
    closable: false,
  },
];

const renderTabs = (props?: Partial<React.ComponentProps<typeof Tabs>>) => {
  return render(
    <AppThemeProvider>
      <Tabs tabs={mockTabs} {...props} />
    </AppThemeProvider>
  );
};

describe('Tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('renders all tabs', () => {
      renderTabs();
      
      expect(screen.getByText('Tab 1')).toBeInTheDocument();
      expect(screen.getByText('Tab 2')).toBeInTheDocument();
      expect(screen.getByText('Tab 3')).toBeInTheDocument();
    });

    it('renders active tab content', () => {
      renderTabs();
      
      expect(screen.getByText('Content 1')).toBeInTheDocument();
    });

    it('renders with custom testid', () => {
      renderTabs({ 'data-testid': 'custom-tabs' });
      
      expect(screen.getByTestId('custom-tabs')).toBeInTheDocument();
    });
  });

  describe('Tab selection', () => {
    it('selects first tab by default', () => {
      renderTabs();
      
      const tab1 = screen.getByText('Tab 1').closest('[role="tab"]');
      expect(tab1).toHaveAttribute('aria-selected', 'true');
      expect(screen.getByText('Content 1')).toBeInTheDocument();
    });

    it('selects tab specified by value prop', () => {
      renderTabs({ value: 'tab2' });
      
      const tab2 = screen.getByText('Tab 2').closest('[role="tab"]');
      expect(tab2).toHaveAttribute('aria-selected', 'true');
      expect(screen.getByText('Content 2')).toBeInTheDocument();
    });

    it('calls onChange when tab is clicked', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      
      renderTabs({ onChange: handleChange });
      
      const tab2 = screen.getByText('Tab 2');
      await user.click(tab2);
      
      await waitFor(() => {
        expect(handleChange).toHaveBeenCalledWith('tab2');
      });
    });

    it('switches content when different tab is selected', async () => {
      const user = userEvent.setup();
      
      renderTabs();
      
      expect(screen.getByText('Content 1')).toBeInTheDocument();
      
      const tab2 = screen.getByText('Tab 2');
      await user.click(tab2);
      
      await waitFor(() => {
        expect(screen.getByText('Content 2')).toBeInTheDocument();
      });
    });
  });

  describe('Tab closing', () => {
    it('shows close button when showCloseButtons is true and tab is closable', () => {
      renderTabs({ showCloseButtons: true });
      
      const tab1Close = screen.getByTestId('tab-close-tab1');
      expect(tab1Close).toBeInTheDocument();
    });

    it('does not show close button when tab is not closable', () => {
      renderTabs({ showCloseButtons: true });
      
      expect(screen.queryByTestId('tab-close-tab3')).not.toBeInTheDocument();
    });

    it('does not show close buttons when showCloseButtons is false', () => {
      renderTabs({ showCloseButtons: false });
      
      expect(screen.queryByTestId('tab-close-tab1')).not.toBeInTheDocument();
    });

    it('removes tab when close button is clicked', async () => {
      const handleTabsChange = vi.fn();
      const user = userEvent.setup();
      
      renderTabs({ showCloseButtons: true, onTabsChange: handleTabsChange });
      
      const closeButton = screen.getByTestId('tab-close-tab1');
      await user.click(closeButton);
      
      await waitFor(() => {
        expect(handleTabsChange).toHaveBeenCalled();
        const updatedTabs = handleTabsChange.mock.calls[0][0];
        expect(updatedTabs).toHaveLength(2);
        expect(updatedTabs.find((t: TabItem) => t.id === 'tab1')).toBeUndefined();
      });
    });

    it('switches to another tab when active tab is closed', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      
      renderTabs({ 
        showCloseButtons: true, 
        value: 'tab2',
        onChange: handleChange 
      });
      
      const closeButton = screen.getByTestId('tab-close-tab2');
      await user.click(closeButton);
      
      await waitFor(() => {
        // Should switch to another tab (likely tab1)
        expect(handleChange).toHaveBeenCalled();
      });
    });

    it('creates new tab when last tab is closed', async () => {
      const handleTabsChange = vi.fn();
      const user = userEvent.setup();
      
      const singleTab: TabItem[] = [{
        id: 'only-tab',
        label: 'Only Tab',
        content: <div>Only Content</div>,
        closable: true,
      }];
      
      render(
        <AppThemeProvider>
          <Tabs 
            tabs={singleTab} 
            showCloseButtons={true}
            onTabsChange={handleTabsChange}
          />
        </AppThemeProvider>
      );
      
      const closeButton = screen.getByTestId('tab-close-only-tab');
      await user.click(closeButton);
      
      await waitFor(() => {
        expect(handleTabsChange).toHaveBeenCalled();
        const updatedTabs = handleTabsChange.mock.calls[0][0];
        expect(updatedTabs.length).toBeGreaterThan(0);
      });
    });
  });

  describe('Tab adding', () => {
    it('shows add button when showAddButton is true', () => {
      renderTabs({ showAddButton: true });
      
      expect(screen.getByTestId('add-tab-button')).toBeInTheDocument();
    });

    it('does not show add button when showAddButton is false', () => {
      renderTabs({ showAddButton: false });
      
      expect(screen.queryByTestId('add-tab-button')).not.toBeInTheDocument();
    });

    it('adds new tab when add button is clicked', async () => {
      const handleTabsChange = vi.fn();
      const user = userEvent.setup();
      
      renderTabs({ 
        showAddButton: true, 
        onTabsChange: handleTabsChange 
      });
      
      const addButton = screen.getByTestId('add-tab-button');
      await user.click(addButton);
      
      await waitFor(() => {
        expect(handleTabsChange).toHaveBeenCalled();
        const updatedTabs = handleTabsChange.mock.calls[0][0];
        expect(updatedTabs.length).toBe(mockTabs.length + 1);
      });
    });

    it('does not show add button when maxTabs is reached', () => {
      renderTabs({ showAddButton: true, maxTabs: 3 });
      
      // We have 3 tabs, so add button should not be visible
      expect(screen.queryByTestId('add-tab-button')).not.toBeInTheDocument();
    });

    it('selects newly added tab', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      
      renderTabs({ 
        showAddButton: true,
        onChange: handleChange
      });
      
      const addButton = screen.getByTestId('add-tab-button');
      await user.click(addButton);
      
      await waitFor(() => {
        expect(handleChange).toHaveBeenCalled();
      });
    });
  });

  describe('Tab badges', () => {
    it('shows badge when tab has badge and showBadges is true', () => {
      const tabsWithBadge: TabItem[] = [{
        id: 'tab1',
        label: 'Tab 1',
        content: <div>Content</div>,
        badge: '5',
      }];
      
      render(
        <AppThemeProvider>
          <Tabs tabs={tabsWithBadge} showBadges={true} />
        </AppThemeProvider>
      );
      
      expect(screen.getByText('5')).toBeInTheDocument();
    });

    it('hides badge when showBadges is false', () => {
      const tabsWithBadge: TabItem[] = [{
        id: 'tab1',
        label: 'Tab 1',
        content: <div>Content</div>,
        badge: '5',
      }];
      
      render(
        <AppThemeProvider>
          <Tabs tabs={tabsWithBadge} showBadges={false} />
        </AppThemeProvider>
      );
      
      expect(screen.queryByText('5')).not.toBeInTheDocument();
    });
  });

  describe('Tab tooltips', () => {
    it('shows tooltip when tab has tooltip prop', async () => {
      const tabsWithTooltip: TabItem[] = [{
        id: 'tab1',
        label: 'Tab 1',
        content: <div>Content</div>,
        tooltip: 'Tooltip text',
      }];
      
      const user = userEvent.setup();
      
      render(
        <AppThemeProvider>
          <Tabs tabs={tabsWithTooltip} />
        </AppThemeProvider>
      );
      
      const tab = screen.getByText('Tab 1');
      await user.hover(tab);
      
      await waitFor(() => {
        expect(screen.getByText('Tooltip text')).toBeInTheDocument();
      });
    });
  });

  describe('Disabled tabs', () => {
    it('disables tab when disabled prop is true', () => {
      const disabledTabs: TabItem[] = [{
        id: 'tab1',
        label: 'Tab 1',
        content: <div>Content</div>,
        disabled: true,
      }];
      
      render(
        <AppThemeProvider>
          <Tabs tabs={disabledTabs} />
        </AppThemeProvider>
      );
      
      const tab = screen.getByText('Tab 1').closest('[role="tab"]');
      // MUI Tabs uses disabled class or aria-disabled, check for either
      expect(tab).toHaveClass('Mui-disabled');
    });
  });

  describe('External value synchronization', () => {
    it('updates active tab when value prop changes', () => {
      const { rerender } = renderTabs({ value: 'tab1' });
      
      expect(screen.getByText('Content 1')).toBeInTheDocument();
      
      rerender(
        <AppThemeProvider>
          <Tabs tabs={mockTabs} value="tab2" />
        </AppThemeProvider>
      );
      
      expect(screen.getByText('Content 2')).toBeInTheDocument();
    });
  });

  describe('Tabs synchronization', () => {
    it('updates tabs when initialTabs prop changes', () => {
      const { rerender } = renderTabs();
      
      expect(screen.getByText('Tab 1')).toBeInTheDocument();
      
      const newTabs: TabItem[] = [{
        id: 'new-tab',
        label: 'New Tab',
        content: <div>New Content</div>,
      }];
      
      rerender(
        <AppThemeProvider>
          <Tabs tabs={newTabs} />
        </AppThemeProvider>
      );
      
      expect(screen.getByText('New Tab')).toBeInTheDocument();
      expect(screen.queryByText('Tab 1')).not.toBeInTheDocument();
    });
  });
});

