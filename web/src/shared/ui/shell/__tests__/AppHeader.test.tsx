import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppHeader } from '../AppHeader';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock Next.js router
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock theme mode hook
const mockToggle = vi.fn();
const mockUseThemeMode = vi.fn(() => ({
  mode: 'light' as const,
  toggle: mockToggle,
}));

vi.mock('@/styles/themes/AppThemeProvider', async () => {
  const actual = await vi.importActual('@/styles/themes/AppThemeProvider');
  return {
    ...actual,
    useThemeMode: () => mockUseThemeMode(),
  };
});

// Mock auth provider
const mockSignOut = vi.fn();
const mockUseAuth = vi.fn(() => ({
  isAuthenticated: false,
  user: null,
  signOut: mockSignOut,
  isLoading: false,
}));

vi.mock('@/shared/providers', () => ({
  useAuth: () => mockUseAuth(),
}));

// Mock ChatDrawer component
vi.mock('@/shared/components/ui/feedback/Chat', () => ({
  ChatDrawer: ({ open, onClose }: { open: boolean; onClose: () => void }) => (
    <div data-testid="chat-drawer" data-open={open}>
      <button onClick={onClose}>Close Chat</button>
    </div>
  ),
}));

// Mock Button component
vi.mock('@/shared/components/ui/primitives/Button', () => ({
  Button: ({ children, onClick, ...props }: any) => (
    <button onClick={onClick} {...props}>
      {children}
    </button>
  ),
}));

const renderAppHeader = () => {
  return render(
    <AppThemeProvider>
      <AppHeader />
    </AppThemeProvider>
  );
};

describe('AppHeader', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseThemeMode.mockReturnValue({
      mode: 'light' as const,
      toggle: mockToggle,
    });
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      user: null,
      signOut: mockSignOut,
      isLoading: false,
    });
  });

  describe('Rendering', () => {
    it('renders header', () => {
      renderAppHeader();
      
      const header = screen.getByRole('banner');
      expect(header).toBeInTheDocument();
    });

    it('renders theme toggle button', () => {
      renderAppHeader();
      
      const themeButton = screen.getByLabelText('toggle theme');
      expect(themeButton).toBeInTheDocument();
    });

    it('renders AI assistant button', () => {
      renderAppHeader();
      
      const chatButton = screen.getByLabelText('Open AI assistant');
      expect(chatButton).toBeInTheDocument();
    });

    it('renders sign in button when not authenticated', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const signInButton = screen.getByTestId('header-signin-button');
      expect(signInButton).toBeInTheDocument();
      expect(signInButton).toHaveTextContent('Sign In');
    });

    it('renders user avatar when authenticated', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      expect(accountButton).toBeInTheDocument();
    });
  });

  describe('Theme toggle', () => {
    it('shows light mode icon when in dark mode', () => {
      mockUseThemeMode.mockReturnValue({
        mode: 'dark' as const,
        toggle: mockToggle,
      });

      renderAppHeader();
      
      const themeButton = screen.getByLabelText('toggle theme');
      expect(themeButton).toBeInTheDocument();
    });

    it('shows dark mode icon when in light mode', () => {
      mockUseThemeMode.mockReturnValue({
        mode: 'light' as const,
        toggle: mockToggle,
      });

      renderAppHeader();
      
      const themeButton = screen.getByLabelText('toggle theme');
      expect(themeButton).toBeInTheDocument();
    });

    it('calls toggle when theme button is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();
      
      const themeButton = screen.getByLabelText('toggle theme');
      await user.click(themeButton);
      
      expect(mockToggle).toHaveBeenCalledTimes(1);
    });
  });

  describe('Chat drawer', () => {
    it('opens chat drawer when AI assistant button is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();
      
      const chatButton = screen.getByLabelText('Open AI assistant');
      await user.click(chatButton);
      
      const chatDrawer = screen.getByTestId('chat-drawer');
      expect(chatDrawer).toHaveAttribute('data-open', 'true');
    });

    it('closes chat drawer when close is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();
      
      // Open drawer
      const chatButton = screen.getByLabelText('Open AI assistant');
      await user.click(chatButton);
      
      // Close drawer
      const closeButton = screen.getByText('Close Chat');
      await user.click(closeButton);
      
      const chatDrawer = screen.getByTestId('chat-drawer');
      expect(chatDrawer).toHaveAttribute('data-open', 'false');
    });
  });

  describe('Authentication', () => {
    it('navigates to signin when sign in button is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();
      
      const signInButton = screen.getByTestId('header-signin-button');
      await user.click(signInButton);
      
      expect(mockPush).toHaveBeenCalledWith('/signin');
    });

    it('opens profile menu when avatar is clicked', async () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      const user = userEvent.setup();
      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      await user.click(accountButton);
      
      // Menu should be open
      const menu = screen.getByRole('menu');
      expect(menu).toBeInTheDocument();
    });

    it('displays user information in profile menu', async () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      const user = userEvent.setup();
      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      await user.click(accountButton);
      
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('john@example.com')).toBeInTheDocument();
    });

    it('closes profile menu when menu item is clicked', async () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      const user = userEvent.setup();
      renderAppHeader();
      
      // Open menu
      const accountButton = screen.getByLabelText('Account menu');
      await user.click(accountButton);
      
      // Click sign out (which also closes menu)
      const signOutItem = screen.getByText('Sign Out');
      await user.click(signOutItem);
      
      // Menu should be closed (not visible)
      await waitFor(() => {
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
      });
    });

    it('calls signOut when sign out is clicked', async () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      const user = userEvent.setup();
      renderAppHeader();
      
      // Open menu
      const accountButton = screen.getByLabelText('Account menu');
      await user.click(accountButton);
      
      // Click sign out
      const signOutItem = screen.getByText('Sign Out');
      await user.click(signOutItem);
      
      expect(mockSignOut).toHaveBeenCalledTimes(1);
    });
  });

  describe('getInitials function', () => {
    it('returns "?" when user is null', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: null,
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      expect(accountButton).toBeInTheDocument();
    });

    it('returns first and last initial when displayName has 2+ words', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      // Avatar should show "JD"
      expect(accountButton).toBeInTheDocument();
    });

    it('returns first initial when displayName has 1 word', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      expect(accountButton).toBeInTheDocument();
    });

    it('returns first initial when displayName has empty words', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: '  John  ',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      expect(accountButton).toBeInTheDocument();
    });

    it('returns email first character when displayName is null', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: null,
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      expect(accountButton).toBeInTheDocument();
    });

    it('returns email first character when displayName is empty', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: '',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      expect(accountButton).toBeInTheDocument();
    });

    it('returns "?" when email is empty or undefined', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: null,
          email: '',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      expect(accountButton).toBeInTheDocument();
    });

    it('handles displayName with multiple spaces correctly', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John  Middle  Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      // Should get first and last: "JD"
      expect(accountButton).toBeInTheDocument();
    });

    it('handles displayName with only whitespace', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: '   ',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      // Should fall back to email
      expect(accountButton).toBeInTheDocument();
    });

    it('uses "User" as fallback display name in menu when displayName is missing', async () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: null,
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      const user = userEvent.setup();
      renderAppHeader();
      
      const accountButton = screen.getByLabelText('Account menu');
      await user.click(accountButton);
      
      // Menu should show "User" as display name
      await waitFor(() => {
        expect(screen.getByText('User')).toBeInTheDocument();
      });
    });
  });

  describe('Menu state management', () => {
    it('manages menu open/close state correctly', async () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        signOut: mockSignOut,
        isLoading: false,
      });

      const user = userEvent.setup();
      renderAppHeader();
      
      // Menu should be closed initially
      expect(screen.queryByRole('menu')).not.toBeInTheDocument();
      
      // Open menu
      const accountButton = screen.getByLabelText('Account menu');
      await user.click(accountButton);
      
      // Menu should be open
      expect(screen.getByRole('menu')).toBeInTheDocument();
      
      // Close menu by clicking outside or on menu item
      const signOutItem = screen.getByText('Sign Out');
      await user.click(signOutItem);
      
      // Menu should be closed
      await waitFor(() => {
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
      });
    });
  });
});

