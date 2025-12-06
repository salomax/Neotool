"use client";

import React from 'react';
import { ApolloProvider } from '@apollo/client/react';
import { getApolloClient } from './client';

interface GraphQLProviderProps {
  children: React.ReactNode;
}

export function GraphQLProvider({ children }: GraphQLProviderProps) {
  // Get Apollo Client instance (ensures proper initialization)
  const apolloClient = React.useMemo(() => getApolloClient(), []);

  if (!apolloClient) {
    console.error('apolloClient is undefined!');
    return <div>Error: Apollo Client not available</div>;
  }

  if (!ApolloProvider) {
    console.error('ApolloProvider is undefined!');
    return <div>Error: ApolloProvider not available</div>;
  }
  
  return (
    <ApolloProvider client={apolloClient}>
      {children}
    </ApolloProvider>
  );
}
