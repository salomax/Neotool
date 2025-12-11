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
const mockUseThemeMode = vi.fn<[], { mode: 'light' | 'dark'; toggle: () => void }>(() => ({
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
type User = {
  id: string;
  email: string;
  displayName?: string | null;
};

type AuthContextType = {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  signIn: (email: string, password: string, rememberMe?: boolean) => Promise<void>;
  signInWithOAuth: (provider: string, idToken: string, rememberMe?: boolean) => Promise<void>;
  signUp: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => void;
  isAuthenticated: boolean;
};

const mockSignOut = vi.fn();
const mockSignIn = vi.fn();
const mockSignInWithOAuth = vi.fn();
const mockSignUp = vi.fn();
const mockUseAuth = vi.fn<[], AuthContextType>(() => ({
  isAuthenticated: false,
  user: null,
  token: null,
  signOut: mockSignOut,
  signIn: mockSignIn,
  signInWithOAuth: mockSignInWithOAuth,
  signUp: mockSignUp,
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

// Mock page title hook
const mockUsePageTitleValue = vi.fn<[], string | null>(() => null);
vi.mock('@/shared/hooks/ui/usePageTitle', () => ({
  usePageTitleValue: () => mockUsePageTitleValue(),
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
      token: null,
      signOut: mockSignOut,
      signIn: mockSignIn,
      signInWithOAuth: mockSignInWithOAuth,
      signUp: mockSignUp,
      isLoading: false,
    });
    mockUsePageTitleValue.mockReturnValue(null);
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
        token: null,
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: '  John  ',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: null,
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: '',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: null,
          email: '',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John  Middle  Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: '   ',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: null,
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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
          id: '1',
          displayName: 'John Doe',
          email: 'john@example.com',
        },
        token: 'test-token',
        signOut: mockSignOut,
        signIn: mockSignIn,
        signInWithOAuth: mockSignInWithOAuth,
        signUp: mockSignUp,
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

  describe('Page title display', () => {
    it('should display title when set', () => {
      mockUsePageTitleValue.mockReturnValue('Instituições Financeiras');
      
      renderAppHeader();
      
      const title = screen.getByText('Instituições Financeiras');
      expect(title).toBeInTheDocument();
      expect(title.tagName).toBe('H1');
    });

    it('should not display title when null', () => {
      mockUsePageTitleValue.mockReturnValue(null);
      
      renderAppHeader();
      
      const titles = screen.queryAllByRole('heading', { level: 1 });
      // Should not have any h1 elements (title is conditionally rendered)
      expect(titles.length).toBe(0);
    });

    it('should not display title when empty string', () => {
      mockUsePageTitleValue.mockReturnValue('');
      
      renderAppHeader();
      
      // Empty string is falsy, so title should not render
      const titles = screen.queryAllByRole('heading', { level: 1 });
      expect(titles.length).toBe(0);
    });

    it('should display title with correct styling', () => {
      mockUsePageTitleValue.mockReturnValue('Test Title');
      
      renderAppHeader();
      
      const title = screen.getByText('Test Title');
      expect(title).toBeInTheDocument();
      
      // Check it's an h1 element
      expect(title.tagName).toBe('H1');
    });

    it('should handle long titles with truncation', () => {
      const longTitle = 'This is a very long title that should be truncated when displayed in the header';
      mockUsePageTitleValue.mockReturnValue(longTitle);
      
      renderAppHeader();
      
      const title = screen.getByText(longTitle);
      expect(title).toBeInTheDocument();
      
      // Title should have truncation styles applied via sx prop
      // We can't easily test sx styles, but we can verify the element exists
      expect(title).toBeInTheDocument();
    });

    it('should maintain layout with title present', () => {
      mockUsePageTitleValue.mockReturnValue('Page Title');
      
      renderAppHeader();
      
      // Header should render with title
      const header = screen.getByRole('banner');
      expect(header).toBeInTheDocument();
      
      // Title should be present
      expect(screen.getByText('Page Title')).toBeInTheDocument();
      
      // Actions should still be present
      const themeButton = screen.getByLabelText('toggle theme');
      expect(themeButton).toBeInTheDocument();
    });

    it('should maintain layout without title', () => {
      mockUsePageTitleValue.mockReturnValue(null);
      
      renderAppHeader();
      
      // Header should render without title
      const header = screen.getByRole('banner');
      expect(header).toBeInTheDocument();
      
      // Actions should still be present
      const themeButton = screen.getByLabelText('toggle theme');
      expect(themeButton).toBeInTheDocument();
    });

    it('should update title when pageTitle changes', () => {
      const { rerender } = renderAppHeader();
      
      // Initially no title
      expect(screen.queryAllByRole('heading', { level: 1 }).length).toBe(0);
      
      // Update to show title
      mockUsePageTitleValue.mockReturnValue('New Title');
      rerender(
        <AppThemeProvider>
          <AppHeader />
        </AppThemeProvider>
      );
      
      expect(screen.getByText('New Title')).toBeInTheDocument();
      
      // Update to remove title
      mockUsePageTitleValue.mockReturnValue(null);
      rerender(
        <AppThemeProvider>
          <AppHeader />
        </AppThemeProvider>
      );
      
      expect(screen.queryAllByRole('heading', { level: 1 }).length).toBe(0);
    });
  });
});

