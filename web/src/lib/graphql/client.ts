// Apollo Client imports for GraphQL operations
import { ApolloClient, InMemoryCache, HttpLink } from '@apollo/client';

// HTTP Link configuration - defines how Apollo Client communicates with the GraphQL server
const httpLink = new HttpLink({
  uri: process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql',
});

// Apollo Client instance - the main GraphQL client for the application
const apolloClient = new ApolloClient({
  link: httpLink,
  // Cache: stores the results of GraphQL operations in memory
  // InMemoryCache provides automatic normalization and caching of query results
  // This improves performance by avoiding duplicate requests and enabling optimistic updates
  cache: new InMemoryCache(),
});

export { apolloClient };