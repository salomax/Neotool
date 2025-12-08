"use client";

import React, { Component, ReactNode } from 'react';
import { Box } from '@mui/material';
import { ErrorAlert } from '@/shared/components/ui/feedback';
import { logger } from '@/shared/utils/logger';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  override componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    logger.error('ErrorBoundary caught an error:', { error, errorInfo });
    this.props.onError?.(error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: undefined });
  };

  override render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <Box sx={{ p: 3, textAlign: 'center' }}>
          <ErrorAlert
            error={this.state.error || new Error('An unexpected error occurred')}
            onRetry={this.handleRetry}
            fallbackMessage="Something went wrong"
          />
        </Box>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
