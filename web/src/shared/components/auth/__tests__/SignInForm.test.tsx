import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SignInForm } from '../SignInForm';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/shared/i18n/config';
import { signinTranslations } from '@/app/signin/i18n';

// Register signin translations
i18n.addResourceBundle('en', 'signin', signinTranslations.en, true, true);
i18n.addResourceBundle('pt', 'signin', signinTranslations.pt, true, true);

// Mock the auth and toast hooks
const mockSignIn = vi.fn();
const mockSignInWithOAuth = vi.fn();
const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();

vi.mock('@/shared/providers', async () => {
  const actual = await vi.importActual('@/shared/providers');
  return {
    ...actual,
    useAuth: vi.fn(() => ({
      signIn: mockSignIn,
      signInWithOAuth: mockSignInWithOAuth,
      isAuthenticated: false,
      isLoading: false,
    })),
    useToast: vi.fn(() => ({
      error: mockShowError,
      success: mockShowSuccess,
    })),
  };
});

// Mock the OAuth hook
const mockSignInOAuth = vi.fn();
vi.mock('@/shared/hooks/auth', () => ({
  useOAuth: vi.fn(() => ({
    signIn: mockSignInOAuth,
    isLoading: false,
    error: null,
  })),
}));

// Mock Next.js Link
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const renderSignInForm = (props = {}) => {
  return render(
    <I18nextProvider i18n={i18n}>
      <AppThemeProvider>
        <SignInForm onSuccess={vi.fn()} {...props} />
      </AppThemeProvider>
    </I18nextProvider>
  );
};

describe('SignInForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSignIn.mockResolvedValue(undefined);
    mockSignInWithOAuth.mockResolvedValue(undefined);
    mockSignInOAuth.mockResolvedValue('test-id-token');
  });

  it('renders all form fields', () => {
    renderSignInForm();
    
    expect(screen.getByTestId('textfield-email')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /keep me signed in/i })).toBeInTheDocument();
    expect(screen.getByTestId('button-signin')).toBeInTheDocument();
    expect(screen.getByTestId('button-google-signin')).toBeInTheDocument();
  });

  it('validates email field', async () => {
    const user = userEvent.setup();
    renderSignInForm();
    
    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-signin');
    
    // Try to submit with invalid email
    await user.type(emailInput, 'invalid-email');
    await user.click(submitButton);
    
    await waitFor(() => {
      const errorText = screen.queryByText(/invalid email|required/i);
      expect(errorText).toBeInTheDocument();
    });
  });

  it('validates required fields', async () => {
    const user = userEvent.setup();
    renderSignInForm();
    
    const submitButton = screen.getByTestId('button-signin');
    await user.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText(/required/i)).toBeInTheDocument();
    });
  });

  it('submits form with valid data', async () => {
    const user = userEvent.setup();
    renderSignInForm();
    
    const emailInput = screen.getByRole('textbox', { name: /email/i }) as HTMLInputElement;
    const passwordInput = screen.getByLabelText('Password') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signin');
    
    // Fill in the form fields
    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'TestPassword123!');
    
    // Wait for inputs to have values
    await waitFor(() => {
      expect(emailInput).toHaveValue('test@example.com');
      expect(passwordInput).toHaveValue('TestPassword123!');
    });
    
    // Click the submit button (this should trigger form submission)
    await user.click(submitButton);
    
    // Wait for the signIn function to be called
    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalled();
    }, { timeout: 3000 });
    
    // Verify it was called with correct arguments
    expect(mockSignIn).toHaveBeenCalledWith(
      'test@example.com',
      'TestPassword123!',
      false
    );
  });

  it('handles remember me checkbox', async () => {
    const user = userEvent.setup();
    renderSignInForm();
    
    const emailInput = screen.getByRole('textbox', { name: /email/i }) as HTMLInputElement;
    const passwordInput = screen.getByLabelText('Password') as HTMLInputElement;
    const checkbox = screen.getByRole('checkbox', { name: /keep me signed in/i });
    const submitButton = screen.getByTestId('button-signin');
    
    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'TestPassword123!');
    await user.click(checkbox);
    
    await waitFor(() => {
      expect(checkbox).toBeChecked();
    });
    
    await user.click(submitButton);
    
    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalled();
    }, { timeout: 3000 });
    
    expect(mockSignIn).toHaveBeenCalledWith(
      'test@example.com',
      'TestPassword123!',
      true
    );
  });

  it('displays error message on sign in failure', async () => {
    const user = userEvent.setup();
    mockSignIn.mockRejectedValue(new Error('Invalid email or password'));
    
    renderSignInForm();
    
    const emailInput = screen.getByRole('textbox', { name: /email/i }) as HTMLInputElement;
    const passwordInput = screen.getByLabelText('Password') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signin');
    
    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'WrongPassword');
    
    await user.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByTestId('signin-error')).toBeInTheDocument();
    }, { timeout: 3000 });
    
    expect(screen.getByText(/invalid/i)).toBeInTheDocument();
    expect(mockShowError).toHaveBeenCalled();
  });

  it('shows loading state during submission', async () => {
    const user = userEvent.setup();
    let resolvePromise: () => void;
    const promise = new Promise<void>((resolve) => {
      resolvePromise = resolve;
    });
    mockSignIn.mockImplementation(() => promise);
    
    renderSignInForm();
    
    const emailInput = screen.getByRole('textbox', { name: /email/i }) as HTMLInputElement;
    const passwordInput = screen.getByLabelText('Password') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signin');
    
    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'TestPassword123!');
    
    // Click submit button
    const clickPromise = user.click(submitButton);
    
    // Button should be disabled during submission
    await waitFor(() => {
      expect(submitButton).toBeDisabled();
    }, { timeout: 1000 });
    
    // Resolve the promise to clean up
    resolvePromise!();
    await clickPromise;
  });

  it('renders links correctly', () => {
    renderSignInForm();
    
    expect(screen.getByText(/forgot your password/i)).toBeInTheDocument();
    expect(screen.getByText(/sign up/i)).toBeInTheDocument();
  });

  it('calls onSuccess callback after successful sign in', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    
    renderSignInForm({ onSuccess });
    
    const emailInput = screen.getByRole('textbox', { name: /email/i }) as HTMLInputElement;
    const passwordInput = screen.getByLabelText('Password') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signin');
    
    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'TestPassword123!');
    
    await user.click(submitButton);
    
    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalled();
    }, { timeout: 3000 });
    
    // Wait for onSuccess to be called after signIn resolves
    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    }, { timeout: 2000 });
  });

  describe('Google OAuth Sign In', () => {
    it('should render Google sign-in button', () => {
      renderSignInForm();

      const googleButton = screen.getByTestId('button-google-signin');
      expect(googleButton).toBeInTheDocument();
      expect(googleButton).toHaveTextContent(/continue with google/i);
    });

    it('should call OAuth sign-in when Google button is clicked', async () => {
      const user = userEvent.setup();
      renderSignInForm();

      const googleButton = screen.getByTestId('button-google-signin');
      await user.click(googleButton);

      await waitFor(() => {
        expect(mockSignInOAuth).toHaveBeenCalledWith('google');
      });
    });

    it('should call signInWithOAuth with ID token on successful OAuth', async () => {
      const user = userEvent.setup();
      const idToken = 'test-google-id-token';
      mockSignInOAuth.mockResolvedValue(idToken);
      
      const { useOAuth } = await import('@/shared/hooks/auth');
      let oauthOnSuccess: ((idToken: string) => void) | undefined;
      
      vi.mocked(useOAuth).mockImplementation((options) => {
        oauthOnSuccess = options?.onSuccess;
        return {
          signIn: mockSignInOAuth,
          isLoading: false,
          error: null,
        };
      });
      
      renderSignInForm();

      const googleButton = screen.getByTestId('button-google-signin');
      await user.click(googleButton);

      await waitFor(() => {
        expect(mockSignInOAuth).toHaveBeenCalledWith('google');
      });

      // Trigger the onSuccess callback manually to simulate OAuth completion
      if (oauthOnSuccess) {
        await act(async () => {
          oauthOnSuccess!(idToken);
        });
      }

      // Wait for the OAuth callback to trigger signInWithOAuth
      await waitFor(() => {
        expect(mockSignInWithOAuth).toHaveBeenCalledWith('google', idToken, false);
      }, { timeout: 2000 });
    });

    it('should show loading state during OAuth sign-in', async () => {
      const user = userEvent.setup();
      let resolveOAuth: (value: string) => void;
      const oauthPromise = new Promise<string>((resolve) => {
        resolveOAuth = resolve;
      });
      mockSignInOAuth.mockReturnValue(oauthPromise);

      const { useOAuth } = await import('@/shared/hooks/auth');
      vi.mocked(useOAuth).mockReturnValue({
        signIn: mockSignInOAuth,
        isLoading: false,
        error: null,
      });

      renderSignInForm();

      const googleButton = screen.getByTestId('button-google-signin');
      
      // Click the button to start OAuth flow
      const clickPromise = user.click(googleButton);
      
      // The button should be disabled while the OAuth promise is pending
      // (isOAuthLoading is set to true in handleGoogleSignIn)
      await waitFor(() => {
        expect(googleButton).toBeDisabled();
      });

      // Resolve the promise to clean up
      resolveOAuth!('test-id-token');
      await clickPromise;
      await oauthPromise;
    });

    it('should display error message on OAuth sign-in failure', async () => {
      const user = userEvent.setup();
      const oauthError = new Error('OAuth authentication failed');
      mockSignInOAuth.mockRejectedValue(oauthError);

      renderSignInForm();

      const googleButton = screen.getByTestId('button-google-signin');
      await user.click(googleButton);

      await waitFor(() => {
        expect(screen.getByTestId('signin-error')).toBeInTheDocument();
      }, { timeout: 2000 });

      expect(mockShowError).toHaveBeenCalled();
    });

    it('should disable Google button during form submission', async () => {
      const user = userEvent.setup();
      let resolveSignIn: () => void;
      const signInPromise = new Promise<void>((resolve) => {
        resolveSignIn = resolve;
      });
      mockSignIn.mockImplementation(() => signInPromise);

      renderSignInForm();

      const emailInput = screen.getByRole('textbox', { name: /email/i }) as HTMLInputElement;
      const passwordInput = screen.getByLabelText('Password') as HTMLInputElement;
      const googleButton = screen.getByTestId('button-google-signin');

      await user.type(emailInput, 'test@example.com');
      await user.type(passwordInput, 'TestPassword123!');
      await user.click(screen.getByTestId('button-signin'));

      await waitFor(() => {
        expect(googleButton).toBeDisabled();
      });

      resolveSignIn!();
      await signInPromise;
    });
  });
});

