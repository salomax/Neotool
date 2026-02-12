import { Stack } from 'expo-router';
import { ErrorBoundary } from 'react-error-boundary';
import { StatusBar } from 'expo-status-bar';

// Providers
import { AppProviders } from '@/AppProviders';

// Error fallback component
import { ErrorFallback } from '@/components/ui/feedback/ErrorFallback';

export default function RootLayout() {
  return (
    <ErrorBoundary FallbackComponent={ErrorFallback}>
      <AppProviders>
        <StatusBar style="auto" />
        <Stack
          screenOptions={{
            headerShown: false,
            animation: 'slide_from_right',
          }}
        >
          <Stack.Screen name="index" />
          <Stack.Screen name="(auth)" />
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="+not-found" />
        </Stack>
      </AppProviders>
    </ErrorBoundary>
  );
}
