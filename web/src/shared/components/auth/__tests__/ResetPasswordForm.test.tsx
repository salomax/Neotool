import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResetPasswordForm } from '../ResetPasswordForm';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/shared/i18n/config';
import { resetPasswordTranslations } from '@/app/(authentication)/reset-password/i18n';

// Register translations
i18n.addResourceBundle('en', 'resetPassword', resetPasswordTranslations.en, true, true);
i18n.addResourceBundle('pt', 'resetPassword', resetPasswordTranslations.pt, true, true);

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
const mockResetPassword = vi.fn();
vi.mock('@apollo/client/react', () => ({
  useMutation: vi.fn(() => [mockResetPassword]),
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

const renderResetPasswordForm = (props = {}) => {
  return render(
    <I18nextProvider i18n={i18n}>
      <AppThemeProvider>
        <ResetPasswordForm token="test-token" {...props} />
      </AppThemeProvider>
    </I18nextProvider>
  );
};

// Run sequentially to avoid multiple rendered forms across parallel threads
describe.sequential('ResetPasswordForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockResetPassword.mockResolvedValue({
      data: {
        resetPassword: {
          success: true,
        },
      },
    });
  });

  it('renders form fields', () => {
    renderResetPasswordForm();

    expect(screen.getByTestId('reset-password-form')).toBeInTheDocument();
    expect(screen.getByTestId('textfield-new-password')).toBeInTheDocument();
    expect(screen.getByTestId('textfield-confirm-password')).toBeInTheDocument();
    expect(screen.getByTestId('button-reset-password')).toBeInTheDocument();
  });

  it('validates password requirements', async () => {
    const user = userEvent.setup();
    renderResetPasswordForm();

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'weak');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.queryByText(/weak password|required/i)).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('requires password to have uppercase, lowercase, number, and special character', async () => {
    const user = userEvent.setup();
    renderResetPasswordForm();

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'ValidPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockResetPassword).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  it('validates password match', async () => {
    const user = userEvent.setup();
    renderResetPasswordForm();

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'DifferentPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.queryByText(/password mismatch|match/i)).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('submits form with valid passwords', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderResetPasswordForm({ onSuccess });

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'ValidPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockResetPassword).toHaveBeenCalledWith({
        variables: {
          input: {
            token: 'test-token',
            newPassword: 'ValidPass123!',
          },
        },
      });
    }, { timeout: 3000 });
  });

  it('shows success message after successful submission', async () => {
    const user = userEvent.setup();
    renderResetPasswordForm();

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'ValidPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/success/i)).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('redirects to signin after successful submission', async () => {
    const user = userEvent.setup();
    renderResetPasswordForm();

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'ValidPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/success/i)).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/signin');
    }, { timeout: 3000 });
  });

  it('calls onSuccess callback after successful submission', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderResetPasswordForm({ onSuccess });

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'ValidPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  it('shows error message on submission failure', async () => {
    const user = userEvent.setup();
    mockResetPassword.mockRejectedValue(new Error('Network error'));

    renderResetPasswordForm();

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'ValidPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByTestId('reset-password-error')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('disables submit button while submitting', async () => {
    const user = userEvent.setup();
    mockResetPassword.mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(resolve, 100);
        })
    );

    renderResetPasswordForm();

    const passwordField = screen.getByTestId('textfield-new-password');
    const passwordInput = within(passwordField).getByLabelText(/new password/i) as HTMLInputElement;
    const confirmField = screen.getByTestId('textfield-confirm-password');
    const confirmPasswordInput = within(confirmField).getByLabelText(/confirm password/i) as HTMLInputElement;
    const submitButton = screen.getByTestId('button-reset-password');

    await user.type(passwordInput, 'ValidPass123!');
    await user.type(confirmPasswordInput, 'ValidPass123!');
    await user.click(submitButton);

    await waitFor(() => {
      expect(submitButton).toBeDisabled();
    }, { timeout: 3000 });
  });
});
