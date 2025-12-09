"use client";

import React from 'react';
import { ApolloProvider } from '@apollo/client/react';
import { getApolloClient } from './client';
import { logger } from '@/shared/utils/logger';

interface GraphQLProviderProps {
  children: React.ReactNode;
}

export function GraphQLProvider({ children }: GraphQLProviderProps) {
  // Get Apollo Client instance (ensures proper initialization)
  const apolloClient = React.useMemo(() => getApolloClient(), []);

  if (!apolloClient) {
    logger.error('apolloClient is undefined!');
    return <div>Error: Apollo Client not available</div>;
  }

  if (!ApolloProvider) {
    logger.error('ApolloProvider is undefined!');
    return <div>Error: ApolloProvider not available</div>;
  }
  
  return (
    <ApolloProvider client={apolloClient}>
      {children}
    </ApolloProvider>
  );
}
