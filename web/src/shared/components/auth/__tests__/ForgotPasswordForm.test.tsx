import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForgotPasswordForm } from '../ForgotPasswordForm';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/shared/i18n/config';
import { forgotPasswordTranslations } from '@/app/forgot-password/i18n';

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

describe('ForgotPasswordForm', () => {
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

    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'invalid-email');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.queryByText(/invalid email|required/i)).toBeInTheDocument();
    });
  });

  it('requires email field', async () => {
    const user = userEvent.setup();
    renderForgotPasswordForm();

    const submitButton = screen.getByTestId('button-send-reset-link');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.queryByText(/required/i)).toBeInTheDocument();
    });
  });

  it('submits form with valid email', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderForgotPasswordForm({ onSuccess });

    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
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
    });
  });

  it('shows success message after successful submission', async () => {
    const user = userEvent.setup();
    renderForgotPasswordForm();

    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/success/i)).toBeInTheDocument();
    });
  });

  it('calls onSuccess callback after successful submission', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderForgotPasswordForm({ onSuccess });

    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    await user.click(submitButton);

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it('shows error message on submission failure', async () => {
    const user = userEvent.setup();
    mockRequestPasswordReset.mockRejectedValue(new Error('Network error'));

    renderForgotPasswordForm();

    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByTestId('forgot-password-error')).toBeInTheDocument();
    });
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

    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByTestId('forgot-password-error')).toBeInTheDocument();
    });
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

    const emailInput = screen.getByTestId('textfield-email');
    const submitButton = screen.getByTestId('button-send-reset-link');

    await user.type(emailInput, 'test@example.com');
    await user.click(submitButton);

    expect(submitButton).toBeDisabled();
  });
});

