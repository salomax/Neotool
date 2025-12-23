import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import SignInPage from '../page';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/shared/i18n/config';
import { signinTranslations } from '../i18n';

// Register signin translations
i18n.addResourceBundle('en', 'signin', signinTranslations.en, true, true);
i18n.addResourceBundle('pt', 'signin', signinTranslations.pt, true, true);

// Mock Next.js router
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock auth and toast providers
const mockUseAuth = vi.fn();
const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();

vi.mock('@/shared/providers', () => ({
  useAuth: () => mockUseAuth(),
  useToast: () => ({
    error: mockShowError,
    success: mockShowSuccess,
  }),
}));

// Mock Next.js Link
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const renderSignInPage = () => {
  return render(
    <I18nextProvider i18n={i18n}>
      <AppThemeProvider>
        <SignInPage />
      </AppThemeProvider>
    </I18nextProvider>
  );
};

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('SignInPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('renders signin page', () => {
    renderSignInPage();
    
    expect(screen.getByTestId('signin-screen')).toBeInTheDocument();
    expect(screen.getByTestId('signin-form')).toBeInTheDocument();
  });

  it('displays welcome title and subtitle', () => {
    renderSignInPage();
    
    // Use getAllByText and get first element to handle multiple renders
    const welcomeElements = screen.getAllByText(/welcome back/i);
    expect(welcomeElements[0]).toBeInTheDocument();
    expect(screen.getByText(/sign in to your account/i)).toBeInTheDocument();
  });

  it('redirects if already authenticated', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
    });
    
    renderSignInPage();
    
    // Should redirect to home
    expect(mockPush).toHaveBeenCalledWith('/');
  });

  it('shows loading state when auth is loading', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
    });
    
    renderSignInPage();
    
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('does not redirect while loading', () => {
    // Reset mock before setting up loading state
    mockPush.mockClear();
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
    });
    
    renderSignInPage();
    
    // Should not redirect while loading, even if eventually authenticated
    expect(mockPush).not.toHaveBeenCalled();
  });
});

