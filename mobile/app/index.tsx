import { useEffect } from 'react';
import { Redirect } from 'expo-router';
import { ActivityIndicator, View, StyleSheet } from 'react-native';
import { useAuth } from '@/hooks/useAuth';

/**
 * Entry screen - handles routing based on authentication state
 */
export default function IndexScreen() {
  const { user, isLoading } = useAuth();

  useEffect(() => {
    // Any initialization logic here
  }, []);

  if (isLoading) {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  // Redirect to appropriate screen based on auth state
  if (user) {
    return <Redirect href="/(tabs)" />;
  }

  return <Redirect href="/(auth)/login" />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
