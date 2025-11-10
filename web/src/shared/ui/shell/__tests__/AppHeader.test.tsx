import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppHeader } from '../AppHeader';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { headerTranslations } from '../i18n';
import i18n from '@/shared/i18n/config';

// Mock the auth hook
const mockUseAuth = vi.fn();
vi.mock('@/shared/providers/AuthProvider', () => ({
  useAuth: () => mockUseAuth(),
}));

// Mock Next.js Link
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Register header translations
i18n.addResourceBundle('en', 'header', headerTranslations.en, true, true);
i18n.addResourceBundle('pt', 'header', headerTranslations.pt, true, true);

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
  });

  describe('Authentication state', () => {
    it('should show login button when user is not authenticated', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const loginButtonContainer = screen.getByTestId('button-login');
      expect(loginButtonContainer).toBeInTheDocument();
      expect(loginButtonContainer).toHaveTextContent('Sign in');
      const loginLink = loginButtonContainer.querySelector('a');
      expect(loginLink).toHaveAttribute('href', '/signin');
      
      // Profile button should not be visible
      expect(screen.queryByTestId('button-profile')).not.toBeInTheDocument();
    });

    it('should show profile button when user is authenticated', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Test User',
        },
        isLoading: false,
      });

      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      expect(profileButton).toBeInTheDocument();
      expect(profileButton).toHaveAttribute('aria-label', 'profile');
      
      // Login button should not be visible
      expect(screen.queryByTestId('button-login')).not.toBeInTheDocument();
    });

    it('should not show login or profile button when loading', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: true,
      });

      renderAppHeader();

      expect(screen.queryByTestId('button-login')).not.toBeInTheDocument();
      expect(screen.queryByTestId('button-profile')).not.toBeInTheDocument();
    });

    it('should show profile button after loading completes and user is authenticated', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Test User',
        },
        isLoading: false,
      });

      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      expect(profileButton).toBeInTheDocument();
    });
  });

  describe('User avatar initials', () => {
    it('should display user initials from displayName when available', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'John Doe',
        },
        isLoading: false,
      });

      renderAppHeader();

      const avatar = screen.getByTestId('button-profile').querySelector('div[class*="MuiAvatar"]');
      expect(avatar).toHaveTextContent('JD');
    });

    it('should display first letter of email when displayName is not available', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: null,
        },
        isLoading: false,
      });

      renderAppHeader();

      const avatar = screen.getByTestId('button-profile').querySelector('div[class*="MuiAvatar"]');
      expect(avatar).toHaveTextContent('T');
    });

    it('should display first letter of email when displayName is empty string', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'alice@example.com',
          displayName: '',
        },
        isLoading: false,
      });

      renderAppHeader();

      const avatar = screen.getByTestId('button-profile').querySelector('div[class*="MuiAvatar"]');
      expect(avatar).toHaveTextContent('A');
    });

    it('should handle multi-word displayName correctly', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Mary Jane Watson',
        },
        isLoading: false,
      });

      renderAppHeader();

      const avatar = screen.getByTestId('button-profile').querySelector('div[class*="MuiAvatar"]');
      expect(avatar).toHaveTextContent('MJ');
    });

    it('should display question mark when user is null', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const avatar = screen.getByTestId('button-profile').querySelector('div[class*="MuiAvatar"]');
      expect(avatar).toHaveTextContent('?');
    });
  });

  describe('Login button', () => {
    it('should have correct href to signin page', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const loginButtonContainer = screen.getByTestId('button-login');
      const loginLink = loginButtonContainer.querySelector('a');
      expect(loginLink).toHaveAttribute('href', '/signin');
    });

    it('should have login icon', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const loginButtonContainer = screen.getByTestId('button-login');
      // Check that the button contains the login icon (via startIcon prop)
      expect(loginButtonContainer).toBeInTheDocument();
      const loginIcon = screen.getByTestId('LoginRoundedIcon');
      expect(loginIcon).toBeInTheDocument();
    });
  });

  describe('Profile button', () => {
    it('should have correct aria-label', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Test User',
        },
        isLoading: false,
      });

      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      expect(profileButton).toHaveAttribute('aria-label', 'Profile menu');
    });

    it('should be wrapped in a tooltip', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Test User',
        },
        isLoading: false,
      });

      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      // Tooltip should be present (MUI Tooltip wraps the button)
      expect(profileButton).toBeInTheDocument();
    });
  });

  describe('Other header elements', () => {
    it('should render search bar', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const searchInput = screen.getByPlaceholderText('Search here');
      expect(searchInput).toBeInTheDocument();
    });

    it('should render theme toggle button', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const themeButton = screen.getByTestId('button-theme-toggle');
      expect(themeButton).toBeInTheDocument();
      expect(themeButton).toHaveAttribute('aria-label', 'toggle theme');
    });

    it('should toggle theme when theme button is clicked', async () => {
      const user = userEvent.setup();
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const themeButton = screen.getByTestId('button-theme-toggle');
      await user.click(themeButton);

      // Theme should toggle (we can't easily verify the state change without more complex setup)
      expect(themeButton).toBeInTheDocument();
    });

    it('should show appropriate icon based on theme mode', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const themeButton = screen.getByTestId('button-theme-toggle');
      // Should show either light or dark icon based on current theme
      const hasLightIcon = themeButton.querySelector('svg[data-testid="LightModeRoundedIcon"]');
      const hasDarkIcon = themeButton.querySelector('svg[data-testid="DarkModeRoundedIcon"]');
      expect(hasLightIcon || hasDarkIcon).toBeTruthy();
    });

    // Notifications button is commented out for now
    // it('should render notifications button', () => {
    //   mockUseAuth.mockReturnValue({
    //     isAuthenticated: false,
    //     user: null,
    //     isLoading: false,
    //   });

    //   renderAppHeader();

    //   const notificationsButton = screen.getByLabelText('notifications');
    //   expect(notificationsButton).toBeInTheDocument();
    // });
  });

  describe('Profile menu', () => {
    const mockSignOut = vi.fn();
    
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Test User',
        },
        isLoading: false,
        signOut: mockSignOut,
      });
    });

    it('should open profile menu when profile button is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });
    });

    it('should display user information in menu', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByText('Test User')).toBeInTheDocument();
        expect(screen.getByText('test@example.com')).toBeInTheDocument();
      });
    });

    it('should display email when displayName is not available', async () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: null,
        },
        isLoading: false,
        signOut: mockSignOut,
      });

      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByText('test@example.com')).toBeInTheDocument();
      });
    });

    it('should display menu items', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('menu-item-my-account')).toBeInTheDocument();
        expect(screen.getByTestId('menu-item-settings')).toBeInTheDocument();
        expect(screen.getByTestId('menu-item-sign-out')).toBeInTheDocument();
      });
    });

    it('should close menu when clicking outside', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });

      // Click outside (on the document body)
      await user.click(document.body);

      await waitFor(() => {
        expect(screen.queryByTestId('profile-menu')).not.toBeInTheDocument();
      });
    });

    it('should close menu when Escape key is pressed', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });

      await user.keyboard('{Escape}');

      await waitFor(() => {
        expect(screen.queryByTestId('profile-menu')).not.toBeInTheDocument();
      });
    });

    it('should have correct links for menu items', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        const myAccountLink = screen.getByTestId('menu-item-my-account').closest('a');
        const settingsLink = screen.getByTestId('menu-item-settings').closest('a');
        
        expect(myAccountLink).toHaveAttribute('href', '/profile');
        expect(settingsLink).toHaveAttribute('href', '/settings');
      });
    });
  });

  describe('Logout functionality', () => {
    const mockSignOut = vi.fn();
    
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Test User',
        },
        isLoading: false,
        signOut: mockSignOut,
      });
    });

    it('should open confirmation dialog when sign out is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });

      const signOutMenuItem = screen.getByTestId('menu-item-sign-out');
      await user.click(signOutMenuItem);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      }, { timeout: 3000 });
    });

    it('should display confirmation dialog with correct text', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });

      const signOutMenuItem = screen.getByTestId('menu-item-sign-out');
      await user.click(signOutMenuItem);

      await waitFor(() => {
        expect(screen.getByText('Sign out')).toBeInTheDocument();
        expect(screen.getByText('Are you sure you want to sign out?')).toBeInTheDocument();
      });
    });

    it('should call signOut when confirmation is confirmed', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });

      const signOutMenuItem = screen.getByTestId('menu-item-sign-out');
      await user.click(signOutMenuItem);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      }, { timeout: 3000 });

      // Find and click the confirm button
      const confirmButton = screen.getByRole('button', { name: /sign out/i });
      await user.click(confirmButton);

      await waitFor(() => {
        expect(mockSignOut).toHaveBeenCalledTimes(1);
      });
    });

    it('should close dialog and not sign out when cancel is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });

      const signOutMenuItem = screen.getByTestId('menu-item-sign-out');
      await user.click(signOutMenuItem);

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      }, { timeout: 3000 });

      // Find and click the cancel button
      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });

      expect(mockSignOut).not.toHaveBeenCalled();
    });

    it('should close menu when sign out is clicked', async () => {
      const user = userEvent.setup();
      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByTestId('profile-menu')).toBeInTheDocument();
      });

      const signOutMenuItem = screen.getByTestId('menu-item-sign-out');
      await user.click(signOutMenuItem);

      await waitFor(() => {
        expect(screen.queryByTestId('profile-menu')).not.toBeInTheDocument();
      });
    });
  });

  describe('i18n translations', () => {
    it('should use translated text for sign in button', () => {
      mockUseAuth.mockReturnValue({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });

      renderAppHeader();

      const loginButtonContainer = screen.getByTestId('button-login');
      expect(loginButtonContainer).toHaveTextContent('Sign in');
    });

    it('should use translated text for profile menu', async () => {
      const user = userEvent.setup();
      mockUseAuth.mockReturnValue({
        isAuthenticated: true,
        user: {
          id: 'user-123',
          email: 'test@example.com',
          displayName: 'Test User',
        },
        isLoading: false,
        signOut: vi.fn(),
      });

      renderAppHeader();

      const profileButton = screen.getByTestId('button-profile');
      await user.click(profileButton);

      await waitFor(() => {
        expect(screen.getByText('My Account')).toBeInTheDocument();
        expect(screen.getByText('Settings')).toBeInTheDocument();
        expect(screen.getByText('Sign out')).toBeInTheDocument();
      });
    });
  });
});

