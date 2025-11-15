// Apollo Client imports for GraphQL operations
import { ApolloClient, InMemoryCache, HttpLink, from } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';

// HTTP Link configuration - defines how Apollo Client communicates with the GraphQL server
const httpLink = new HttpLink({
  uri: process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql',
});

// Auth link to add token to requests
const authLink = setContext((_, { headers }) => {
  // Get token from storage (check both localStorage and sessionStorage)
  let token: string | null = null;
  if (typeof window !== 'undefined') {
    token = localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
  }
  
  return {
    headers: {
      ...headers,
      ...(token && { authorization: `Bearer ${token}` }),
    },
  };
});

// Apollo Client instance - the main GraphQL client for the application
const apolloClient = new ApolloClient({
  link: from([authLink, httpLink]),
  // Cache: stores the results of GraphQL operations in memory
  // InMemoryCache provides automatic normalization and caching of query results
  // This improves performance by avoiding duplicate requests and enabling optimistic updates
  cache: new InMemoryCache(),
});

export { apolloClient };