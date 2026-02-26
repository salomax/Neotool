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

// Mock Next.js router and search params (useSearchParams overridden per test where needed)
const mockPush = vi.fn();
const mockUseSearchParams = vi.fn(() => new URLSearchParams());
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
  useSearchParams: () => mockUseSearchParams(),
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
  const mockSetPostLoginRedirect = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSearchParams.mockReturnValue(new URLSearchParams());
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      setPostLoginRedirect: mockSetPostLoginRedirect,
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

  it('displays title and subtitle', () => {
    renderSignInPage();
    
    // Use locale-agnostic matchers (en: Welcome back / pt: Bem-vindo de volta)
    const welcomeElements = screen.getAllByText(/welcome back|bem-vindo de volta/i);
    expect(welcomeElements[0]).toBeInTheDocument();
    expect(screen.getByText(/sign in to your account|entre na sua conta para continuar/i)).toBeInTheDocument();
  });

  it('redirects if already authenticated', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      setPostLoginRedirect: vi.fn(),
    });

    renderSignInPage();

    // Should redirect to home (resolvePostLoginRedirect with no next param returns '/')
    expect(mockPush).toHaveBeenCalledWith('/');
  });

  it('shows loading state when auth is loading', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
      setPostLoginRedirect: vi.fn(),
    });

    renderSignInPage();

    expect(screen.getByTestId('signin-loading')).toBeInTheDocument();
  });

  it('does not redirect while loading', () => {
    // Reset mock before setting up loading state
    mockPush.mockClear();
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
      setPostLoginRedirect: mockSetPostLoginRedirect,
    });

    renderSignInPage();

    // Should not redirect while loading, even if eventually authenticated
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('calls setPostLoginRedirect with /settings when next=/settings', () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ next: '/settings' }));

    renderSignInPage();

    expect(mockSetPostLoginRedirect).toHaveBeenCalledWith('/settings');
  });

  it('redirects to /settings when already authenticated and next=/settings', () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ next: '/settings' }));
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      setPostLoginRedirect: mockSetPostLoginRedirect,
    });

    renderSignInPage();

    expect(mockPush).toHaveBeenCalledWith('/settings');
  });

  it('uses fallback / for invalid next and when already authenticated', () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ next: '//evil.com' }));
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      setPostLoginRedirect: mockSetPostLoginRedirect,
    });

    renderSignInPage();

    expect(mockSetPostLoginRedirect).toHaveBeenCalledWith('/');
    expect(mockPush).toHaveBeenCalledWith('/');
  });

  it('shows login required message when valid next is present', () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ next: '/settings' }));

    renderSignInPage();

    expect(screen.getByTestId('signin-login-required-message')).toBeInTheDocument();
    expect(
      screen.getByText(
        /signed in to access this feature|precisa realizar o login/i,
      ),
    ).toBeInTheDocument();
  });

  it('does not show login required message when next is absent', () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    renderSignInPage();

    expect(screen.queryByTestId('signin-login-required-message')).not.toBeInTheDocument();
  });

  it('does not show login required message when next is invalid', () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams({ next: '/signin' }));

    renderSignInPage();

    expect(screen.queryByTestId('signin-login-required-message')).not.toBeInTheDocument();
  });
});

