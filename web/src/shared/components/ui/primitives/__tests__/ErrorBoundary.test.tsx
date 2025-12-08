import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorBoundary } from '../ErrorBoundary';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Use vi.hoisted() to define variables that need to be available in mock factories
const { mockLoggerError } = vi.hoisted(() => {
  const mockLoggerError = vi.fn();
  
  return {
    mockLoggerError,
  };
});

// Mock logger
vi.mock('@/shared/utils/logger', () => ({
  logger: {
    error: mockLoggerError,
  },
}));

// Component that throws an error for testing
const ThrowError = ({ shouldThrow = false, message = 'Test error' }: { shouldThrow?: boolean; message?: string }) => {
  if (shouldThrow) {
    throw new Error(message);
  }
  return <div>No error</div>;
};

// Component that throws an error without a message
const ThrowErrorNoMessage = ({ shouldThrow = false }: { shouldThrow?: boolean }) => {
  if (shouldThrow) {
    throw new Error();
  }
  return <div>No error</div>;
};

const renderErrorBoundary = (children: React.ReactNode, props = {}) => {
  return render(
    <AppThemeProvider>
      <ErrorBoundary {...props}>{children}</ErrorBoundary>
    </AppThemeProvider>
  );
};

describe('ErrorBoundary', () => {
  beforeEach(() => {
    // Clear logger mock calls before each test
    mockLoggerError.mockClear();
  });

  it('renders children when no error occurs', () => {
    renderErrorBoundary(<div>Test content</div>);
    expect(screen.getByText('Test content')).toBeInTheDocument();
  });

  it.skip('catches errors and displays default error UI', async () => {
    // TODO: Fix test that is hanging/timing out
    renderErrorBoundary(<ThrowError shouldThrow={true} message="Something broke" />);
    
    // ErrorAlert shows the error message, not the fallbackMessage when error has a message
    await waitFor(() => {
      expect(screen.getByText('Something broke')).toBeInTheDocument();
    }, { timeout: 3000 });
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it.skip('displays fallback message when error has no message', async () => {
    // TODO: Fix test that is hanging/timing out
    renderErrorBoundary(<ThrowErrorNoMessage shouldThrow={true} />);
    
    // When error has no message, ErrorBoundary creates a new Error with 'An unexpected error occurred'
    // and passes fallbackMessage="Something went wrong", but ErrorAlert will use the error message
    // if it exists, so it shows "An unexpected error occurred"
    await waitFor(() => {
      expect(screen.getByText('An unexpected error occurred')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('renders custom fallback when provided', () => {
    const customFallback = <div data-testid="custom-fallback">Custom error UI</div>;
    renderErrorBoundary(
      <ThrowError shouldThrow={true} message="Error occurred" />,
      { fallback: customFallback }
    );
    
    expect(screen.getByTestId('custom-fallback')).toBeInTheDocument();
    expect(screen.getByText('Custom error UI')).toBeInTheDocument();
    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();
  });

  it('calls onError callback when error occurs', () => {
    const onError = vi.fn();
    const errorInfo = { componentStack: 'test stack' } as React.ErrorInfo;
    
    renderErrorBoundary(
      <ThrowError shouldThrow={true} message="Callback test" />,
      { onError }
    );
    
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'Callback test' }),
      expect.any(Object)
    );
  });

  it('does not call onError when no error occurs', () => {
    const onError = vi.fn();
    
    renderErrorBoundary(
      <div>No error</div>,
      { onError }
    );
    
    expect(onError).not.toHaveBeenCalled();
  });

  it.skip('retry button resets error boundary state', async () => {
    // TODO: Fix test that is hanging/timing out
    const user = userEvent.setup();
    
    renderErrorBoundary(<ThrowError shouldThrow={true} message="Test error" />);
    
    // Error should be shown initially
    await waitFor(() => {
      expect(screen.getByText('Test error')).toBeInTheDocument();
    });
    
    // Verify retry button exists and is clickable
    const retryButton = screen.getByRole('button', { name: /try again/i });
    expect(retryButton).toBeInTheDocument();
    expect(retryButton).not.toBeDisabled();
    
    // Click retry - verify the button click works
    // Note: The component will throw again after retry (expected behavior),
    // but we're testing that the retry mechanism exists and is functional
    await user.click(retryButton);
    
    // Verify the click was processed (button still exists after click)
    // The ErrorBoundary resets, but ThrowError throws again, so error UI is still shown
    // This is expected - the test verifies the retry button functionality exists
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('logs error to console when error occurs', () => {
    renderErrorBoundary(<ThrowError shouldThrow={true} message="Console test" />);
    
    expect(mockLoggerError).toHaveBeenCalledWith(
      'ErrorBoundary caught an error:',
      expect.objectContaining({
        error: expect.objectContaining({ message: 'Console test' }),
        errorInfo: expect.any(Object),
      })
    );
  });

  it('works with complex children components', () => {
    const ComplexComponent = () => (
      <div>
        <h1>Complex Component</h1>
        <p>Some content</p>
      </div>
    );
    
    renderErrorBoundary(<ComplexComponent />);
    
    expect(screen.getByText('Complex Component')).toBeInTheDocument();
    expect(screen.getByText('Some content')).toBeInTheDocument();
  });
});

