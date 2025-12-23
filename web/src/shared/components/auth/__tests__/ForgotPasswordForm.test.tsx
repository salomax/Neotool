import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForgotPasswordForm } from '../ForgotPasswordForm';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/shared/i18n/config';
import { forgotPasswordTranslations } from '@/app/(authentication)/forgot-password/i18n';

// Register translations
i18n.addResourceBundle('en', 'forgotPassword', forgotPasswordTranslations.en, true, true);
i18n.addResourceBundle('pt', 'forgotPassword', forgotPasswordTranslations.pt, true, true);

// Mock providers
const mockShowError = vi.fn();
const mockShowSuccess = vi.fn();

vi.mock('@/shared/providers', async () => {
  const actual = await vi.importActual('@/shared/providers');
  return {
    ...actual,
    useToast: vi.fn(() => ({
      error: mockShowError,
      success: mockShowSuccess,
    })),
  };
});

// Mock GraphQL mutation
const mockRequestPasswordReset = vi.fn();
vi.mock('@/lib/graphql/operations/auth/mutations.generated', () => ({
  useRequestPasswordResetMutation: vi.fn(() => [mockRequestPasswordReset]),
}));

// Mock Next.js router
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

// Mock Next.js Link
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const renderForgotPasswordForm = (props = {}) => {
  return render(
    <I18nextProvider i18n={i18n}>
      <AppThemeProvider>
        <ForgotPasswordForm {...props} />
      </AppThemeProvider>
    </I18nextProvider>
  );
};

// Run sequentially to avoid multiple rendered forms across parallel threads
describe.sequential('ForgotPasswordForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRequestPasswordReset.mockResolvedValue({
      data: {
        requestPasswordReset: {
          success: true,
        },
      },
    });
  });

  it('renders form fields', () => {
    renderForgotPasswordForm();

    expect(screen.getByTestId('forgot-password-form')).toBeInTheDocument();
    expect(screen.getByTestId('textfield-email')).toBeInTheDocument();
    expect(screen.getByTestId('button-send-reset-link')).toBeInTheDocument();
  });

  it('validates email field', async () => {
    const user = userEvent.setup();
    renderForgotPasswordForm();

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'invalid-email');
    await user.click(submitButton);

    await waitFor(() => {
      // Error message is displayed in helperText, which is accessible via aria-describedby
      // or we can check for the error state on the input
      expect(emailInput).toHaveAttribute('aria-invalid', 'true');
      // The helperText should contain the translated error message
      const helperText = screen.getByText(/please enter a valid email address|this field is required/i);
      expect(helperText).toBeInTheDocument();
    });
  });

  it('requires email field', async () => {
    const user = userEvent.setup();
    renderForgotPasswordForm();

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');
    await user.click(submitButton);

    await waitFor(() => {
      // Check that the input has error state
      expect(emailInput).toHaveAttribute('aria-invalid', 'true');
      // The error message should be displayed as helperText in the TextField
      // For an empty email field, zod validates email() first, which shows "Please enter a valid email address"
      // instead of "This field is required" because empty string fails email validation first
      const errorMessage = screen.getByText(/please enter a valid email address|this field is required/i);
      expect(errorMessage).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('submits form with valid email', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderForgotPasswordForm({ onSuccess });

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    
    await waitFor(() => {
      expect(emailInput).toHaveValue('test@example.com');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockRequestPasswordReset).toHaveBeenCalledWith({
        variables: {
          input: {
            email: 'test@example.com',
            locale: 'en',
          },
        },
      });
    }, { timeout: 3000 });
  });

  it('shows success message after successful submission', async () => {
    const user = userEvent.setup();
    renderForgotPasswordForm();

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    
    await waitFor(() => {
      expect(emailInput).toHaveValue('test@example.com');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      // Wait for the mutation to complete and state to update
      // The success message should be displayed in an Alert
      const successAlert = screen.getByRole('alert');
      expect(successAlert).toBeInTheDocument();
      // Check for the translated success message
      expect(successAlert).toHaveTextContent(/if an account with that email exists, a password reset link has been sent/i);
    }, { timeout: 3000 });
  });

  it('calls onSuccess callback after successful submission', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderForgotPasswordForm({ onSuccess });

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    
    await waitFor(() => {
      expect(emailInput).toHaveValue('test@example.com');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  it('shows error message on submission failure', async () => {
    const user = userEvent.setup();
    mockRequestPasswordReset.mockRejectedValue(new Error('Network error'));

    renderForgotPasswordForm();

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    
    await waitFor(() => {
      expect(emailInput).toHaveValue('test@example.com');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByTestId('forgot-password-error')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('shows error when mutation returns unsuccessful result', async () => {
    const user = userEvent.setup();
    mockRequestPasswordReset.mockResolvedValue({
      data: {
        requestPasswordReset: {
          success: false,
        },
      },
    });

    renderForgotPasswordForm();

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    
    await waitFor(() => {
      expect(emailInput).toHaveValue('test@example.com');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByTestId('forgot-password-error')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('disables submit button while submitting', async () => {
    const user = userEvent.setup();
    mockRequestPasswordReset.mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(resolve, 100);
        })
    );

    renderForgotPasswordForm();

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    
    await waitFor(() => {
      expect(emailInput).toHaveValue('test@example.com');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(submitButton).toBeDisabled();
    });
  });
});
