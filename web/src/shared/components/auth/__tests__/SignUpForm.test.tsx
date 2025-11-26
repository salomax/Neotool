import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SignUpForm } from '../SignUpForm';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/shared/i18n/config';
import { signupTranslations } from '@/app/signup/i18n';

// Register translations
i18n.addResourceBundle('en', 'signup', signupTranslations.en, true, true);
i18n.addResourceBundle('pt', 'signup', signupTranslations.pt, true, true);

// Mock providers
const mockSignUp = vi.fn();
const mockShowError = vi.fn();

vi.mock('@/shared/providers', async () => {
  const actual = await vi.importActual('@/shared/providers');
  return {
    ...actual,
    useAuth: vi.fn(() => ({
      signUp: mockSignUp,
      isAuthenticated: false,
      isLoading: false,
    })),
    useToast: vi.fn(() => ({
      error: mockShowError,
      success: vi.fn(),
    })),
  };
});

// Mock Next.js Link
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const renderSignUpForm = (props = {}) => {
  return render(
    <I18nextProvider i18n={i18n}>
      <AppThemeProvider>
        <SignUpForm {...props} />
      </AppThemeProvider>
    </I18nextProvider>
  );
};

describe('SignUpForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSignUp.mockResolvedValue(undefined);
  });

  it('renders all form fields', () => {
    renderSignUpForm();

    expect(screen.getByTestId('signup-form')).toBeInTheDocument();
    expect(screen.getByTestId('textfield-name')).toBeInTheDocument();
    expect(screen.getByTestId('textfield-email')).toBeInTheDocument();
    expect(screen.getByTestId('textfield-password')).toBeInTheDocument();
    expect(screen.getByTestId('button-signup')).toBeInTheDocument();
  });

  it('validates required name field', async () => {
    const user = userEvent.setup();
    renderSignUpForm();

    const submitButton = screen.getByTestId('button-signup');
    await user.click(submitButton);

    await waitFor(() => {
      const errorMessages = screen.getAllByText(/required/i);
      expect(errorMessages.length).toBeGreaterThan(0);
    });
  });

  it('validates email field', async () => {
    const user = userEvent.setup();
    renderSignUpForm();

    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const submitButton = screen.getByTestId('button-signup');

    await user.type(emailInput, 'invalid-email');
    await user.click(submitButton);

    await waitFor(() => {
      const errorMessages = screen.getAllByText(/invalid email|required/i);
      expect(errorMessages.length).toBeGreaterThan(0);
    });
  });

  it('validates password requirements', async () => {
    const user = userEvent.setup();
    renderSignUpForm();

    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signup');

    await user.type(passwordInput, 'weak');
    await user.click(submitButton);

    await waitFor(() => {
      const errorMessages = screen.getAllByText(/invalid password|required/i);
      expect(errorMessages.length).toBeGreaterThan(0);
    });
  });

  it('shows password validation rules when password is entered', async () => {
    const user = userEvent.setup();
    renderSignUpForm();

    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT') as HTMLInputElement;

    await user.type(passwordInput, 'test');

    await waitFor(() => {
      expect(screen.getByTestId('password-validation')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('displays password validation rules', async () => {
    const user = userEvent.setup();
    renderSignUpForm();

    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT') as HTMLInputElement;

    await user.type(passwordInput, 'test');

    await waitFor(() => {
      expect(screen.getByTestId('password-rule-0')).toBeInTheDocument();
      expect(screen.getByTestId('password-rule-1')).toBeInTheDocument();
      expect(screen.getByTestId('password-rule-2')).toBeInTheDocument();
      expect(screen.getByTestId('password-rule-3')).toBeInTheDocument();
      expect(screen.getByTestId('password-rule-4')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('submits form with valid data', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderSignUpForm({ onSuccess });

    const nameInput = screen.getByRole('textbox', { name: /name/i });
    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signup');

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john@example.com');
    await user.type(passwordInput, 'ValidPass123!');
    
    await waitFor(() => {
      expect(nameInput).toHaveValue('John Doe');
      expect(emailInput).toHaveValue('john@example.com');
      expect(passwordInput).toHaveValue('ValidPass123!');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith('John Doe', 'john@example.com', 'ValidPass123!');
    }, { timeout: 3000 });
  });

  it('calls onSuccess callback after successful submission', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderSignUpForm({ onSuccess });

    const nameInput = screen.getByRole('textbox', { name: /name/i });
    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signup');

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john@example.com');
    await user.type(passwordInput, 'ValidPass123!');
    
    await waitFor(() => {
      expect(nameInput).toHaveValue('John Doe');
      expect(emailInput).toHaveValue('john@example.com');
      expect(passwordInput).toHaveValue('ValidPass123!');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  it('shows error message on submission failure', async () => {
    const user = userEvent.setup();
    mockSignUp.mockRejectedValue(new Error('Sign up failed'));

    renderSignUpForm();

    const nameInput = screen.getByRole('textbox', { name: /name/i });
    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signup');

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john@example.com');
    await user.type(passwordInput, 'ValidPass123!');
    
    await waitFor(() => {
      expect(nameInput).toHaveValue('John Doe');
      expect(emailInput).toHaveValue('john@example.com');
      expect(passwordInput).toHaveValue('ValidPass123!');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByTestId('signup-error')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('disables submit button while submitting', async () => {
    const user = userEvent.setup();
    mockSignUp.mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(resolve, 100);
        })
    );

    renderSignUpForm();

    const nameInput = screen.getByRole('textbox', { name: /name/i });
    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT') as HTMLInputElement;
    const submitButton = screen.getByTestId('button-signup');

    await user.type(nameInput, 'John Doe');
    await user.type(emailInput, 'john@example.com');
    await user.type(passwordInput, 'ValidPass123!');
    
    await waitFor(() => {
      expect(nameInput).toHaveValue('John Doe');
      expect(emailInput).toHaveValue('john@example.com');
      expect(passwordInput).toHaveValue('ValidPass123!');
    });
    
    await user.click(submitButton);

    await waitFor(() => {
      expect(submitButton).toBeDisabled();
    });
  });

  it('trims name and email before submission', async () => {
    const user = userEvent.setup();
    renderSignUpForm();

    const nameInput = screen.getByRole('textbox', { name: /name/i });
    const emailInput = screen.getByRole('textbox', { name: /email/i });
    const passwordInputs = screen.getAllByLabelText(/password/i);
    const passwordInput = passwordInputs.find((el) => el.tagName === 'INPUT');
    if (!passwordInput) {
      throw new Error('Password input not found');
    }
    const submitButton = screen.getByTestId('button-signup');

    await user.type(nameInput, '  John Doe  ');
    await user.type(emailInput, '  john@example.com  ');
    await user.type(passwordInput as HTMLInputElement, 'ValidPass123!');
    
    // Wait for password input to have value
    await waitFor(() => {
      expect(passwordInput).toHaveValue('ValidPass123!');
    });
    
    await user.click(submitButton);

    // Verify that the form trims name and email before submission
    await waitFor(() => {
      expect(mockSignUp).toHaveBeenCalledWith('John Doe', 'john@example.com', 'ValidPass123!');
    }, { timeout: 3000 });
  });
});

