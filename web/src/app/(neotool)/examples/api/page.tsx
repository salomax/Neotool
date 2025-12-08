"use client";

import React, { useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import CodeBlock from "@mui/material/Box";
import { Button } from "@/shared/components/ui/primitives";
// import { useQuery, useMutation, useLazyQuery } from '@apollo/client';
// import { 
//   GET_CUSTOMERS, 
//   GET_PRODUCTS, 
//   CREATE_CUSTOMER, 
//   CREATE_PRODUCT,
//   GET_DASHBOARD_SUMMARY,
//   GET_DASHBOARD_TIMESERIES 
// } from '@/lib/graphql/operations';
// import { Customer, Product } from '@/lib/graphql/types';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`api-tabpanel-${index}`}
      aria-labelledby={`api-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

export default function ApiIntegrationPage() {
  const [tabValue, setTabValue] = useState(0);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        API Integration Examples
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        Comprehensive examples of GraphQL and REST API integration patterns using Apollo Client and TanStack Query.
      </Typography>

      <Paper sx={{ width: '100%' }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={tabValue} onChange={handleTabChange} aria-label="API integration tabs">
            <Tab label="GraphQL Queries" />
            <Tab label="GraphQL Mutations" />
            <Tab label="REST API" />
            <Tab label="Error Handling" />
            <Tab label="Performance" />
          </Tabs>
        </Box>

        <TabPanel value={tabValue} index={0}>
          <GraphQLQueriesExample />
        </TabPanel>

        <TabPanel value={tabValue} index={1}>
          <GraphQLMutationsExample />
        </TabPanel>

        <TabPanel value={tabValue} index={2}>
          <RestApiExample />
        </TabPanel>

        <TabPanel value={tabValue} index={3}>
          <ErrorHandlingExample />
        </TabPanel>

        <TabPanel value={tabValue} index={4}>
          <PerformanceExample />
        </TabPanel>
      </Paper>
    </Container>
  );
}

// GraphQL Queries Example
function GraphQLQueriesExample() {
  // const { data: customersData, loading: customersLoading, error: customersError } = useQuery(GET_CUSTOMERS);
  // const { data: productsData, loading: productsLoading, error: productsError } = useQuery(GET_PRODUCTS);
  // const { data: dashboardData, loading: dashboardLoading, error: dashboardError } = useQuery(GET_DASHBOARD_SUMMARY);

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        GraphQL Queries with Apollo Client
      </Typography>
      
      <Alert severity="info" sx={{ mb: 3 }}>
        GraphQL integration is temporarily disabled. Enable the GraphQLProvider in providers.tsx to use GraphQL features.
      </Alert>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Example GraphQL Query:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`const { data, loading, error } = useQuery(GET_CUSTOMERS);

if (loading) return <CircularProgress />;
if (error) return <Alert severity="error">{error.message}</Alert>;

return <div>{/* Render data */}</div>;`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// GraphQL Mutations Example
function GraphQLMutationsExample() {
  // const [createCustomer, { loading: createCustomerLoading, error: createCustomerError }] = useMutation(CREATE_CUSTOMER);
  // const [createProduct, { loading: createProductLoading, error: createProductError }] = useMutation(CREATE_PRODUCT);

  const handleCreateCustomer = async () => {
    // Example implementation
    console.log('Create customer mutation would be called here');
  };

  const handleCreateProduct = async () => {
    // Example implementation
    console.log('Create product mutation would be called here');
  };

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        GraphQL Mutations with Apollo Client
      </Typography>
      
      <Alert severity="info" sx={{ mb: 3 }}>
        GraphQL integration is temporarily disabled. Enable the GraphQLProvider in providers.tsx to use GraphQL features.
      </Alert>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Example GraphQL Mutation:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`const [createCustomer, { loading, error }] = useMutation(CREATE_CUSTOMER);

const handleSubmit = async () => {
  try {
    const result = await createCustomer({
      variables: {
        input: {
          name: "John Doe",
          email: "john@example.com",
          status: "ACTIVE"
        }
      }
    });
    console.log('Customer created:', result.data);
  } catch (error) {
    // Error handling example - in real code, show toast/error message
  }
};`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// REST API Example
function RestApiExample() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/api/customers/`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const result = await response.json();
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch data');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        REST API Integration
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Fetch Customers via REST API:
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
          <Button 
            onClick={fetchData}
            loading={loading}
            variant="contained"
          >
            {loading ? "Loading..." : "Fetch Customers"}
          </Button>
        </Box>
        <ErrorAlert
          error={error}
          onRetry={fetchData}
        />
        {data && (
          <CodeBlock component="pre" sx={{ 
            backgroundColor: 'grey.100', 
            p: 2, 
            borderRadius: 1,
            overflow: 'auto',
            fontSize: '0.875rem'
          }}>
            {JSON.stringify(data, null, 2)}
          </CodeBlock>
        )}
      </Box>
    </Box>
  );
}

// Error Handling Example
function ErrorHandlingExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Error Handling Patterns
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          GraphQL Error Handling:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`const { data, loading, error } = useQuery(GET_CUSTOMERS);

if (loading) return <CircularProgress />;
if (error) return <Alert severity="error">{error.message}</Alert>;
if (!data) return <Alert severity="warning">No data available</Alert>;

return <div>{/* Render data */}</div>;`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          REST API Error Handling:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`const fetchData = async () => {
  try {
    const response = await fetch('/api/customers');
    if (!response.ok) {
      throw new Error(\`HTTP error! status: \${response.status}\`);
    }
    const data = await response.json();
    setData(data);
  } catch (error) {
    setError(error instanceof Error ? error.message : 'Failed to fetch');
  }
};`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Performance Example
function PerformanceExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Performance Optimization
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Apollo Client Caching:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`const apolloClient = new ApolloClient({
  cache: new InMemoryCache({
    typePolicies: {
      Query: {
        fields: {
          customers: {
            merge(existing = [], incoming) {
              return [...existing, ...incoming];
            },
          },
        },
      },
    },
  }),
});`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Query Optimization:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`// Use lazy queries for conditional loading
const [getCustomers, { data, loading }] = useLazyQuery(GET_CUSTOMERS);

// Use variables for dynamic queries
const { data } = useQuery(GET_CUSTOMERS, {
  variables: { search: searchTerm, status: statusFilter }
});`}
        </CodeBlock>
      </Box>
    </Box>
  );
}
