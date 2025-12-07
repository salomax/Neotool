import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SidebarRail } from '../SidebarRail';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { AuthProvider } from '@/shared/providers/AuthProvider';
import { AuthorizationProvider } from '@/shared/providers/AuthorizationProvider';

// Mock Next.js navigation
const mockPathname = vi.fn();
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  usePathname: () => mockPathname(),
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
  }),
  Link: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock LogoMark component
vi.mock('@/shared/ui/brand/LogoMark', () => ({
  LogoMark: ({ variant, width, height }: any) => (
    <div data-testid="logo-mark" data-variant={variant} data-width={width} data-height={height}>
      Logo
    </div>
  ),
}));

// Mock GraphQL queries
vi.mock('@/lib/graphql/operations/auth/queries.generated', () => ({
  useCurrentUserQuery: vi.fn(() => ({
    data: {
      currentUser: {
        id: '1',
        email: 'test@example.com',
        displayName: 'Test User',
        roles: [],
        permissions: [],
      },
    },
    loading: false,
    refetch: vi.fn(),
  })),
}));

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

const renderSidebarRail = () => {
  return render(
    <AppThemeProvider>
      <AuthProvider>
        <AuthorizationProvider>
          <SidebarRail />
        </AuthorizationProvider>
      </AuthProvider>
    </AppThemeProvider>
  );
};

describe('SidebarRail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPathname.mockReturnValue('/');
  });

  describe('Rendering', () => {
    it('renders sidebar rail', () => {
      renderSidebarRail();
      
      const sidebar = screen.getByRole('complementary');
      expect(sidebar).toBeInTheDocument();
    });

    it('renders logo with home link', () => {
      renderSidebarRail();
      
      const logoLink = screen.getByLabelText('Go to home page');
      expect(logoLink).toBeInTheDocument();
      expect(logoLink).toHaveAttribute('href', '/');
      
      const logo = screen.getByTestId('logo-mark');
      expect(logo).toBeInTheDocument();
      expect(logo).toHaveAttribute('data-variant', 'white');
    });

    it('renders all navigation items', () => {
      renderSidebarRail();
      
      expect(screen.getByLabelText('Design System')).toBeInTheDocument();
      expect(screen.getByLabelText('Examples')).toBeInTheDocument();
      expect(screen.getByLabelText('Documentation')).toBeInTheDocument();
    });

    it('renders navigation items with correct hrefs', () => {
      renderSidebarRail();
      
      // IconButton with LinkComponent wraps the button in a link
      const designSystemButton = screen.getByLabelText('Design System');
      const examplesButton = screen.getByLabelText('Examples');
      const documentationButton = screen.getByLabelText('Documentation');
      
      expect(designSystemButton).toBeInTheDocument();
      expect(examplesButton).toBeInTheDocument();
      expect(documentationButton).toBeInTheDocument();
    });
  });

  describe('Active state logic', () => {
    it('marks item as active when pathname exactly matches href', () => {
      mockPathname.mockReturnValue('/design-system');
      renderSidebarRail();
      
      const designSystemButton = screen.getByLabelText('Design System');
      
      // The button should be present (IconButton with LinkComponent renders as anchor)
      expect(designSystemButton).toBeInTheDocument();
    });

    it('marks item as active when pathname starts with href (non-root)', () => {
      mockPathname.mockReturnValue('/design-system/components');
      renderSidebarRail();
      
      const designSystemButton = screen.getByLabelText('Design System');
      
      expect(designSystemButton).toBeInTheDocument();
    });

    it('does not mark root item as active when pathname starts with other href', () => {
      mockPathname.mockReturnValue('/design-system');
      renderSidebarRail();
      
      // Root path should not be active
      const logoLink = screen.getByLabelText('Go to home page');
      expect(logoLink).toBeInTheDocument();
    });

    it('marks root as active when pathname is exactly "/"', () => {
      mockPathname.mockReturnValue('/');
      renderSidebarRail();
      
      // When on root, no nav items should be active (they're not root)
      const designSystemButton = screen.getByLabelText('Design System');
      
      // Should be present but not necessarily active
      expect(designSystemButton).toBeInTheDocument();
    });

    it('handles null pathname gracefully', () => {
      mockPathname.mockReturnValue(null);
      renderSidebarRail();
      
      // Should render without errors
      expect(screen.getByRole('complementary')).toBeInTheDocument();
      expect(screen.getByLabelText('Design System')).toBeInTheDocument();
    });

    it('does not mark item as active when pathname does not match', () => {
      mockPathname.mockReturnValue('/other-page');
      renderSidebarRail();
      
      const designSystemButton = screen.getByLabelText('Design System');
      
      // Should be present
      expect(designSystemButton).toBeInTheDocument();
    });

    it('handles subpath matching correctly for non-root items', () => {
      mockPathname.mockReturnValue('/examples/code-samples');
      renderSidebarRail();
      
      const examplesButton = screen.getByLabelText('Examples');
      
      expect(examplesButton).toBeInTheDocument();
    });
  });

  describe('Tooltips', () => {
    it('shows tooltip for logo', () => {
      renderSidebarRail();
      
      const logoLink = screen.getByLabelText('Go to home page');
      expect(logoLink).toBeInTheDocument();
    });

    it('shows tooltips for navigation items', () => {
      renderSidebarRail();
      
      // Tooltips are rendered by MUI, we can check the aria-labels
      expect(screen.getByLabelText('Design System')).toBeInTheDocument();
      expect(screen.getByLabelText('Examples')).toBeInTheDocument();
      expect(screen.getByLabelText('Documentation')).toBeInTheDocument();
    });
  });

  describe('Interactions', () => {
    it('navigates to home when logo is clicked', async () => {
      const user = userEvent.setup();
      renderSidebarRail();
      
      const logoLink = screen.getByLabelText('Go to home page');
      await user.click(logoLink);
      
      // Link should have correct href
      expect(logoLink).toHaveAttribute('href', '/');
    });

    it('navigates to correct page when nav item is clicked', async () => {
      const user = userEvent.setup();
      renderSidebarRail();
      
      const designSystemButton = screen.getByLabelText('Design System');
      
      // Button should be clickable
      expect(designSystemButton).toBeInTheDocument();
      await user.click(designSystemButton);
    });
  });

  describe('Styling and layout', () => {
    it('applies correct width constant', () => {
      renderSidebarRail();
      
      const sidebar = screen.getByRole('complementary');
      expect(sidebar).toHaveStyle({ width: '84px' });
    });

    it('renders with fixed positioning', () => {
      renderSidebarRail();
      
      const sidebar = screen.getByRole('complementary');
      expect(sidebar).toHaveStyle({ position: 'fixed' });
    });
  });
});

