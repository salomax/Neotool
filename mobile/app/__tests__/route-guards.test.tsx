import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { useAuth } from '@/shared/hooks/useAuth';
import AuthLayout from '../(auth)/_layout';
import TabsLayout from '../(tabs)/_layout';

jest.mock('@/shared/hooks/useAuth', () => ({
  useAuth: jest.fn(),
}));

jest.mock('@/shared/hooks/useTheme', () => ({
  useTheme: () => ({
    colors: {
      primary: '#000',
      onSurfaceVariant: '#666',
      surface: '#fff',
      onSurface: '#111',
      background: '#fff',
    },
  }),
}));

jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

jest.mock('react-native-paper', () => {
  const React = require('react');
  const { Text } = require('react-native');

  return {
    ActivityIndicator: () => <Text>loading</Text>,
    Icon: () => null,
  };
});

jest.mock('expo-router', () => {
  const React = require('react');
  const { Text } = require('react-native');

  const Stack = ({ children }: { children?: React.ReactNode }) => <>{children}</>;
  Stack.Screen = () => null;

  const Tabs = ({ children }: { children?: React.ReactNode }) => <>{children}</>;
  Tabs.Screen = () => null;

  return {
    Redirect: ({ href }: { href: string }) => <Text testID="redirect">{href}</Text>,
    Stack,
    Tabs,
  };
});

const useAuthMock = useAuth as jest.MockedFunction<typeof useAuth>;

describe('route guards', () => {
  it('redirects authenticated users away from auth routes', () => {
    useAuthMock.mockReturnValue({
      user: null,
      isLoading: false,
      isAuthenticated: true,
      login: jest.fn(),
      logout: jest.fn(),
      signup: jest.fn(),
    });

    render(<AuthLayout />);

    expect(screen.getByText('/(tabs)')).toBeTruthy();
  });

  it('redirects unauthenticated users away from tab routes', () => {
    useAuthMock.mockReturnValue({
      user: null,
      isLoading: false,
      isAuthenticated: false,
      login: jest.fn(),
      logout: jest.fn(),
      signup: jest.fn(),
    });

    render(<TabsLayout />);

    expect(screen.getByText('/(auth)/login')).toBeTruthy();
  });

  it('shows loading while auth state is resolving in auth layout', () => {
    useAuthMock.mockReturnValue({
      user: null,
      isLoading: true,
      isAuthenticated: false,
      login: jest.fn(),
      logout: jest.fn(),
      signup: jest.fn(),
    });

    render(<AuthLayout />);

    expect(screen.queryByTestId('redirect')).toBeNull();
  });

  it('shows loading while auth state is resolving in tabs layout', () => {
    useAuthMock.mockReturnValue({
      user: null,
      isLoading: true,
      isAuthenticated: false,
      login: jest.fn(),
      logout: jest.fn(),
      signup: jest.fn(),
    });

    render(<TabsLayout />);

    expect(screen.queryByTestId('redirect')).toBeNull();
  });
});
