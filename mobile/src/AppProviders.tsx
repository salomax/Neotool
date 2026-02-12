import { ReactNode } from 'react';
import { ApolloProvider } from '@/shared/providers/ApolloProvider';
import { AuthProvider } from '@/shared/providers/AuthProvider';
import { I18nProvider } from '@/shared/providers/I18nProvider';
import { ThemeProvider } from '@/shared/providers/ThemeProvider';

interface AppProvidersProps {
  children: ReactNode;
}

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <I18nProvider>
      <ThemeProvider>
        <ApolloProvider>
          <AuthProvider>
            {children}
          </AuthProvider>
        </ApolloProvider>
      </ThemeProvider>
    </I18nProvider>
  );
}
