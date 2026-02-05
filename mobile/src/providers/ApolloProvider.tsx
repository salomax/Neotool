import { ReactNode, useEffect, useState } from 'react';
import { ApolloProvider as BaseApolloProvider, ApolloClient, NormalizedCacheObject } from '@apollo/client';
import { ActivityIndicator, View, StyleSheet } from 'react-native';
import { initializeApolloClient } from '@/lib/apollo-client';

interface ApolloProviderProps {
  children: ReactNode;
}

export function ApolloProvider({ children }: ApolloProviderProps) {
  const [client, setClient] = useState<ApolloClient<NormalizedCacheObject> | null>(null);

  useEffect(() => {
    initializeApolloClient()
      .then((apolloClient) => {
        setClient(apolloClient);
      })
      .catch((error) => {
        console.error('Failed to initialize Apollo Client:', error);
      });
  }, []);

  if (!client) {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return <BaseApolloProvider client={client}>{children}</BaseApolloProvider>;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
