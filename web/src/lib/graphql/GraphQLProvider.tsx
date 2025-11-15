"use client";

import React from 'react';
import { ApolloProvider } from '@apollo/client/react';
import { apolloClient } from './client';

interface GraphQLProviderProps {
  children: React.ReactNode;
}

export function GraphQLProvider({ children }: GraphQLProviderProps) {

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
